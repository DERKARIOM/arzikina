import ArzikinaDomain
import Foundation
import GRDB
import XCTest
@testable import ArzikinaData

final class TransactionWithFeeTests: XCTestCase {

    private var space: UserDataSpace!

    override func setUpWithError() throws {
        space = try UserDataSpace.inMemory()
    }

    override func tearDown() {
        space.close()
    }

    private func payment(feeTransactionId: EntityID? = nil) -> Transaction {
        Transaction(id: "pay", amount: 100_000, type: .transfer, accountId: "om", transferAccountId: "bank", date: 50, feeTransactionId: feeTransactionId)
    }

    private func row(_ id: EntityID) throws -> TransactionRecord? {
        try space.database.writer.read { db in try TransactionRecord.fetchOne(db, key: id) }
    }

    private func queue() throws -> [String: String] {
        try space.database.writer.read { db in
            Dictionary(uniqueKeysWithValues: try SyncQueueRecord.fetchAll(db).map { ("\($0.entityType.rawValue)/\($0.entityId)", $0.operation.rawValue) })
        }
    }

    func testFeeBecomesLinkedExpenseInFeesCategoryCreatedOnce() async throws {
        try await space.transactions.save(payment(), fee: TransactionFee(amount: 1_500, accountId: "om", type: .transfer, description: "Wave"))

        let main = try XCTUnwrap(try row("pay"))
        let feeId = try XCTUnwrap(main.feeTransactionId)
        let fee = try XCTUnwrap(try row(feeId))
        XCTAssertEqual(fee.amount, 1_500)
        XCTAssertEqual(fee.type, "EXPENSE")
        XCTAssertEqual(fee.accountId, "om")
        XCTAssertEqual(fee.date, 50, "Les frais sont datés comme la transaction")
        XCTAssertEqual(fee.feeType, "TRANSFER")
        XCTAssertNil(main.feeType)
        let category = try await space.categories.category(id: try XCTUnwrap(fee.categoryId))
        XCTAssertEqual(category?.systemKey, .fees)

        // Une seconde transaction avec frais réutilise la même catégorie système.
        try await space.transactions.save(Transaction(id: "pay2", amount: 5, type: .expense, accountId: "om", categoryId: "c", date: 60), fee: TransactionFee(amount: 1, accountId: "om", type: .bank))
        let count = try await space.database.writer.read { db in
            try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM categories WHERE name = 'Frais et commissions'")
        }
        XCTAssertEqual(count, 1)

        let pending = try queue()
        let categoryId = try XCTUnwrap(category?.id)
        XCTAssertEqual(pending["transactions/pay"], "CREATE")
        XCTAssertEqual(pending["transactions/\(feeId)"], "CREATE")
        XCTAssertEqual(pending["categories/\(categoryId)"], "CREATE")
    }

    func testEditingUpdatesFeeInPlaceAndRemovingFeeDeletesIt() async throws {
        try await space.transactions.save(payment(), fee: TransactionFee(amount: 1_500, accountId: "om", type: .transfer))
        let feeId = try XCTUnwrap(try row("pay")?.feeTransactionId)

        try await space.transactions.save(payment(feeTransactionId: feeId), fee: TransactionFee(amount: 2_000, accountId: "bank", type: .service))
        XCTAssertEqual(try row("pay")?.feeTransactionId, feeId, "Jamais une deuxième transaction de frais")
        XCTAssertEqual(try row(feeId)?.amount, 2_000)
        XCTAssertEqual(try row(feeId)?.accountId, "bank")

        try await space.transactions.save(payment(feeTransactionId: feeId), fee: nil)
        XCTAssertNil(try row("pay")?.feeTransactionId)
        XCTAssertNotNil(try row(feeId)?.deletedAt, "Frais retirés : leur transaction est supprimée")
    }

    func testDeletingTransactionDeletesItsFee() async throws {
        try await space.transactions.save(payment(), fee: TransactionFee(amount: 1_500, accountId: "om", type: .transfer))
        let feeId = try XCTUnwrap(try row("pay")?.feeTransactionId)

        try await space.transactions.delete(id: "pay")

        XCTAssertNotNil(try row("pay")?.deletedAt)
        XCTAssertNotNil(try row(feeId)?.deletedAt)
        XCTAssertEqual(try queue()["transactions/pay"], nil, "Créée puis supprimée hors ligne : rien à envoyer")
    }

    func testFeeCountsInBalance() async throws {
        try await space.accounts.save(Account(id: "om", name: "OM", initialBalance: 200_000))
        try await space.accounts.save(Account(id: "bank", name: "Banque"))
        try await space.transactions.save(payment(), fee: TransactionFee(amount: 1_500, accountId: "om", type: .transfer))

        var iterator = space.accounts.observeBalances().makeAsyncIterator()
        let balances = await iterator.next()
        XCTAssertEqual(balances?["om"], 200_000 - 100_000 - 1_500)
        XCTAssertEqual(balances?["bank"], 100_000)
    }

    func testLoanLinkedTransactionsAreDetected() async throws {
        try await space.transactions.save(Transaction(id: "t-loan", amount: 1, type: .expense, accountId: "om", date: 1))
        try await space.transactions.save(Transaction(id: "t-payment", amount: 1, type: .income, accountId: "om", date: 1))
        try await space.transactions.save(Transaction(id: "t-free", amount: 1, type: .income, accountId: "om", date: 1))
        try await space.database.writer.write { db in
            try db.execute(sql: """
                INSERT INTO loans (id, personId, accountId, type, amount, amountRepaid, remainingAmount, startDate, dueDate,
                                   reason, repaymentMode, description, status, transactionId, createdAt, updatedAt)
                VALUES ('l', 'p', 'om', 'LENT', 1, 0, 1, 0, 0, 'OTHER', 'FREE', '', 'ONGOING', 't-loan', 0, 0)
                """)
            try db.execute(sql: """
                INSERT INTO loan_payments (id, loanId, accountId, amount, date, note, transactionId, createdAt, updatedAt)
                VALUES ('lp', 'l', 'om', 1, 0, '', 't-payment', 0, 0)
                """)
        }

        let loan = try await space.transactions.isLinkedToLoan(id: "t-loan")
        let repayment = try await space.transactions.isLinkedToLoan(id: "t-payment")
        let free = try await space.transactions.isLinkedToLoan(id: "t-free")
        XCTAssertTrue(loan)
        XCTAssertTrue(repayment)
        XCTAssertFalse(free)
    }
}
