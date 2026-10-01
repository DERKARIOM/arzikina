import ArzikinaDomain
import GRDB
import XCTest
@testable import ArzikinaData

/// Suppression d'un compte en cascade — comportements d'Android `AccountRepositoryImpl.deleteAccount`.
final class AccountDeletionTests: XCTestCase {

    private var space: UserDataSpace!
    private let now: EpochMillis = 1_790_000_000_000
    private let day: EpochMillis = 24 * 60 * 60 * 1000

    override func setUpWithError() throws {
        space = try UserDataSpace.inMemory()
    }

    override func tearDown() {
        space.close()
    }

    private func isDeleted(_ table: String, _ id: String) async throws -> Bool {
        try await space.database.writer.read { db in
            try Int64.fetchOne(db, sql: "SELECT deletedAt FROM \(table) WHERE id = ?", arguments: [id]) != nil
        }
    }

    private func balances() async throws -> [EntityID: MinorUnits] {
        var iterator = space.accounts.observeBalances().makeAsyncIterator()
        let value = await iterator.next()
        return try XCTUnwrap(value)
    }

    func testDeletingAnAccountRemovesEverythingThatDependsOnIt() async throws {
        try await space.accounts.save(Account(id: "old", name: "Ancien", initialBalance: 50_000))
        try await space.accounts.save(Account(id: "main", name: "Principal", initialBalance: 100_000))
        try await space.loans.savePerson(Person(id: "awa", name: "Awa"))

        // Transfert vers le compte supprimé, avec des frais payés depuis le compte principal.
        try await space.transactions.save(
            Transaction(id: "toOld", amount: 10_000, type: .transfer, accountId: "main", transferAccountId: "old", date: now),
            fee: TransactionFee(amount: 300, accountId: "main", type: .transfer)
        )
        // Dépense du compte principal dont les frais sont payés depuis le compte supprimé.
        try await space.transactions.save(
            Transaction(id: "survivor", amount: 2_000, type: .expense, accountId: "main", date: now),
            fee: TransactionFee(amount: 100, accountId: "old", type: .bank)
        )
        let survivorBefore = try await space.transactions.transaction(id: "survivor")
        let survivorFeeId = try XCTUnwrap(survivorBefore?.feeTransactionId)
        // Prêt du compte supprimé, et remboursement fait depuis ce compte pour un prêt du principal.
        let oldLoan = try await space.loans.create(Loan(id: "oldLoan", personId: "awa", accountId: "old", type: .lent, amount: 5_000, remainingAmount: 5_000, startDate: now - day, dueDate: now + day, transactionId: ""), firstPayment: nil)
        let mainLoan = try await space.loans.create(Loan(id: "mainLoan", personId: "awa", accountId: "main", type: .borrowed, amount: 8_000, remainingAmount: 8_000, startDate: now - day, dueDate: now + day, transactionId: ""), firstPayment: nil)
        try await space.loans.recordPayment(LoanPayment(id: "fromOld", loanId: mainLoan.id, accountId: "old", amount: 3_000, date: now, transactionId: ""))

        let impact = try await space.accounts.deletionImpact(id: "old")
        XCTAssertEqual(impact.loans, 1)
        XCTAssertEqual(impact.transactions, 4, "Transfert reçu, frais, décaissement du prêt, remboursement")

        try await space.accounts.delete(id: "old")

        let transferDeleted = try await isDeleted("transactions", "toOld")
        XCTAssertTrue(transferDeleted, "Transfert vers le compte : supprimé")
        let transferFee = try await space.database.writer.read { db in try String.fetchOne(db, sql: "SELECT feeTransactionId FROM transactions WHERE id = 'toOld'") }
        let transferFeeId = try XCTUnwrap(transferFee)
        let transferFeeDeleted = try await isDeleted("transactions", transferFeeId)
        XCTAssertTrue(transferFeeDeleted, "Ses frais, sur un autre compte, aussi")
        let survivor = try await space.transactions.transaction(id: "survivor")
        XCTAssertNotNil(survivor, "La dépense du compte principal reste")
        XCTAssertNil(survivor?.feeTransactionId, "Mais perd le lien vers ses frais supprimés")
        let survivorFeeDeleted = try await isDeleted("transactions", survivorFeeId)
        XCTAssertTrue(survivorFeeDeleted)

        let oldLoanDeleted = try await isDeleted("loans", oldLoan.id)
        XCTAssertTrue(oldLoanDeleted)
        let paymentDeleted = try await isDeleted("loan_payments", "fromOld")
        XCTAssertTrue(paymentDeleted)
        let mainLoanStored = try await space.database.writer.read { db in try LoanRecord.fetchOne(db, key: mainLoan.id)?.domain }
        XCTAssertEqual(mainLoanStored?.amountRepaid, 0, "Le prêt du compte principal est mis à jour")

        let accountDeleted = try await isDeleted("accounts", "old")
        XCTAssertTrue(accountDeleted)
        let after = try await balances()
        XCTAssertNil(after["old"])
        XCTAssertEqual(after["main"], 100_000 + 8_000 - 2_000, "Restent l'emprunt reçu et la dépense du compte principal ; ses frais (sur le compte supprimé) ont disparu")

        // Tout ce qui a changé part au serveur, y compris le lien de frais retiré.
        let queued = try await space.database.writer.read { db in
            try Row.fetchAll(db, sql: "SELECT entityType, entityId, operation FROM sync_queue")
        }
        let survivorUpdate = queued.first { ($0["entityId"] as String?) == "survivor" }
        XCTAssertNotNil(survivorUpdate)
    }

    func testImpactCountsAutomationsThatWillStopWorking() async throws {
        try await space.accounts.save(Account(id: "a", name: "A"))
        try await space.database.writer.write { db in
            try db.execute(sql: """
                INSERT INTO recurring_transactions (id, type, amount, accountId, startDate, frequency, nextExecutionDate, triggerHour, triggerMinute, createdAt, updatedAt)
                VALUES ('r', 'EXPENSE', 1, 'a', 0, 'MONTHLY', 0, 8, 0, 0, 0)
                """)
        }
        let impact = try await space.accounts.deletionImpact(id: "a")
        XCTAssertEqual(impact, AccountDeletionImpact(transactions: 0, loans: 0, automations: 1))
        try await space.accounts.delete(id: "a")
        let recurringDeleted = try await isDeleted("recurring_transactions", "r")
        XCTAssertFalse(recurringDeleted, "Laissée telle quelle, comme Android")
    }
}
