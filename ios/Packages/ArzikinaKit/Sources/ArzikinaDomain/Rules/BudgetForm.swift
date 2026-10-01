import Foundation

/// Raccourcis de période d'un budget — Android `QuickDateRange`. Ils donnent des dates FIGÉES au
/// moment du choix : un budget « Ce mois » créé aujourd'hui garde ses dates le mois suivant.
public enum BudgetQuickRange: CaseIterable, Sendable {
    case thisWeek, thisMonth, nextMonth, thisYear

    /// Bornes incluses.
    public func bounds(today: CalendarDay, calendar: Calendar) -> (start: CalendarDay, end: CalendarDay) {
        switch self {
        case .thisWeek:
            return (
                DatePeriods.currentPeriodStart(.weekly, today: today, calendar: calendar),
                DatePeriods.currentPeriodEnd(.weekly, today: today, calendar: calendar)
            )
        case .thisMonth:
            return (
                DatePeriods.currentPeriodStart(.monthly, today: today, calendar: calendar),
                DatePeriods.currentPeriodEnd(.monthly, today: today, calendar: calendar)
            )
        case .nextMonth:
            let firstOfNext = CalendarDay(year: today.year, month: today.month, day: 1).adding(.month, 1, calendar: calendar)
            return (firstOfNext, DatePeriods.currentPeriodEnd(.monthly, today: firstOfNext, calendar: calendar))
        case .thisYear:
            return (CalendarDay(year: today.year, month: 1, day: 1), CalendarDay(year: today.year, month: 12, day: 31))
        }
    }
}

/// Saisie du formulaire de budget — Android `BudgetFormState`.
///
/// Deux modes, comme sur Android : période FIXE (dates de début et de fin, seul mode proposé pour
/// un nouveau budget) ou RÉCURRENT (semaine / mois en cours), conservé uniquement pour modifier un
/// budget créé avant l'arrivée des périodes fixes.
public struct BudgetDraft: Equatable, Sendable {
    public var categoryId: EntityID?
    public var limitInput: String
    public var currencyCode: String
    /// Budget récurrent existant (sans dates) : [period] fait foi, les dates sont ignorées.
    public let isLegacyRecurring: Bool
    public var period: BudgetPeriod
    public private(set) var startDay: CalendarDay?
    public private(set) var endDay: CalendarDay?
    /// Raccourci à l'origine des dates, `nil` si elles ont été choisies à la main (« Personnalisée »).
    public private(set) var quickRange: BudgetQuickRange?

    /// Nouveau budget : « Ce mois » présélectionné (écart volontaire avec Android, qui laisse la
    /// période vide : c'est le choix le plus fréquent, et il se change d'un geste).
    public init(today: CalendarDay, calendar: Calendar, currencyCode: String = SupportedCurrency.defaultCode, categoryId: EntityID? = nil) {
        self.categoryId = categoryId
        limitInput = ""
        self.currencyCode = currencyCode
        isLegacyRecurring = false
        period = .monthly
        applyQuickRange(.thisMonth, today: today, calendar: calendar)
    }

    public init(editing budget: Budget, calendar: Calendar) {
        categoryId = budget.categoryId
        limitInput = Money.formatForInput(budget.limitAmount)
        currencyCode = budget.currencyCode
        isLegacyRecurring = !budget.hasFixedPeriod
        period = budget.period
        startDay = budget.startDate.map { CalendarDay(epochMillis: $0, calendar: calendar) }
        endDay = budget.endDate.map { CalendarDay(epochMillis: $0, calendar: calendar) }
        quickRange = nil
    }

    public mutating func applyQuickRange(_ range: BudgetQuickRange, today: CalendarDay, calendar: Calendar) {
        let bounds = range.bounds(today: today, calendar: calendar)
        startDay = bounds.start
        endDay = bounds.end
        quickRange = range
    }

    /// Date choisie à la main : plus aucun raccourci n'est actif.
    public mutating func changeStartDay(_ day: CalendarDay) {
        startDay = day
        quickRange = nil
    }

    public mutating func changeEndDay(_ day: CalendarDay) {
        endDay = day
        quickRange = nil
    }
}

/// Même ordre de vérification qu'Android (`BudgetFormViewModel.save`).
public enum BudgetFormError: Error, Equatable, Sendable {
    case categoryRequired
    case invalidLimit
    case periodRequired
    case endBeforeStart
}

public enum BudgetForm {

    /// Budget à enregistrer, ou la PREMIÈRE erreur de saisie. Les dates sont enregistrées au début
    /// de leur jour, comme Android (`DatePeriods.toEpochMillis`) : seuls les JOURS comptent.
    public static func build(
        _ draft: BudgetDraft,
        existing: Budget?,
        newId: @autoclosure () -> EntityID,
        now: EpochMillis,
        calendar: Calendar
    ) -> Result<Budget, BudgetFormError> {
        guard let categoryId = draft.categoryId else { return .failure(.categoryRequired) }
        guard let limit = Money.parseToMinorUnits(draft.limitInput), limit > 0 else { return .failure(.invalidLimit) }

        var startDate: EpochMillis?
        var endDate: EpochMillis?
        if !draft.isLegacyRecurring {
            guard let start = draft.startDay, let end = draft.endDay else { return .failure(.periodRequired) }
            guard end >= start else { return .failure(.endBeforeStart) }
            startDate = start.startOfDayMillis(calendar: calendar)
            endDate = end.startOfDayMillis(calendar: calendar)
        }
        return .success(Budget(
            id: existing?.id ?? newId(),
            categoryId: categoryId,
            period: draft.period,
            limitAmount: limit,
            currencyCode: draft.currencyCode,
            startDate: startDate,
            endDate: endDate,
            createdAt: existing?.createdAt ?? now
        ))
    }

    /// Catégories proposées : les catégories de DÉPENSE sans budget actif (à venir, en cours ou
    /// récurrent) ; un budget terminé libère sa catégorie. La catégorie du budget modifié
    /// ([editingBudgetId]) reste proposée — Android `availableCategories`.
    public static func availableCategories(
        _ categories: [Category],
        budgets: [Budget],
        editingBudgetId: EntityID?,
        today: CalendarDay,
        calendar: Calendar
    ) -> [Category] {
        let blocked = Set(budgets
            .filter { $0.id != editingBudgetId && isActive($0, today: today, calendar: calendar) }
            .map(\.categoryId))
        return categories.filter { $0.type == .expense && !blocked.contains($0.id) }
    }

    /// Un budget occupe sa catégorie tant qu'il n'est pas terminé (toujours pour un récurrent).
    public static func isActive(_ budget: Budget, today: CalendarDay, calendar: Calendar) -> Bool {
        BudgetPeriodStatus.of(budget: budget, today: today, calendar: calendar) != .completed
    }
}
