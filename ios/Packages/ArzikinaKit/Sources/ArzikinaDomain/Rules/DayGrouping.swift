import Foundation

/// Regroupement par jour civil des listes de transactions (détail d'un compte, future liste des
/// transactions) — portage d'Android `TransactionDayGrouping`.
public enum DayGrouping {

    public struct Section<Item: Sendable>: Sendable {
        public let day: CalendarDay
        public let items: [Item]
    }

    /// Sections du jour le plus récent au plus ancien ; l'ordre des éléments est conservé dans
    /// chaque jour. [dateOf] donne l'instant de chaque élément.
    public static func group<Item: Sendable>(
        _ items: [Item],
        calendar: Calendar,
        dateOf: (Item) -> EpochMillis
    ) -> [Section<Item>] {
        var order: [CalendarDay] = []
        var byDay: [CalendarDay: [Item]] = [:]
        for item in items {
            let day = CalendarDay(epochMillis: dateOf(item), calendar: calendar)
            if byDay[day] == nil { order.append(day) }
            byDay[day, default: []].append(item)
        }
        return order.sorted(by: >).map { Section(day: $0, items: byDay[$0]!) }
    }
}
