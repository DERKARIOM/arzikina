import Foundation

/// Préréglages de période de l'écran Rapports — Android `StatsPeriodPreset` et Web
/// `resolveStatsPeriodRange` (mêmes plages sur les trois plateformes).
public enum StatsPeriodPreset: CaseIterable, Sendable {
    case month, previousMonth, last7Days, last30Days, year, custom

    /// Bornes incluses ; `nil` pour [custom] (dates choisies par l'utilisateur).
    public func bounds(today: CalendarDay, calendar: Calendar) -> (start: CalendarDay, end: CalendarDay)? {
        switch self {
        case .month:
            return (
                DatePeriods.currentPeriodStart(.monthly, today: today, calendar: calendar),
                DatePeriods.currentPeriodEnd(.monthly, today: today, calendar: calendar)
            )
        case .previousMonth:
            let first = CalendarDay(year: today.year, month: today.month, day: 1).adding(.month, -1, calendar: calendar)
            return (first, DatePeriods.currentPeriodEnd(.monthly, today: first, calendar: calendar))
        case .last7Days:
            return (today.adding(.day, -6, calendar: calendar), today)
        case .last30Days:
            return (today.adding(.day, -29, calendar: calendar), today)
        case .year:
            return (CalendarDay(year: today.year, month: 1, day: 1), CalendarDay(year: today.year, month: 12, day: 31))
        case .custom:
            return nil
        }
    }
}

public enum StatsPeriodError: Error, Equatable, Sendable {
    case missingDates
    case startAfterEnd
}

/// Période choisie — Android `PeriodSelection`. Les dates personnalisées sont conservées quand
/// un autre préréglage est choisi, pour ne pas perdre la saisie.
public struct StatsPeriodSelection: Equatable, Sendable {
    public private(set) var preset: StatsPeriodPreset
    public private(set) var customStart: CalendarDay?
    public private(set) var customEnd: CalendarDay?

    public init() {
        preset = .month
    }

    /// « Personnalisée » choisie pour la première fois : pré-remplie avec le mois en cours (aucune
    /// erreur affichée avant que l'utilisateur ait agi), comme Android et le Web.
    public mutating func select(_ preset: StatsPeriodPreset, today: CalendarDay, calendar: Calendar) {
        if preset == .custom, customStart == nil, customEnd == nil,
           let month = StatsPeriodPreset.month.bounds(today: today, calendar: calendar) {
            customStart = month.start
            customEnd = month.end
        }
        self.preset = preset
    }

    public mutating func changeCustomStart(_ day: CalendarDay) {
        preset = .custom
        customStart = day
    }

    public mutating func changeCustomEnd(_ day: CalendarDay) {
        preset = .custom
        customEnd = day
    }

    /// Retour au mois en cours (bouton « Réinitialiser »).
    public mutating func reset() {
        self = StatsPeriodSelection()
    }

    public func resolve(today: CalendarDay, calendar: Calendar) -> Result<(start: CalendarDay, end: CalendarDay), StatsPeriodError> {
        if let bounds = preset.bounds(today: today, calendar: calendar) { return .success(bounds) }
        guard let start = customStart, let end = customEnd else { return .failure(.missingDates) }
        guard start <= end else { return .failure(.startAfterEnd) }
        return .success((start, end))
    }
}

/// Mouvements représentés par la répartition (les transferts n'ont pas de catégorie).
public enum BreakdownType: CaseIterable, Sendable {
    case expense, income

    public var transactionType: TransactionType { self == .expense ? .expense : .income }
}

/// Part d'une catégorie dans la répartition.
public struct CategoryShare: Identifiable, Equatable, Sendable {
    /// `nil` pour la ligne « Autres » (catégories regroupées).
    public let categoryId: EntityID?
    /// `nil` si la catégorie a été supprimée (ou pour « Autres »).
    public let category: Category?
    public let amount: MinorUnits
    /// 0…1.
    public let share: Double

    public var id: String { categoryId ?? "other" }
    public var isOther: Bool { categoryId == nil }

    public init(categoryId: EntityID?, category: Category?, amount: MinorUnits, share: Double) {
        self.categoryId = categoryId
        self.category = category
        self.amount = amount
        self.share = share
    }
}

/// Revenus et dépenses d'un mois du graphique d'évolution.
public struct MonthTotals: Identifiable, Equatable, Sendable {
    /// 1er jour du mois.
    public let month: CalendarDay
    public let income: MinorUnits
    public let expense: MinorUnits

    public var id: CalendarDay { month }

    public init(month: CalendarDay, income: MinorUnits, expense: MinorUnits) {
        self.month = month
        self.income = income
        self.expense = expense
    }
}

/// Contenu de l'écran Rapports.
public struct ReportSnapshot: Equatable, Sendable {
    /// Devise des montants : seules les transactions des comptes dans cette devise sont comptées.
    public var currencyCode: String
    public var income: MinorUnits
    public var expense: MinorUnits
    /// Répartition de la période, de la plus grosse part à la plus petite.
    public var breakdown: [CategoryShare]
    /// Les [Reports.evolutionMonthCount] derniers mois, du plus ancien au mois en cours —
    /// INDÉPENDANT de la période choisie, comme Android.
    public var evolution: [MonthTotals]

