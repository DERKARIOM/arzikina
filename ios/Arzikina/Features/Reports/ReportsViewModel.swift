import ArzikinaDomain
import Foundation
import Observation

/// Onglet Rapports : totaux et répartition par catégorie sur une période choisie, évolution des
/// 6 derniers mois — Android `StatisticsViewModel`. Les sommes sont faites par la base
/// (`ReportsRepository`) ; ce ViewModel gère la sélection et la présentation.
@MainActor
@Observable
final class ReportsViewModel {

    private(set) var selection = StatsPeriodSelection()
    var breakdownType: BreakdownType = .expense
    private(set) var snapshot: ReportSnapshot?

    @ObservationIgnored private let calendar: Calendar

    init(calendar: Calendar = ArzikinaCalendar.current) {
        self.calendar = calendar
    }

    /// Période résolue (`nil` + [periodError] si la période personnalisée est invalide).
    func period(today: CalendarDay) -> (start: CalendarDay, end: CalendarDay)? {
        try? selection.resolve(today: today, calendar: calendar).get()
    }

    func periodError(today: CalendarDay) -> StatsPeriodError? {
        if case .failure(let error) = selection.resolve(today: today, calendar: calendar) { return error }
        return nil
    }

    func select(_ preset: StatsPeriodPreset, today: CalendarDay) {
        selection.select(preset, today: today, calendar: calendar)
    }

    func changeCustomStart(_ date: Date) { selection.changeCustomStart(day(date)) }
    func changeCustomEnd(_ date: Date) { selection.changeCustomEnd(day(date)) }
    func resetPeriod() { selection.reset() }

    var customStart: Date { date(selection.customStart) }
    var customEnd: Date { date(selection.customEnd) }

    /// Suit le rapport jusqu'à l'annulation (changement de période, de type, de jour ou
    /// d'utilisateur : la vue relance l'observation).
    func observe(_ repository: ReportsRepository, today: CalendarDay) async {
        let stream = repository.observeReport(period: period(today: today), breakdownType: breakdownType, today: today, calendar: calendar)
        for await snapshot in stream {
            self.snapshot = snapshot
        }
    }

    private func date(_ day: CalendarDay?) -> Date {
        let millis = (day ?? .today(calendar: calendar)).startOfDayMillis(calendar: calendar)
        return Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
    }

    private func day(_ date: Date) -> CalendarDay {
        CalendarDay(epochMillis: EpochMillis((date.timeIntervalSince1970 * 1000).rounded()), calendar: calendar)
    }
}
