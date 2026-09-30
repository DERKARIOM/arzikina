import Foundation

/// Statut automatique d'un budget à période FIXE (jamais stocké) — Android `BudgetPeriodStatus`.
public enum BudgetPeriodStatus: String, CaseIterable, Sendable {
    case upcoming = "UPCOMING"
    case ongoing = "ONGOING"
    case completed = "COMPLETED"

    static func of(start: CalendarDay, end: CalendarDay, today: CalendarDay) -> BudgetPeriodStatus {
        if today < start { return .upcoming }
        if today > end { return .completed }
        return .ongoing
    }

    /// `nil` pour un budget récurrent (sans dates).
    public static func of(budget: Budget, today: CalendarDay, calendar: Calendar) -> BudgetPeriodStatus? {
        guard let startDate = budget.startDate, let endDate = budget.endDate else { return nil }
        return of(
            start: CalendarDay(epochMillis: startDate, calendar: calendar),
            end: CalendarDay(epochMillis: endDate, calendar: calendar),
            today: today
        )
    }
}

/// Rythme de dépense comparé au rythme théorique — mêmes valeurs qu'Android et le Web.
public enum BudgetPaceState: String, CaseIterable, Sendable {
    case onTrack = "ON_TRACK"
    /// En dessous du rythme théorique (au-delà de la tolérance).
    case ahead = "AHEAD"
    /// Au-dessus du rythme théorique (au-delà de la tolérance).
    case over = "OVER"
}

/// Dépensé et progression d'un budget sur sa période — Android `BudgetProgress`.
public struct BudgetProgress: Equatable, Sendable {
    public let spent: MinorUnits
    /// Dépensé / plafond ; peut dépasser 1 en cas de dépassement ; 0 si le plafond est nul.
    public let progress: Double

    /// Ne compte que les DÉPENSES de la catégorie du budget, sur les comptes dans la devise du
    /// budget, dont le jour tombe dans la période (fixe : dates incluses ; récurrent : semaine ou
    /// mois en cours).
    public static func compute(
        budget: Budget,
        transactions: [Transaction],
        accountsById: [EntityID: Account],
        today: CalendarDay,
        calendar: Calendar
    ) -> BudgetProgress {
        let bounds = BudgetPace.bounds(of: budget, today: today, calendar: calendar)
        let spent = transactions.lazy
            .filter { $0.type == .expense && $0.categoryId == budget.categoryId }
            .filter { accountsById[$0.accountId]?.currencyCode == budget.currencyCode }
            .filter {
                let day = CalendarDay(epochMillis: $0.date, calendar: calendar)
                return day >= bounds.start && day <= bounds.end
            }
            .reduce(MinorUnits(0)) { $0 + $1.amount }
        let progress = budget.limitAmount > 0 ? Double(spent) / Double(budget.limitAmount) : 0
        return BudgetProgress(spent: spent, progress: progress)
    }
}

/// Position du curseur « Aujourd'hui » sur la barre d'un budget — Android `BudgetPace`.
public struct BudgetPace: Equatable, Sendable {
    public let periodStatus: BudgetPeriodStatus
    public let periodStart: CalendarDay
    public let periodEnd: CalendarDay
    /// Durée de la période en jours, bornes incluses (1er → 30 = 30 jours), au moins 1.
    public let totalDays: Int
    public let elapsedDays: Int
    public let daysRemaining: Int
    /// 0…1 : position du curseur.
    public let elapsedRatio: Double
    public let dailyTheoretical: Double
    public let theoreticalSpentToDate: Double
    /// Dépensé − théorique du jour : positif = au-dessus du rythme.
    public let gap: Double
    public let paceState: BudgetPaceState

    /// Tolérance par défaut (±5 % du plafond), identique sur Android et le Web.
    public static let defaultTolerance = 0.05

    public static func of(
        budget: Budget,
        spent: MinorUnits,
        today: CalendarDay,
        calendar: Calendar,
        tolerance: Double = defaultTolerance
    ) -> BudgetPace {
        let (start, end) = bounds(of: budget, today: today, calendar: calendar)
        let totalDays = max(start.days(to: end, calendar: calendar) + 1, 1)
        let status = BudgetPeriodStatus.of(start: start, end: end, today: today)
        let clampedToday = min(max(today, start), end)
        let elapsedDays = status == .upcoming ? 0 : start.days(to: clampedToday, calendar: calendar) + 1
        let elapsedRatio = min(max(Double(elapsedDays) / Double(totalDays), 0), 1)
        let limit = Double(budget.limitAmount)
        let dailyTheoretical = limit / Double(totalDays)
        let theoreticalSpentToDate = dailyTheoretical * Double(elapsedDays)
        let gap = Double(spent) - theoreticalSpentToDate
        let toleranceAmount = limit * tolerance
        let paceState: BudgetPaceState
        if gap > toleranceAmount {
            paceState = .over
        } else if gap < -toleranceAmount {
            paceState = .ahead
        } else {
            paceState = .onTrack
        }
        return BudgetPace(
            periodStatus: status,
            periodStart: start,
            periodEnd: end,
            totalDays: totalDays,
            elapsedDays: elapsedDays,
            daysRemaining: max(totalDays - elapsedDays, 0),
            elapsedRatio: elapsedRatio,
            dailyTheoretical: dailyTheoretical,
            theoreticalSpentToDate: theoreticalSpentToDate,
            gap: gap,
            paceState: paceState
        )
    }

    /// Bornes RÉELLES (jours inclus) de la période d'un budget : ses dates s'il est à période fixe,
    /// sinon la semaine ISO / le mois civil contenant [today].
    public static func bounds(of budget: Budget, today: CalendarDay, calendar: Calendar) -> (start: CalendarDay, end: CalendarDay) {
        if let startDate = budget.startDate, let endDate = budget.endDate {
            return (CalendarDay(epochMillis: startDate, calendar: calendar), CalendarDay(epochMillis: endDate, calendar: calendar))
        }
        return (
            DatePeriods.currentPeriodStart(budget.period, today: today, calendar: calendar),
            DatePeriods.currentPeriodEnd(budget.period, today: today, calendar: calendar)
        )
    }
}
