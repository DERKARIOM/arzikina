import ArzikinaDomain
import GRDB
import XCTest
@testable import ArzikinaData

final class ReportsRepositoryTests: XCTestCase {

    private var space: UserDataSpace!
    private let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Africa/Niamey")!
        calendar.firstWeekday = 2
        return calendar
    }()
    private let today = CalendarDay(year: 2026, month: 9, day: 30)

    override func setUpWithError() throws {
        space = try UserDataSpace.inMemory()
    }

    override func tearDown() {
        space.close()
    }

    private func report(_ period: (start: CalendarDay, end: CalendarDay)?, _ type: BreakdownType = .expense) async throws -> ReportSnapshot {
        var iterator = space.reports.observeReport(period: period, breakdownType: type, today: today, calendar: calendar).makeAsyncIterator()
        let value = await iterator.next()
        return try XCTUnwrap(value)
    }

    private func millis(_ month: Int, _ day: Int, hour: Int) -> EpochMillis {
        CalendarDay(year: 2026, month: month, day: day).millis(hour: hour, minute: 0, calendar: calendar)
    }

    func testCurrencyComesFromSyncedPreferenceThenFirstAccount() async throws {
        try await space.accounts.save(Account(id: "a", name: "A", currencyCode: "EUR"))
        var snapshot = try await report(nil)
        XCTAssertEqual(snapshot.currencyCode, "EUR")
        XCTAssertEqual(snapshot.evolution.count, 6)
        XCTAssertEqual(snapshot.income, 0)

        try await space.database.writer.write { db in
            try db.execute(sql: "INSERT INTO user_preferences (id, themeMode, currencyCode, createdAt, updatedAt, version) VALUES ('p', 'DARK', 'NGN', 0, 0, 1)")
        }
        snapshot = try await report(nil)
        XCTAssertEqual(snapshot.currencyCode, "NGN")
    }

    /// Les sommes SQL doivent donner EXACTEMENT le calcul de référence du domaine (porté
    /// d'Android) : devises, compte exclu, transferts, supprimées, bornes de jour et de mois.
    func testSqlMatchesDomainReference() async throws {
        try await space.accounts.save(Account(id: "a", name: "A"))
        try await space.accounts.save(Account(id: "b", name: "B"))
        try await space.accounts.save(Account(id: "e", name: "E", currencyCode: "EUR"))
        try await space.accounts.save(Account(id: "x", name: "X", isExcludedFromStatistics: true))
        let categoryIds = ["c1", "c2", "c3", "c4"]
        for (index, id) in categoryIds.enumerated() {
            try await space.categories.save(ArzikinaDomain.Category(id: id, name: id, type: index % 2 == 0 ? .expense : .income))
        }
        let accountIds = ["a", "b", "e", "x"]
        var transactions: [Transaction] = []
        for index in 0..<200 {
            let type: TransactionType = [.expense, .income, .expense, .transfer][index % 4]
            let transaction = Transaction(
                id: "t\(index)",
                amount: Int64(17 * index + 3),
                type: type,
                accountId: accountIds[index % 4 == 3 ? 0 : (index / 4) % 4],
                transferAccountId: type == .transfer ? "b" : nil,
                categoryId: type == .transfer ? nil : categoryIds[(index / 3) % 4],
                date: millis(3 + (index % 7), 1 + (index * 11) % 30, hour: (index * 7) % 24)
            )
            transactions.append(transaction)
            try await space.transactions.save(transaction)
        }
        try await space.transactions.delete(id: "t0")
        transactions.removeAll { $0.id == "t0" }

        // Cas limites : minuit pile le lendemain de la période (exclu), dépense sans catégorie
        // (données d'une autre plateforme), compte supprimé (ses transactions ne comptent plus).
        let edges = [
            Transaction(id: "midnight", amount: 5_000, type: .expense, accountId: "a", categoryId: "c1", date: millis(10, 1, hour: 0)),
            Transaction(id: "lastSecond", amount: 7_000, type: .income, accountId: "a", categoryId: "c2", date: millis(9, 30, hour: 23) + 3_599_999),
            Transaction(id: "noCategory", amount: 900, type: .expense, accountId: "a", date: millis(9, 12, hour: 9)),
        ]
        for edge in edges {
            transactions.append(edge)
            try await space.transactions.save(edge)
        }
        try await space.accounts.save(Account(id: "gone", name: "Gone"))
        try await space.transactions.save(Transaction(id: "orphan", amount: 9_999, type: .expense, accountId: "gone", categoryId: "c1", date: millis(9, 12, hour: 9)))
        try await space.accounts.delete(id: "gone")

        let accounts = try await space.database.writer.read { db in try LocalAccountRepository.activeAccounts(db) }
        var iterator = space.categories.observeCategories(type: nil).makeAsyncIterator()
        let categories = await iterator.next() ?? []
        let periods: [(start: CalendarDay, end: CalendarDay)?] = [
            StatsPeriodPreset.month.bounds(today: today, calendar: calendar),
            StatsPeriodPreset.previousMonth.bounds(today: today, calendar: calendar),
            StatsPeriodPreset.last7Days.bounds(today: today, calendar: calendar),
            (CalendarDay(year: 2026, month: 4, day: 10), CalendarDay(year: 2026, month: 7, day: 2)),
            nil,
        ]
        for period in periods {
            for type in BreakdownType.allCases {
                let sql = try await report(period, type)
                let expected = Reports.compute(transactions: transactions, accounts: accounts, categories: categories, currencyCode: "XOF", period: period, breakdownType: type, today: today, calendar: calendar)
                XCTAssertEqual(sql, expected, "\(String(describing: period)) \(type)")
            }
        }
        let wide = try await report((CalendarDay(year: 2026, month: 1, day: 1), CalendarDay(year: 2026, month: 12, day: 31)))
        XCTAssertGreaterThan(wide.income, 0, "Le jeu doit exercer les sommes")
        XCTAssertGreaterThan(wide.breakdown.count, 1)
    }
}
