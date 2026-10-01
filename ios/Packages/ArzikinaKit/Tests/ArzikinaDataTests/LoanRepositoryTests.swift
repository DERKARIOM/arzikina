import ArzikinaDomain
import GRDB
import XCTest
@testable import ArzikinaData

final class LoanRepositoryTests: XCTestCase {

    private var space: UserDataSpace!
    private let calendar = ArzikinaCalendar.make(timeZone: TimeZone(identifier: "Africa/Niamey")!)
    private let day: EpochMillis = 24 * 60 * 60 * 1000
    private let now: EpochMillis = 1_790_000_000_000

    override func setUpWithError() throws {
        space = try UserDataSpace.inMemory()
    }

    override func tearDown() {
        space.close()
    }

    private func newLoan(_ id: String = "loan", type: LoanType = .lent, amount: MinorUnits = 50_000, description: String = "") -> Loan {
        Loan(id: id, personId: "awa", accountId: "cash", type: type, amount: amount, remainingAmount: amount, startDate: now - day, dueDate: now + 30 * day, description: description, transactionId: "")
    }

    private func setUpAccountAndPerson() async throws {
        try await space.accounts.save(Account(id: "cash", name: "Espèces", initialBalance: 100_000))
        try await space.loans.savePerson(Person(id: "awa", name: "Awa", phone: "+227 90 00 00 00"))
    }

    private func first<T>(_ stream: AsyncStream<T>) async throws -> T {
        var iterator = stream.makeAsyncIterator()
        let value = await iterator.next()
        return try XCTUnwrap(value)
    }

    func testCreatingALoanCreatesItsDisbursementInTheSystemCategory() async throws {
        try await setUpAccountAndPerson()
        let saved = try await space.loans.create(newLoan(), firstPayment: nil)

        let transaction = try await space.transactions.transaction(id: saved.transactionId)
        XCTAssertEqual(transaction?.type, .expense, "Prêt accordé : l'argent sort")
        XCTAssertEqual(transaction?.amount, 50_000)
        XCTAssertEqual(transaction?.description, "Prêt accordé")
        let category = try await space.categories.category(id: try XCTUnwrap(transaction?.categoryId))
        XCTAssertEqual(category?.systemKey, .loanDisbursementLent, "Catégorie système recréée si absente")
        let linked = try await space.transactions.isLinkedToLoan(id: saved.transactionId)
        XCTAssertTrue(linked, "Le formulaire de transaction la passe en lecture seule")

        let balances = try await first(space.accounts.observeBalances())
        XCTAssertEqual(balances["cash"], 50_000)

        let queued = try await space.database.writer.read { db in
            try String.fetchAll(db, sql: "SELECT entityType FROM sync_queue ORDER BY id")
        }
        XCTAssertEqual(Set(queued), ["accounts", "persons", "categories", "transactions", "loans"])
    }

    func testBorrowingIsAnIncomeAndReusesAnExistingCategory() async throws {
        try await setUpAccountAndPerson()
        try await space.categories.save(ArzikinaDomain.Category(id: "existing", name: "Emprunt reçu", type: .income))
        let saved = try await space.loans.create(newLoan(type: .borrowed, description: "Loyer"), firstPayment: nil)
        let transaction = try await space.transactions.transaction(id: saved.transactionId)
        XCTAssertEqual(transaction?.type, .income)
        XCTAssertEqual(transaction?.categoryId, "existing")
        XCTAssertEqual(transaction?.description, "Loyer")
    }

    func testSummaryAddsUpPaymentsReceivedBySync() async throws {
        try await setUpAccountAndPerson()
        let saved = try await space.loans.create(newLoan(), firstPayment: nil)
        // Remboursements reçus d'Android (la ligne `loans` peut, elle, être en retard).
        try await space.database.writer.write { db in
            for (id, amount, deleted) in [("p1", 10_000, false), ("p2", 5_000, false), ("p3", 7_000, true)] {
                try db.execute(sql: """
                    INSERT INTO loan_payments (id, loanId, accountId, amount, date, note, transactionId, createdAt, updatedAt, deletedAt, version)
                    VALUES (?, ?, 'cash', ?, ?, '', ?, 0, 0, ?, 1)
                    """, arguments: [id, saved.id, amount, self.now, "t-\(id)", deleted ? 1 : nil])
            }
        }
        let summaries = try await first(space.loans.observeSummaries(now: now, calendar: calendar))
        let summary = try XCTUnwrap(summaries.first)
        XCTAssertEqual(summary.amountRepaid, 15_000, "Remboursement supprimé ignoré")
        XCTAssertEqual(summary.remaining, 35_000)
        XCTAssertEqual(summary.person?.name, "Awa")
        XCTAssertEqual(summary.currencyCode, "XOF")

        let detail = try await first(space.loans.observeDetail(id: saved.id, now: now, calendar: calendar))
        XCTAssertEqual(detail?.payments.map(\.id), ["p2", "p1"], "Remboursements actifs, même date : départagés par identifiant")
        XCTAssertEqual(detail?.account?.id, "cash")
    }