    public var net: MinorUnits { income - expense }

    public init(currencyCode: String, income: MinorUnits, expense: MinorUnits, breakdown: [CategoryShare], evolution: [MonthTotals]) {
        self.currencyCode = currencyCode
        self.income = income
        self.expense = expense
        self.breakdown = breakdown
        self.evolution = evolution
    }
}

/// Règles de l'écran Rapports — portage d'Android `StatisticsViewModel`.
public enum Reports {

    public static let evolutionMonthCount = 6

    /// Catégories affichées individuellement ; les suivantes sont regroupées en « Autres »
    /// (au-delà, des barres minuscules ne se lisent plus).
    public static let breakdownVisibleCount = 7

    /// Devise des rapports : préférence de l'utilisateur (synchronisée depuis Android / le Web),
    /// sinon celle du premier compte, sinon XOF.
    public static func currencyCode(preference: String?, accounts: [Account]) -> String {
        preference ?? accounts.first?.currencyCode ?? SupportedCurrency.defaultCode
    }

    /// Premiers jours des [count] derniers mois, du plus ancien au mois de [today].
    public static func evolutionMonths(today: CalendarDay, calendar: Calendar, count: Int = evolutionMonthCount) -> [CalendarDay] {
        let current = CalendarDay(year: today.year, month: today.month, day: 1)
        return (0..<count).reversed().map { current.adding(.month, -$0, calendar: calendar) }
    }

    /// Répartition à partir des montants par catégorie : parts, tri décroissant (à égalité, par
    /// identifiant pour un ordre stable), puis regroupement au-delà de [visibleCount].
    public static func breakdown(
        amountsByCategory: [EntityID: MinorUnits],
        categories: [EntityID: Category],
        visibleCount: Int = breakdownVisibleCount
    ) -> [CategoryShare] {
        let total = amountsByCategory.values.reduce(0, +)
        guard total > 0 else { return [] }
        let sorted = amountsByCategory
            .filter { $0.value > 0 }
            .sorted { $0.value != $1.value ? $0.value > $1.value : $0.key < $1.key }
        let share = { (amount: MinorUnits) in Double(amount) / Double(total) }
        var rows = sorted.prefix(visibleCount).map {
            CategoryShare(categoryId: $0.key, category: categories[$0.key], amount: $0.value, share: share($0.value))
        }
        let rest = sorted.dropFirst(visibleCount).reduce(MinorUnits(0)) { $0 + $1.value }
        // Une seule catégorie restante : affichée telle quelle plutôt qu'un « Autres » d'une ligne.
        if sorted.count == visibleCount + 1, let last = sorted.last {
            rows.append(CategoryShare(categoryId: last.key, category: categories[last.key], amount: last.value, share: share(last.value)))
        } else if rest > 0 {
            rows.append(CategoryShare(categoryId: nil, category: nil, amount: rest, share: share(rest)))
        }
        return rows
    }

    /// Calcul de RÉFÉRENCE, en mémoire, fidèle à Android : périmètre personnel (comptes non
    /// exclus), comptes dans [currencyCode], jours de la période inclus. La base fait le même
    /// calcul en SQL ; un test vérifie que les deux concordent.
    public static func compute(
        transactions: [Transaction],
        accounts: [Account],
        categories: [Category],
        currencyCode: String,
        period: (start: CalendarDay, end: CalendarDay)?,
        breakdownType: BreakdownType,
        today: CalendarDay,
        calendar: Calendar
    ) -> ReportSnapshot {
        let scope = PersonalStatistics.scope(accounts: accounts, transactions: transactions)
        let accountIds = Set(scope.accounts.filter { $0.currencyCode == currencyCode }.map(\.id))
        let relevant = scope.transactions.filter { accountIds.contains($0.accountId) }
        let day = { (transaction: Transaction) in CalendarDay(epochMillis: transaction.date, calendar: calendar) }

        let inPeriod = period.map { period in relevant.filter { day($0) >= period.start && day($0) <= period.end } } ?? []
        func sum(_ list: [Transaction], _ type: TransactionType) -> MinorUnits {
            list.filter { $0.type == type }.reduce(0) { $0 + $1.amount }
        }
        var amounts: [EntityID: MinorUnits] = [:]
        for transaction in inPeriod where transaction.type == breakdownType.transactionType {
            if let categoryId = transaction.categoryId { amounts[categoryId, default: 0] += transaction.amount }
        }
        let evolution = evolutionMonths(today: today, calendar: calendar).map { month in
            let monthTransactions = relevant.filter { day($0).year == month.year && day($0).month == month.month }
            return MonthTotals(month: month, income: sum(monthTransactions, .income), expense: sum(monthTransactions, .expense))
        }
        return ReportSnapshot(
            currencyCode: currencyCode,
            income: sum(inPeriod, .income),
            expense: sum(inPeriod, .expense),
            breakdown: breakdown(amountsByCategory: amounts, categories: Dictionary(uniqueKeysWithValues: categories.map { ($0.id, $0) })),
            evolution: evolution
        )
    }
}
