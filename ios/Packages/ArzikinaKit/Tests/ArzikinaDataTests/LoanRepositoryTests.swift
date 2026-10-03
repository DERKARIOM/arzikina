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

    // MARK: - Prêt transformé en cadeau (sur Android ou le Web)

    /// Prêt de 100 000 F, 40 000 F remboursés, puis transformé en cadeau sur Android : le
    /// décaissement garde la part remboursée, la transaction « Cadeaux » porte les 60 000 F
    /// restants (Android `LoanGiftConverter`), reçus ici par synchronisation.
    private func giftedLoanFromAndroid() async throws -> Loan {
        try await setUpAccountAndPerson()
        let saved = try await space.loans.create(newLoan(amount: 100_000), firstPayment: nil)
        try await space.loans.recordPayment(LoanPayment(id: "p1", loanId: saved.id, accountId: "cash", amount: 40_000, date: now, transactionId: ""))
        try await space.database.writer.write { db in
            try db.execute(sql: "UPDATE transactions SET amount = 40000 WHERE id = ?", arguments: [saved.transactionId])
            try db.execute(sql: """
                INSERT INTO transactions (id, amount, type, accountId, categoryId, date, description, createdAt, updatedAt, version)
                VALUES ('gift', 60000, 'EXPENSE', 'cash', NULL, ?, 'Cadeau à Awa', 0, 0, 1)
                """, arguments: [saved.startDate])
            try db.execute(sql: """
                UPDATE loans SET giftedAmount = 60000, giftTransactionId = 'gift', giftedAt = ?, status = 'GIFTED', remainingAmount = 0
                WHERE id = ?
                """, arguments: [self.now, saved.id])
            try db.execute(sql: "DELETE FROM sync_queue")
        }
        let stored = try await storedLoan(saved.id)
        return try XCTUnwrap(stored)
    }

    func testAGiftedLoanIsSettledAndKeepsTheBalance() async throws {
        let loan = try await giftedLoanFromAndroid()
        XCTAssertTrue(loan.isGifted)
        XCTAssertEqual(loan.ownTransactionIds, [loan.transactionId, "gift"])

        try await space.loans.create(newLoan("other", amount: 1_000), firstPayment: nil)
        let summaries = try await first(space.loans.observeSummaries(now: now, calendar: calendar))
        let gifted = try XCTUnwrap(summaries.first { $0.loan.id == "loan" })
        XCTAssertEqual(gifted.status, .gifted)
        XCTAssertEqual(gifted.remaining, 0)
        XCTAssertEqual(LoanList.apply(summaries, filters: LoanFilters()).map(\.loan.id), ["other", "loan"], "Dette éteinte en dernier")
        XCTAssertEqual(LoanList.apply(summaries, filters: LoanFilters(status: .gifted)).map(\.loan.id), ["loan"])

        let balances = try await first(space.accounts.observeBalances())
        XCTAssertEqual(balances["cash"], 100_000 - 40_000 - 60_000 + 40_000 - 1_000, "Aucun mouvement d'argent en plus")
        let giftLinked = try await space.transactions.isLinkedToLoan(id: "gift")
        XCTAssertTrue(giftLinked, "La transaction cadeau se gère depuis le prêt")
    }

    func testAGiftedLoanRefusesPaymentsAndLockedChanges() async throws {
        let loan = try await giftedLoanFromAndroid()
        for action in [
            { try await self.space.loans.recordPayment(LoanPayment(id: "p2", loanId: loan.id, accountId: "cash", amount: 1, date: self.now, transactionId: "")) },
            { try await self.space.loans.deletePayment(id: "p1") },
            { var edited = loan; edited.amount = 120_000; try await self.space.loans.update(edited) },
            { var edited = loan; edited.accountId = "bank"; try await self.space.loans.update(edited) }
        ] as [() async throws -> Void] {
            do {
                try await action()
                XCTFail("Verrouillé")
            } catch let error as LoanWriteError {
                XCTAssertEqual(error, .loanGifted)
            }
        }
        let queued = try await space.database.writer.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM sync_queue") }
        XCTAssertEqual(queued, 0, "Rien n'a été écrit")
    }

    func testMovingAGiftedLoanMovesBothTransactionsAndKeepsTheGift() async throws {
        let loan = try await giftedLoanFromAndroid()
        var edited = loan
        edited.startDate = loan.startDate - 2 * day
        edited.description = "Moto"
        // Comme le ferait un formulaire qui renverrait des champs « cadeau » vides.
        edited.giftedAmount = 0
        edited.giftTransactionId = nil
        try await space.loans.update(edited)

        let reloaded = try await storedLoan(loan.id)
        let stored = try XCTUnwrap(reloaded)
        XCTAssertEqual(stored.giftedAmount, 60_000)
        XCTAssertEqual(stored.giftTransactionId, "gift")
        XCTAssertEqual(stored.status, .gifted)
        XCTAssertEqual(stored.remainingAmount, 0)
        XCTAssertEqual(stored.description, "Moto")
        let disbursement = try await space.transactions.transaction(id: loan.transactionId)
        let gift = try await space.transactions.transaction(id: "gift")
        XCTAssertEqual(disbursement?.date, edited.startDate)
        XCTAssertEqual(gift?.date, edited.startDate)
        XCTAssertEqual(disbursement?.amount, 40_000, "Le reclassement n'est jamais défait")
        XCTAssertEqual(gift?.amount, 60_000)
        XCTAssertEqual(gift?.description, "Cadeau à Awa")
    }

    func testDeletingAGiftedLoanDeletesItsGiftTransaction() async throws {
        let loan = try await giftedLoanFromAndroid()
        try await space.loans.delete(id: loan.id)
        let gift = try await space.transactions.transaction(id: "gift")
        XCTAssertNil(gift)
        let balances = try await first(space.accounts.observeBalances())
        XCTAssertEqual(balances["cash"], 100_000)
    }

    /// Le serveur garde l'état « cadeau » : l'iPhone ne l'envoie que s'il est renseigné, jamais
    /// pour l'effacer depuis une copie locale en retard.
    func testGiftFieldsAreNeverSentEmpty() async throws {
        let loan = try await giftedLoanFromAndroid()
        try await space.loans.create(newLoan("plain", amount: 1_000), firstPayment: nil)
        let (gifted, plain) = try await space.database.writer.read { db in
            (try SyncRowCodec.payload(db, schema: .loans, id: loan.id, operation: .update),
             try SyncRowCodec.payload(db, schema: .loans, id: "plain", operation: .create))
        }
        XCTAssertEqual(gifted?["giftedAmount"], .int(60_000))
        XCTAssertEqual(gifted?["giftTransactionSyncId"], .string("gift"))
        XCTAssertEqual(gifted?["status"], .string("GIFTED"))
        XCTAssertNil(plain?["giftedAmount"])
        XCTAssertNil(plain?["giftTransactionSyncId"])
        XCTAssertNil(plain?["giftedAt"])
        XCTAssertEqual(plain?["amount"], .int(1_000))
    }

    func testGiftFieldsAreReceivedFromTheServer() throws {
        let entity: [String: JSONValue] = [
            "id": .string("l"), "updatedAt": .int(5), "giftedAmount": .int(60_000),
            "giftTransactionSyncId": .string("gift"), "giftedAt": .int(9)
        ]
        let decoded = try XCTUnwrap(SyncRowCodec.localValues(from: entity, schema: .loans))
        XCTAssertEqual(decoded.values["giftedAmount"], 60_000.databaseValue)
        XCTAssertEqual(decoded.values["giftTransactionId"], "gift".databaseValue)
        let old = try XCTUnwrap(SyncRowCodec.localValues(from: ["id": .string("l"), "updatedAt": .int(5)], schema: .loans))
        XCTAssertEqual(old.values["giftedAmount"], 0.databaseValue, "Ancien serveur : jamais transformé")
        XCTAssertEqual(old.values["giftTransactionId"], .null)
    }

    // MARK: - Transformer en cadeau sur l'iPhone

    private func queueOrder() async throws -> [String] {
        try await space.database.writer.read { db in
            try String.fetchAll(db, sql: "SELECT entityType || ':' || operation FROM sync_queue ORDER BY id")
        }
    }

    func testConvertingAPartlyRepaidLoanKeepsTheBalanceAndGivesTheRest() async throws {
        try await setUpAccountAndPerson()
        let saved = try await space.loans.create(newLoan(amount: 100_000), firstPayment: nil)
        try await space.loans.recordPayment(LoanPayment(id: "p1", loanId: saved.id, accountId: "cash", amount: 40_000, date: now, transactionId: ""))
        let before = try await first(space.accounts.observeBalances())
        try await space.database.writer.write { db in try db.execute(sql: "DELETE FROM sync_queue") }

        let giftId = try await space.loans.convertToGift(loanId: saved.id, description: "Cadeau à Awa")

        XCTAssertNotEqual(giftId, saved.transactionId)
        let after = try await first(space.accounts.observeBalances())
        XCTAssertEqual(after, before, "Aucun mouvement d'argent")
        let disbursement = try await space.transactions.transaction(id: saved.transactionId)
        let gift = try await space.transactions.transaction(id: giftId)
        XCTAssertEqual(disbursement?.amount, 40_000)
        XCTAssertEqual(gift?.amount, 60_000)
        XCTAssertEqual(gift?.type, .expense)
        XCTAssertEqual(gift?.date, disbursement?.date)
        XCTAssertEqual(gift?.accountId, "cash")
        XCTAssertEqual(gift?.description, "Cadeau à Awa")
        let category = try await space.categories.category(id: try XCTUnwrap(gift?.categoryId))
        XCTAssertEqual(category?.systemKey, .gifts)

        let stored = try await storedLoan(saved.id)
        XCTAssertEqual(stored?.giftedAmount, 60_000)
        XCTAssertEqual(stored?.giftTransactionId, giftId)
        XCTAssertEqual(stored?.status, .gifted)
        XCTAssertEqual(stored?.remainingAmount, 0)
        XCTAssertNotNil(stored?.giftedAt)
        let order = try await queueOrder()
        // Le moteur envoie par type (catégories, puis transactions, puis prêts) : le prêt part
        // toujours après les transactions qu'il référence.
        XCTAssertEqual(Set(order), ["categories:CREATE", "transactions:CREATE", "transactions:UPDATE", "loans:UPDATE"])
        XCTAssertLessThan(SyncEntitySchema.all.firstIndex { $0.type == .transactions }!, SyncEntitySchema.all.firstIndex { $0.type == .loans }!)

        do {
            _ = try await space.loans.convertToGift(loanId: saved.id, description: "x")
            XCTFail("Déjà transformé")
        } catch let error as LoanWriteError {
            XCTAssertEqual(error, .notConvertible)
        }
    }

    func testConvertingABorrowingWithNothingRepaidReclassifiesInPlace() async throws {
        try await setUpAccountAndPerson()
        let saved = try await space.loans.create(newLoan(type: .borrowed, amount: 50_000), firstPayment: nil)
        let before = try await first(space.accounts.observeBalances())

        let giftId = try await space.loans.convertToGift(loanId: saved.id, description: "Cadeau de la part d'Awa")

        XCTAssertEqual(giftId, saved.transactionId, "Même transaction : aucune nouvelle ligne")
        let gift = try await space.transactions.transaction(id: giftId)
        XCTAssertEqual(gift?.amount, 50_000)
        XCTAssertEqual(gift?.type, .income)
        XCTAssertEqual(gift?.description, "Cadeau de la part d'Awa")
        let category = try await space.categories.category(id: try XCTUnwrap(gift?.categoryId))
        XCTAssertEqual(category?.systemKey, .giftsReceived, "« Cadeaux » en revenu, créée à la demande")
        let after = try await first(space.accounts.observeBalances())
        XCTAssertEqual(after, before)
        let transactionCount = try await space.database.writer.read { db in try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL") }
        XCTAssertEqual(transactionCount, 1)
        let summaries = try await first(space.loans.observeSummaries(now: now, calendar: calendar))
        XCTAssertEqual(summaries.first?.status, .gifted)
        XCTAssertEqual(summaries.first?.remaining, 0)
    }

    func testAFullyRepaidLoanCannotBeConverted() async throws {
        try await setUpAccountAndPerson()
        let saved = try await space.loans.create(newLoan(amount: 10_000), firstPayment: LoanPayment(id: "p", loanId: "", accountId: "cash", amount: 10_000, date: now, transactionId: ""))
        do {
            _ = try await space.loans.convertToGift(loanId: saved.id, description: "x")
            XCTFail("Remboursé")
        } catch let error as LoanWriteError {
            XCTAssertEqual(error, .notConvertible)
        }
        let stored = try await storedLoan(saved.id)
        XCTAssertEqual(stored?.giftedAmount, 0, "Rien n'est écrit")
    }
}
