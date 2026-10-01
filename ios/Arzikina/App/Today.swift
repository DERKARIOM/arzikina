import ArzikinaDomain
import Foundation

extension CalendarDay {
    /// Jour courant dans le fuseau de l'appareil (périodes de budget, filtres « cette semaine »…).
    static func today(calendar: Calendar = ArzikinaCalendar.current) -> CalendarDay {
        CalendarDay(epochMillis: EpochMillis(Date().timeIntervalSince1970 * 1000), calendar: calendar)
    }
}
