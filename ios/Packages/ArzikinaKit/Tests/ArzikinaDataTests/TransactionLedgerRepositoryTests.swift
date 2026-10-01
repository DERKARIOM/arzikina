import ArzikinaDomain
import XCTest
@testable import ArzikinaData

final class TransactionLedgerRepositoryTests: XCTestCase {

    private var space: UserDataSpace!

    override func setUpWithError() throws {
        space = try UserDataSpace.inMemory()
    }

    override func tearDown() {
        space.close()
    }

    private func firstLedger() async throws -> [TransactionLedgerEntry] {
        var iterator = space.ledger.observeLedger().makeAsyncIterator()
        let value = await iterator.next()
        return try XCTUnwrap(value)
    }

    func testLedgerHidesFeesAndCarriesBothBalancesOfATransfer() async throws {
        try await space.accounts.save(Account(id: "om", name: "Orange Money", initialBalance: 50_000))
        try await space.accounts.save(Account(id: "bank", name: "Banque", initialBalance: 1_000))
        try await space.categories.save(Category(id: "food", name: "Nourriture", type: .expense))
        try await space.transactions.save(Transaction(id: "lunch", amount: 2_000, type: .expense, accountId: "om", categoryId: "food", date: 1))
        try await space.transactions.save(
            Transaction(id: "send", amount: 10_000, type: .transfer, accountId: "om", transferAccountId: "bank", date: 2),
            fee: TransactionFee(amount: 500, accountId: "om", type: .transfer)
        )

        let ledger = try await firstLedger()

        XCTAssertEqual(ledger.map(\.id), ["send", "lunch"], "Le frais n'a pas de ligne propre")
        let send = ledger[0]
        XCTAssertEqual(send.feeAmount, 500)
        XCTAssertEqual(send.sourceAccount?.id, "om")
        XCTAssertEqual(send.destinationAccount?.id, "bank")
        XCTAssertEqual(send.sourceBalanceAfter, 50_000 - 2_000 - 500 - 10_000)
        XCTAssertEqual(send.destinationBalanceAfter, 11_000)
        XCTAssertEqual(ledger[1].category?.id, "food")
        XCTAssertEqual(ledger[1].sourceBalanceAfter, 48_000)
        XCTAssertNil(ledger[1].destinationAccount)
    }

    /// Une même ligne affiche le même solde dans la liste globale et dans le détail du compte.
    func testBalancesMatchAccountDetailOnVariedData() async throws {
        let ids = ["a", "b", "c"]
        for (index, id) in ids.enumerated() {
            try await space.accounts.save(Account(id: id, name: id, initialBalance: Int64(index) * 1_000))
        }
        for index in 0..<60 {
            let type: TransactionType = [.income, .expense, .transfer][index % 3]
            try await space.transactions.save(Transaction(
                id: "t\(index)",
                amount: Int64(41 * index + 3),
                type: type,
                accountId: ids[(index / 3) % 3],
                transferAccountId: type == .transfer ? ids[(index / 3 + 1) % 3] : nil,
                date: EpochMillis(index % 11)
            ))
        }

        let ledger = try await firstLedger()
        for id in ids {
            var iterator = space.accountOverview.observeAccountDetail(id: id).makeAsyncIterator()
            let value = await iterator.next()
            let detail = try XCTUnwrap(value ?? nil)
            for row in detail.transactions {
                let entry = try XCTUnwrap(ledger.first { $0.id == row.id })
                let expected = entry.transaction.accountId == id ? entry.sourceBalanceAfter : entry.destinationBalanceAfter
                XCTAssertEqual(expected, row.runningBalance, "\(id) / \(row.id)")
            }
        }
    }

    func testDeletedTransactionsAndTheirFeesLeaveTheLedger() async throws {
        try await space.accounts.save(Account(id: "a", name: "A"))
        try await space.transactions.save(
            Transaction(id: "t", amount: 1_000, type: .expense, accountId: "a", date: 1),
            fee: TransactionFee(amount: 50, accountId: "a", type: .other)
        )
        try await space.transactions.delete(id: "t")

        let ledger = try await firstLedger()
        XCTAssertTrue(ledger.isEmpty)
    }
}
