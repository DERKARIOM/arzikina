import XCTest
@testable import ArzikinaDomain

/// Écran Rapports — comportements d'Android `StatsPeriodPreset` et `StatisticsViewModel`.
final class ReportsTests: XCTestCase {

    private let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Africa/Niamey")!
        calendar.firstWeekday = 2
        return calendar
    }()

    private func day(_ month: Int, _ day: Int, year: Int = 2026) -> CalendarDay {
        CalendarDay(year: year, month: month, day: day)
    }

    func testPresetsMatchAndroidAndWeb() {
        let today = day(3, 1)
        func bounds(_ preset: StatsPeriodPreset) -> [CalendarDay]? {
            preset.bounds(today: today, calendar: calendar).map { [$0.start, $0.end] }
        }
        XCTAssertEqual(bounds(.month), [day(3, 1), day(3, 31)])
        XCTAssertEqual(bounds(.previousMonth), [day(2, 1), day(2, 28)])
        XCTAssertEqual(bounds(.last7Days), [day(2, 23), day(3, 1)], "Aujourd'hui compris")
        XCTAssertEqual(bounds(.last30Days), [day(1, 31), day(3, 1)])
        XCTAssertEqual(bounds(.year), [day(1, 1), day(12, 31)])
        XCTAssertNil(bounds(.custom))
        let january = StatsPeriodPreset.previousMonth.bounds(today: day(1, 15), calendar: calendar)
        XCTAssertEqual(january?.start, day(12, 1, year: 2025))
    }

    func testCustomSelectionIsPrefilledThenValidated() {
        let today = day(9, 30)
        var selection = StatsPeriodSelection()
        selection.select(.custom, today: today, calendar: calendar)
        XCTAssertEqual(selection.customStart, day(9, 1), "Pré-remplie avec le mois en cours")
        XCTAssertEqual(selection.customEnd, day(9, 30))

        selection.changeCustomStart(day(10, 5))
        if case .failure(let error) = selection.resolve(today: today, calendar: calendar) {
            XCTAssertEqual(error, .startAfterEnd)
        } else { XCTFail() }

        selection.select(.year, today: today, calendar: calendar)
        XCTAssertEqual(selection.customStart, day(10, 5), "Saisie conservée")
        selection.reset()
        XCTAssertEqual(selection.preset, .month)
        XCTAssertNil(selection.customStart)
    }

    func testBreakdownSortsAndGroupsTheTail() {
        var amounts: [EntityID: MinorUnits] = [:]
        for index in 1...10 { amounts["c\(index)"] = MinorUnits(index * 100) }
        let rows = Reports.breakdown(amountsByCategory: amounts, categories: [:], visibleCount: 3)
        XCTAssertEqual(rows.map(\.categoryId), ["c10", "c9", "c8", nil])
        XCTAssertEqual(rows.last?.amount, 2_800)
        XCTAssertEqual(rows.reduce(0) { $0 + $1.share }, 1, accuracy: 1e-9)

        let oneLeft = Reports.breakdown(amountsByCategory: ["a": 3, "b": 2, "c": 1], categories: [:], visibleCount: 2)
        XCTAssertEqual(oneLeft.map(\.categoryId), ["a", "b", "c"], "Pas d'« Autres » pour une seule catégorie")
        XCTAssertTrue(Reports.breakdown(amountsByCategory: [:], categories: [:]).isEmpty)
        XCTAssertEqual(Reports.breakdown(amountsByCategory: ["b": 5, "a": 5], categories: [:]).map(\.categoryId), ["a", "b"], "Ordre stable")
    }

    func testEvolutionMonthsAndCurrency() {
        let months = Reports.evolutionMonths(today: day(2, 14), calendar: calendar)
        XCTAssertEqual(months, [day(9, 1, year: 2025), day(10, 1, year: 2025), day(11, 1, year: 2025), day(12, 1, year: 2025), day(1, 1), day(2, 1)])
        XCTAssertEqual(Reports.currencyCode(preference: "NGN", accounts: [Account(id: "a", name: "A")]), "NGN")
        XCTAssertEqual(Reports.currencyCode(preference: nil, accounts: [Account(id: "a", name: "A", currencyCode: "EUR")]), "EUR")
        XCTAssertEqual(Reports.currencyCode(preference: nil, accounts: []), "XOF")
    }
}
