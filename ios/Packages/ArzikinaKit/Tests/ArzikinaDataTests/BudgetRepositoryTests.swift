import ArzikinaDomain
import GRDB
import XCTest
@testable import ArzikinaData

final class BudgetRepositoryTests: XCTestCase {

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

    private func millis(_ month: Int, _ day: Int, hour: Int = 12) -> EpochMillis {
        CalendarDay(year: 2026, month: month, day: day).millis(hour: hour, minute: 0, calendar: calendar)
    }

    private func summaries() async throws -> [BudgetSummary] {
        var iterator = space.budgets.observeSummaries(today: today, calendar: calendar).makeAsyncIterator()
        let value = await iterator.next()
        return try XCTUnwrap(value)
    }

    func testSaveDeleteAndQueue() async throws {
        let budget = Budget(id: "b", categoryId: "food", limitAmount: 5_000, startDate: millis(9, 1, hour: 0), endDate: millis(9, 30, hour: 0))
        try await space.budgets.save(budget)
        var iterator = space.budgets.observeBudgets().makeAsyncIterator()
        let saved = await iterator.next()
        XCTAssertEqual(saved?.first?.limitAmount, 5_000)

        try await space.budgets.delete(id: "b")
        let list = try await summaries()
        XCTAssertTrue(list.isEmpty)
        let queued = try await space.database.writer.read { db in
            try String.fetchAll(db, sql: "SELECT entityType FROM sync_queue")
        }
        XCTAssertFalse(queued.contains("budgets"), "Créé puis supprimé hors ligne : rien à envoyer")
    }

    func testSpentCountsOnlyPersonalExpensesOfTheCategoryInCurrencyAndPeriod() async throws {
        try await space.accounts.save(Account(id: "cash", name: "Espèces"))
        try await space.accounts.save(Account(id: "other", name: "Pour maman", isExcludedFromStatistics: true))
        try await space.accounts.save(Account(id: "euro", name: "Compte €", currencyCode: "EUR"))
        try await space.categories.save(ArzikinaDomain.Category(id: "food", name: "Nourriture", type: .expense))
        try await space.budgets.save(Budget(id: "b", categoryId: "food", limitAmount: 10_000, startDate: millis(9, 1, hour: 0), endDate: millis(9, 30, hour: 0)))

        let rows: [Transaction] = [
            Transaction(id: "in", amount: 1_000, type: .expense, accountId: "cash", categoryId: "food", date: millis(9, 1, hour: 0)),
            Transaction(id: "last", amount: 2_000, type: .expense, accountId: "cash", categoryId: "food", date: millis(9, 30, hour: 23)),
            Transaction(id: "before", amount: 400, type: .expense, accountId: "cash", categoryId: "food", date: millis(8, 31, hour: 23)),
            Transaction(id: "after", amount: 800, type: .expense, accountId: "cash", categoryId: "food", date: millis(10, 1, hour: 0)),
            Transaction(id: "excluded", amount: 5_000, type: .expense, accountId: "other", categoryId: "food", date: millis(9, 10)),
            Transaction(id: "currency", amount: 7_000, type: .expense, accountId: "euro", categoryId: "food", date: millis(9, 10)),
            Transaction(id: "income", amount: 9_000, type: .income, accountId: "cash", categoryId: "food", date: millis(9, 10)),
            Transaction(id: "otherCat", amount: 9_000, type: .expense, accountId: "cash", categoryId: "fuel", date: millis(9, 10)),
        ]
        for row in rows { try await space.transactions.save(row) }
        try await space.transactions.save(Transaction(id: "gone", amount: 3_000, type: .expense, accountId: "cash", categoryId: "food", date: millis(9, 10)))
        try await space.transactions.delete(id: "gone")

        let list = try await summaries()
        let summary = try XCTUnwrap(list.first)
        XCTAssertEqual(summary.spent, 3_000)
        XCTAssertEqual(summary.category?.id, "food")
        XCTAssertEqual(summary.percent, 30)
    }

    /// La somme SQL doit donner EXACTEMENT la règle du domaine (`BudgetProgress.compute`, portée
    /// d'Android) sur un jeu varié : périodes fixes et récurrentes, devises, compte exclu, bornes.
    func testSqlSpentMatchesDomainRule() async throws {
        try await space.accounts.save(Account(id: "a", name: "A"))
        try await space.accounts.save(Account(id: "b", name: "B", currencyCode: "EUR"))
        try await space.accounts.save(Account(id: "x", name: "X", isExcludedFromStatistics: true))
        let accountIds = ["a", "b", "x"]
        let categoryIds = ["c1", "c2"]
        var transactions: [Transaction] = []
        for index in 0..<120 {
            let transaction = Transaction(
                id: "t\(index)",
                amount: Int64(13 * index + 7),
                type: index % 5 == 0 ? .income : .expense,
                accountId: accountIds[index % 3],
                categoryId: categoryIds[index % 2],
                date: millis(8 + (index % 3), 1 + (index * 7) % 28, hour: (index * 5) % 24)
            )
            transactions.append(transaction)
            try await space.transactions.save(transaction)
        }
        let budgets = [
            Budget(id: "fixed", categoryId: "c1", limitAmount: 50_000, startDate: millis(9, 3, hour: 0), endDate: millis(9, 17, hour: 0)),
            Budget(id: "eur", categoryId: "c2", limitAmount: 50_000, currencyCode: "EUR", startDate: millis(8, 1, hour: 0), endDate: millis(10, 31, hour: 0)),
            Budget(id: "month", categoryId: "c2", period: .monthly, limitAmount: 50_000),
            Budget(id: "week", categoryId: "c1", period: .weekly, limitAmount: 50_000),
        ]
        for budget in budgets { try await space.budgets.save(budget) }

        let accounts = try await space.database.writer.read { db in try LocalAccountRepository.activeAccounts(db) }
        let scope = PersonalStatistics.scope(accounts: accounts, transactions: transactions)
        let accountsById = Dictionary(uniqueKeysWithValues: accounts.map { ($0.id, $0) })
        let list = try await summaries()
        let sql = Dictionary(uniqueKeysWithValues: list.map { ($0.id, $0.spent) })

        for budget in budgets {
            let expected = BudgetProgress.compute(budget: budget, transactions: scope.transactions, accountsById: accountsById, today: today, calendar: calendar).spent
            XCTAssertEqual(sql[budget.id], expected, budget.id)
        }
        XCTAssertGreaterThan(sql.values.reduce(0, +), 0, "Le jeu doit exercer la somme")
    }
}
