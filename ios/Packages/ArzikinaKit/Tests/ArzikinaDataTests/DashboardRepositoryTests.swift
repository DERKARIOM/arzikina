import ArzikinaDomain
import Foundation
import GRDB
import XCTest
@testable import ArzikinaData

final class DashboardRepositoryTests: XCTestCase {

    private var space: UserDataSpace!

    override func setUpWithError() throws {
        space = try UserDataSpace.inMemory()
    }

    override func tearDown() {
        space.close()
    }

    private func firstSnapshot(start: EpochMillis = 0, end: EpochMillis = .max, limit: Int = 5) async throws -> DashboardSnapshot {
        var iterator = space.dashboard.observeDashboard(monthStart: start, monthEnd: end, recentLimit: limit).makeAsyncIterator()
        let value = await iterator.next()
        return try XCTUnwrap(value)
    }

    func testEmptyDatabase() async throws {
        let snapshot = try await firstSnapshot()
        XCTAssertEqual(snapshot, .empty)
    }

    func testRecentTransactionsHideFeeTransactionsAndCarryTheirAmount() async throws {
        try await space.accounts.save(Account(id: "om", name: "Orange Money"))
        try await space.accounts.save(Account(id: "cash", name: "Espèces"))
        try await space.categories.save(Category(id: "food", name: "Nourriture", type: .expense))
        try await space.transactions.save(Transaction(id: "fee", amount: 150, type: .expense, accountId: "om", date: 10))
        try await space.transactions.save(Transaction(id: "pay", amount: 10_000, type: .expense, accountId: "om", categoryId: "food", date: 10, feeTransactionId: "fee"))
        try await space.transactions.save(Transaction(id: "move", amount: 500, type: .transfer, accountId: "om", transferAccountId: "cash", date: 20))
        try await space.transactions.save(Transaction(id: "old", amount: 1, type: .income, accountId: "cash", date: 1))

        let snapshot = try await firstSnapshot(limit: 2)

        XCTAssertEqual(snapshot.recentTransactions.map(\.id), ["move", "pay"])
        let move = snapshot.recentTransactions[0]
        XCTAssertEqual(move.account?.id, "om")
        XCTAssertEqual(move.transferAccount?.id, "cash")
        let pay = snapshot.recentTransactions[1]
        XCTAssertEqual(pay.feeAmount, 150)
        XCTAssertEqual(pay.category?.systemKey, .food)
    }

    func testDeletedReferencesAreNotResolved() async throws {
        try await space.accounts.save(Account(id: "a", name: "A"))
        try await space.categories.save(Category(id: "c", name: "C", type: .expense))
        try await space.transactions.save(Transaction(id: "t", amount: 5, type: .expense, accountId: "a", categoryId: "c", date: 1))
        try await space.categories.delete(id: "c")

        let snapshot = try await firstSnapshot()

        XCTAssertEqual(snapshot.recentTransactions.first?.id, "t")
        XCTAssertNil(snapshot.recentTransactions.first?.category)
    }

    /// Les agrégats SQL doivent donner EXACTEMENT les règles du domaine (elles-mêmes portées
    /// d'Android), sur un jeu varié : devises, compte exclu, transferts, bornes du mois,
    /// transactions supprimées.
    func testSqlAggregatesMatchDomainRules() async throws {
        let accounts = [
            Account(id: "xof1", name: "Espèces", currencyCode: "XOF", initialBalance: 10_000, displayOrder: 0),
            Account(id: "eur", name: "Euro", currencyCode: "EUR", initialBalance: 2_000, displayOrder: 1),
            Account(id: "xof2", name: "Banque", currencyCode: "XOF", initialBalance: 50_000, displayOrder: 2),
            Account(id: "excl", name: "Asso", currencyCode: "XOF", initialBalance: 7_000, isExcludedFromStatistics: true, displayOrder: 3)
        ]
        for account in accounts { try await space.accounts.save(account) }
        let start: EpochMillis = 1_000_000
        let end: EpochMillis = 2_000_000
        var transactions: [Transaction] = []
        let ids = accounts.map(\.id)
        for index in 0..<120 {
            let type: TransactionType = [.income, .expense, .expense, .transfer][index % 4]
            // (index / 3) : chaque compte reçoit des transactions de chaque type.
            let account = ids[(index / 3) % ids.count]
            let transaction = Transaction(
                id: "t\(index)",
                amount: EpochMillis(100 + index * 37),
                type: type,
                accountId: account,
                transferAccountId: type == .transfer ? ids[(index + 1) % ids.count] : nil,
                date: start - 50_000 + EpochMillis(index) * 10_000
            )
            transactions.append(transaction)
            try await space.transactions.save(transaction)
        }
        try await space.transactions.delete(id: "t5")
        transactions.removeAll { $0.id == "t5" }

        let snapshot = try await firstSnapshot(start: start, end: end)

        XCTAssertEqual(snapshot.month, DashboardRules.periodTotals(accounts: accounts, transactions: transactions, start: start, end: end))
        XCTAssertEqual(
            snapshot.totalBalances,
            DashboardRules.totalBalances(accounts: accounts, balances: AccountBalances.compute(accounts: accounts, transactions: transactions))
        )
        XCTAssertFalse(snapshot.month.income.isEmpty)
        XCTAssertEqual(snapshot.totalBalances.map(\.currencyCode), ["XOF", "EUR"])
    }

    func testSnapshotIsReEmittedAfterChanges() async throws {
        let stream = space.dashboard.observeDashboard(monthStart: 0, monthEnd: .max, recentLimit: 5)
        var iterator = stream.makeAsyncIterator()
        _ = await iterator.next()

        try await space.accounts.save(Account(id: "a", name: "A", initialBalance: 1_234))

        var latest: DashboardSnapshot?
        while latest?.totalBalances.first?.amountMinor != 1_234 {
            latest = await iterator.next()
            if latest == nil { break }
        }
        XCTAssertEqual(latest?.totalBalances, [CurrencyAmount(currencyCode: SupportedCurrency.defaultCode, amountMinor: 1_234)])
    }

    func testFeeLookupUsesIndex() throws {
        let details = try space.database.writer.read { db in
            try Row.fetchAll(db, sql: "EXPLAIN QUERY PLAN SELECT id FROM transactions WHERE feeTransactionId = 'x'")
                .map { $0["detail"] as String }
                .joined(separator: " ")
        }
        XCTAssertTrue(details.contains("transactions_on_feeTransactionId"), details)
    }
}
