import Foundation

/// Jour calendaire (sans heure ni fuseau) — équivalent de `java.time.LocalDate` côté Android.
///
/// Les règles métier comparent des JOURS, pas des millisecondes (un prêt n'est en retard qu'à
/// partir du lendemain de son échéance, un budget couvre des journées entières…). Ce type rend
/// cette intention explicite et évite les erreurs de fuseau.
public struct CalendarDay: Hashable, Comparable, Sendable, CustomStringConvertible {
    public let year: Int
    public let month: Int
    public let day: Int

    public init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    /// Jour calendaire de l'instant [epochMillis] dans le fuseau de [calendar].
    public init(epochMillis: EpochMillis, calendar: Calendar) {
        let date = Date(timeIntervalSince1970: TimeInterval(epochMillis) / 1000)
        let components = calendar.dateComponents([.year, .month, .day], from: date)
        self.init(year: components.year ?? 1970, month: components.month ?? 1, day: components.day ?? 1)
    }

    /// Analyse une date ISO `AAAA-MM-JJ` (ex. « 2026-09-30 »). `nil` si le format est invalide.
    public init?(iso: String) {
        let parts = iso.split(separator: "-")
        guard parts.count == 3,
              let year = Int(parts[0]), let month = Int(parts[1]), let day = Int(parts[2]),
              (1...12).contains(month), (1...31).contains(day)
        else { return nil }
        self.init(year: year, month: month, day: day)
    }

    public var description: String {
        String(format: "%04d-%02d-%02d", year, month, day)
    }

    public static func < (lhs: CalendarDay, rhs: CalendarDay) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    // MARK: - Conversions

    /// Instant de début de ce jour (00:00) dans le fuseau de [calendar] — équivalent de
    /// `LocalDate.atStartOfDay(zone)` côté Android.
    public func startOfDayMillis(calendar: Calendar) -> EpochMillis {
        millis(hour: 0, minute: 0, second: 0, calendar: calendar)
    }

    /// Instant « ce jour à [hour]:[minute] » dans le fuseau de [calendar].
    public func millis(hour: Int, minute: Int, second: Int = 0, calendar: Calendar) -> EpochMillis {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = day
        components.hour = hour
        components.minute = minute
        components.second = second
        let date = calendar.date(from: components) ?? Date(timeIntervalSince1970: 0)
        return EpochMillis((date.timeIntervalSince1970 * 1000).rounded())
    }

    // MARK: - Arithmétique

    /// Ajoute une durée calendaire. Les mois et années sont « civils » : le jour est ramené au
    /// dernier jour du mois si nécessaire (31 janvier + 1 mois = 28/29 février), exactement comme
    /// `LocalDate.plusMonths` côté Android.
    public func adding(_ component: Calendar.Component, _ value: Int, calendar: Calendar) -> CalendarDay {
        let noon = Date(timeIntervalSince1970: TimeInterval(millis(hour: 12, minute: 0, calendar: calendar)) / 1000)
        let shifted = calendar.date(byAdding: component, value: value, to: noon) ?? noon
        let components = calendar.dateComponents([.year, .month, .day], from: shifted)
        return CalendarDay(year: components.year ?? year, month: components.month ?? month, day: components.day ?? day)
    }

    /// Nombre de jours de [self] à [other] (négatif si [other] est antérieur) — équivalent de
    /// `ChronoUnit.DAYS.between(self, other)`.
    public func days(to other: CalendarDay, calendar: Calendar) -> Int {
        let from = Date(timeIntervalSince1970: TimeInterval(millis(hour: 12, minute: 0, calendar: calendar)) / 1000)
        let to = Date(timeIntervalSince1970: TimeInterval(other.millis(hour: 12, minute: 0, calendar: calendar)) / 1000)
        return calendar.dateComponents([.day], from: from, to: to).day ?? 0
    }

    /// Jour de la semaine ISO : 1 = lundi … 7 = dimanche.
    public func isoWeekday(calendar: Calendar) -> Int {
        let date = Date(timeIntervalSince1970: TimeInterval(millis(hour: 12, minute: 0, calendar: calendar)) / 1000)
        let weekday = calendar.component(.weekday, from: date) // 1 = dimanche … 7 = samedi
        return weekday == 1 ? 7 : weekday - 1
    }

    /// Nombre de jours du mois de ce jour.
    public func daysInMonth(calendar: Calendar) -> Int {
        let firstOfMonth = CalendarDay(year: year, month: month, day: 1)
        return firstOfMonth.days(to: firstOfMonth.adding(.month, 1, calendar: calendar), calendar: calendar)
    }
}