    func testDeletingALoanDeletesPaymentsAndAllTheirTransactions() async throws {
        try await setUpAccountAndPerson()
        let saved = try await space.loans.create(newLoan(), firstPayment: nil)
        try await space.transactions.save(Transaction(id: "repay", amount: 20_000, type: .income, accountId: "cash", date: now))
        try await space.database.writer.write { db in
            try db.execute(sql: """
                INSERT INTO loan_payments (id, loanId, accountId, amount, date, note, transactionId, createdAt, updatedAt, version)
                VALUES ('p1', ?, 'cash', 20000, ?, '', 'repay', 0, 0, 1)
                """, arguments: [saved.id, self.now])
        }

        try await space.loans.delete(id: saved.id)

        let summaries = try await first(space.loans.observeSummaries(now: now, calendar: calendar))
        XCTAssertTrue(summaries.isEmpty)
        let detail = try await first(space.loans.observeDetail(id: saved.id, now: now, calendar: calendar))
        XCTAssertNil(detail)
        let disbursement = try await space.transactions.transaction(id: saved.transactionId)
        let repayment = try await space.transactions.transaction(id: "repay")
        XCTAssertNil(disbursement)
        XCTAssertNil(repayment)
        let balances = try await first(space.accounts.observeBalances())
        XCTAssertEqual(balances["cash"], 100_000, "Le solde revient à son état d'avant le prêt")
        let deletedPayment = try await space.database.writer.read { db in
            try Int64.fetchOne(db, sql: "SELECT deletedAt FROM loan_payments WHERE id = 'p1'")
        }
        XCTAssertNotNil(deletedPayment)
    }

    private func storedLoan(_ id: String) async throws -> Loan? {
        try await space.database.writer.read { db in try LoanRecord.fetchOne(db, key: id)?.domain }
    }

    func testPaymentsCreateTheirTransactionAndKeepStoredProgressInStep() async throws {
        try await setUpAccountAndPerson()
        let saved = try await space.loans.create(newLoan(), firstPayment: nil)
        try await space.loans.recordPayment(LoanPayment(id: "p1", loanId: saved.id, accountId: "cash", amount: 20_000, date: now, note: "", transactionId: ""))

        var stored = try await storedLoan(saved.id)
        XCTAssertEqual(stored?.amountRepaid, 20_000, "Champs stockés à jour pour Android et le Web")
        XCTAssertEqual(stored?.remainingAmount, 30_000)
        let detail = try await first(space.loans.observeDetail(id: saved.id, now: now, calendar: calendar))
        let payment = try XCTUnwrap(detail?.payments.first)
        let transaction = try await space.transactions.transaction(id: payment.transactionId)
        XCTAssertEqual(transaction?.type, .income, "Remboursement d'un prêt accordé : l'argent revient")
        XCTAssertEqual(transaction?.description, "Remboursement de prêt reçu")
        let linked = try await space.transactions.isLinkedToLoan(id: payment.transactionId)
        XCTAssertTrue(linked)

        do {
            try await space.loans.recordPayment(LoanPayment(id: "p2", loanId: saved.id, accountId: "cash", amount: 30_001, date: now, transactionId: ""))
            XCTFail("Au-delà du reste")
        } catch let error as LoanWriteError {
            XCTAssertEqual(error, .amountExceedsRemaining)
        }

        try await space.loans.recordPayment(LoanPayment(id: "p3", loanId: saved.id, accountId: "cash", amount: 30_000, date: now, transactionId: ""))
        stored = try await storedLoan(saved.id)
        XCTAssertEqual(stored?.status, .repaid)

        try await space.loans.deletePayment(id: "p3")
        stored = try await storedLoan(saved.id)
        XCTAssertEqual(stored?.amountRepaid, 20_000)
        XCTAssertNotEqual(stored?.status, .repaid)
        let balances = try await first(space.accounts.observeBalances())
        XCTAssertEqual(balances["cash"], 100_000 - 50_000 + 20_000)
    }

    func testCreatingWithAFirstPaymentWritesEverythingTogether() async throws {
        try await setUpAccountAndPerson()
        let first = LoanPayment(id: "first", loanId: "", accountId: "cash", amount: 5_000, date: now, transactionId: "")
        let saved = try await space.loans.create(newLoan(type: .borrowed), firstPayment: first)
        XCTAssertEqual(saved.amountRepaid, 5_000)
        let detail = try await self.first(space.loans.observeDetail(id: saved.id, now: now, calendar: calendar))
        XCTAssertEqual(detail?.payments.map(\.id), ["first"])
        let repayment = try await space.transactions.transaction(id: try XCTUnwrap(detail?.payments.first?.transactionId))
        XCTAssertEqual(repayment?.type, .expense, "Remboursement d'un emprunt : l'argent sort")
    }

    func testUpdatingALoanUpdatesItsDisbursementAndKeepsItsType() async throws {
        try await setUpAccountAndPerson()
        try await space.accounts.save(Account(id: "bank", name: "Banque"))
        let saved = try await space.loans.create(newLoan(), firstPayment: nil)
        try await space.loans.recordPayment(LoanPayment(id: "p1", loanId: saved.id, accountId: "cash", amount: 10_000, date: now, transactionId: ""))

        var edited = saved
        edited.type = .borrowed
        edited.amount = 8_000
        do {
            try await space.loans.update(edited)
            XCTFail("Sous le remboursé")
        } catch let error as LoanWriteError {
            XCTAssertEqual(error, .amountBelowRepaid)
        }

        edited.amount = 60_000
        edited.accountId = "bank"
        edited.description = "Moto"
        try await space.loans.update(edited)

        let stored = try await storedLoan(saved.id)
        XCTAssertEqual(stored?.type, .lent, "Le type n'est jamais modifié")
        XCTAssertEqual(stored?.remainingAmount, 50_000)
        let disbursement = try await space.transactions.transaction(id: saved.transactionId)
        XCTAssertEqual(disbursement?.amount, 60_000)
        XCTAssertEqual(disbursement?.accountId, "bank")
        XCTAssertEqual(disbursement?.type, .expense)
        XCTAssertEqual(disbursement?.description, "Moto")
    }
}
