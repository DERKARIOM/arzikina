import ArzikinaDomain
import Foundation
import XCTest
@testable import ArzikinaData

final class AccountOverviewRepositoryTests: XCTestCase {

    private var space: UserDataSpace!

    override func setUpWithError() throws {
        space = try UserDataSpace.inMemory()
    }

    override func tearDown() {
        space.close()
    }

    private func firstSummaries() async throws -> [AccountSummary] {
        var iterator = space.accountOverview.observeAccountSummaries().makeAsyncIterator()
        let value = await iterator.next()
        return try XCTUnwrap(value)
    }

    private func firstDetail(_ id: EntityID) async -> AccountDetail?? {
        var iterator = space.accountOverview.observeAccountDetail(id: id).makeAsyncIterator()
        return await iterator.next()
    }

    func testSummariesCarryBalancesInDisplayOrderAndSavingsGoalProgress() async throws {
        try await space.accounts.save(Account(id: "goal", name: "Moto", initialBalance: 0, type: .savingsGoal, displayOrder: 1, savingsTargetAmount: 400_000))
        try await space.accounts.save(Account(id: "cash", name: "Espèces", initialBalance: 10_000, displayOrder: 0))
        try await space.transactions.save(Transaction(id: "t", amount: 100_000, type: .transfer, accountId: "cash", transferAccountId: "goal", date: 1))

        let summaries = try await firstSummaries()

        XCTAssertEqual(summaries.map(\.id), ["cash", "goal"])
        XCTAssertEqual(summaries.map(\.balance), [-90_000, 100_000])
        XCTAssertNil(summaries[0].savingsGoal)
        XCTAssertEqual(summaries[1].savingsGoal?.percent, 25)
        XCTAssertEqual(summaries[1].savingsGoal?.remaining, 300_000)
    }

    func testDetailListsOwnAndIncomingTransfersWithRunningBalanceAndHidesFees() async throws {
        try await space.accounts.save(Account(id: "om", name: "Orange Money", initialBalance: 50_000))
        try await space.accounts.save(Account(id: "bank", name: "Banque", initialBalance: 0))
        try await space.transactions.save(Transaction(id: "in", amount: 20_000, type: .income, accountId: "om", date: 1))
        try await space.transactions.save(Transaction(id: "fee", amount: 500, type: .expense, accountId: "om", date: 2))
        try await space.transactions.save(Transaction(id: "send", amount: 10_000, type: .transfer, accountId: "om", transferAccountId: "bank", date: 2, feeTransactionId: "fee"))
        try await space.transactions.save(Transaction(id: "back", amount: 3_000, type: .transfer, accountId: "bank", transferAccountId: "om", date: 3))
        try await space.transactions.save(Transaction(id: "other", amount: 99, type: .income, accountId: "bank", date: 4))

        let value = await firstDetail("om")
        let detail = try XCTUnwrap(value ?? nil)

        XCTAssertEqual(detail.summary.balance, 50_000 + 20_000 - 500 - 10_000 + 3_000)
        XCTAssertEqual(detail.transactions.map(\.id), ["back", "send", "in"], "Le frais n'a pas de ligne ; la transaction d'un autre compte non plus")
        XCTAssertEqual(detail.transactions.first?.runningBalance, detail.summary.balance, "Le solde après la plus récente est le solde courant")
        XCTAssertEqual(detail.transactions.map(\.runningBalance), [62_500, 59_500, 70_000])
        let send = detail.transactions[1]
        XCTAssertEqual(send.feeAmount, 500)
        XCTAssertEqual(send.transferAccount?.id, "bank")
        XCTAssertEqual(detail.transactions[0].transferAccount?.id, "bank", "Transfert reçu : l'autre compte est la source")
    }

    func testNextDisplayOrderFollowsExistingAccounts() async throws {
        let empty = try await space.accounts.nextDisplayOrder()
        XCTAssertEqual(empty, 0)
        try await space.accounts.save(Account(id: "a", name: "A", displayOrder: 4))
        try await space.accounts.save(Account(id: "b", name: "B", displayOrder: 9))
        try await space.accounts.delete(id: "b")
        let next = try await space.accounts.nextDisplayOrder()
        XCTAssertEqual(next, 5, "Les comptes supprimés ne comptent pas")
    }

    func testDetailOfDeletedOrUnknownAccountIsNil() async throws {
        try await space.accounts.save(Account(id: "a", name: "A"))
        try await space.accounts.delete(id: "a")

        let deleted = await firstDetail("a")
        let unknown = await firstDetail("nope")
        XCTAssertEqual(deleted, .some(nil))
        XCTAssertEqual(unknown, .some(nil))
    }

    /// Le solde après la ligne la plus récente doit TOUJOURS être égal au solde SQL (deux
    /// calculs indépendants, sur un jeu varié avec transferts dans les deux sens).
    func testRunningBalanceAgreesWithSqlBalanceOnVariedData() async throws {
        let ids = ["a", "b", "c"]
        for (index, id) in ids.enumerated() {
            try await space.accounts.save(Account(id: id, name: id, initialBalance: Int64(index) * 1_000))
        }
        for index in 0..<90 {
            let type: TransactionType = [.income, .expense, .transfer][index % 3]
            try await space.transactions.save(Transaction(
                id: "t\(index)",
                amount: Int64(37 * index + 5),
                type: type,
                accountId: ids[(index / 3) % 3],
                transferAccountId: type == .transfer ? ids[(index / 3 + 1) % 3] : nil,
                date: EpochMillis(index % 17)
            ))
        }
        for id in ids {
            let value = await firstDetail(id)
            let detail = try XCTUnwrap(value ?? nil)
            XCTAssertEqual(detail.transactions.first?.runningBalance, detail.summary.balance, id)
        }
    }
}
