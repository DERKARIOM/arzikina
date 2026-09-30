import Foundation

/// Semaine ISO et mois civil « en cours » — portage d'Android `util/DatePeriods.kt`, partagé par
/// les budgets et les filtres par période.
public enum DatePeriods {

    /// Premier jour de la période en cours : lundi de la semaine ISO, ou 1er du mois.
    public static func currentPeriodStart(_ period: BudgetPeriod, today: CalendarDay, calendar: Calendar) -> CalendarDay {
        switch period {
        case .monthly:
            return CalendarDay(year: today.year, month: today.month, day: 1)
        case .weekly:
            return today.adding(.day, -(today.isoWeekday(calendar: calendar) - 1), calendar: calendar)
        }
    }

    /// Dernier jour de la période en cours : dimanche de la semaine ISO, ou dernier jour du mois.
    public static func currentPeriodEnd(_ period: BudgetPeriod, today: CalendarDay, calendar: Calendar) -> CalendarDay {
        switch period {
        case .monthly:
            return CalendarDay(year: today.year, month: today.month, day: today.daysInMonth(calendar: calendar))
        case .weekly:
            return today.adding(.day, 7 - today.isoWeekday(calendar: calendar), calendar: calendar)
        }
    }

    /// `true` si le jour de [epochMillis] appartient à la période en cours contenant [today].
    public static func isInCurrentPeriod(
        _ epochMillis: EpochMillis,
        period: BudgetPeriod,
        today: CalendarDay,
        calendar: Calendar
    ) -> Bool {
        let day = CalendarDay(epochMillis: epochMillis, calendar: calendar)
        return day >= currentPeriodStart(period, today: today, calendar: calendar)
            && day <= currentPeriodEnd(period, today: today, calendar: calendar)
    }
}
