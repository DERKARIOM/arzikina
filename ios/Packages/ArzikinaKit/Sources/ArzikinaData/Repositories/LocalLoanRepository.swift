import ArzikinaDomain
import Foundation
import GRDB

/// [LoanRepository] adossé à la base locale.
///
/// Le montant remboursé est TOUJOURS recalculé à partir des remboursements non supprimés (jamais
/// lu dans `loans.amountRepaid`) : si deux appareils enregistrent chacun un remboursement, la
/// valeur stockée de l'un écraserait celle de l'autre, alors que la somme des remboursements reste
/// juste une fois tout synchronisé.
public struct LocalLoanRepository: LoanRepository {

    private let database: AppDatabase
    private let now: Clock
    private let persons: SyncedStore<PersonRecord>
    private let loans: SyncedStore<LoanRecord>
    private let payments: SyncedStore<LoanPaymentRecord>
    private let transactions: SyncedStore<TransactionRecord>
    private let categories: SyncedStore<CategoryRecord>

    public init(database: AppDatabase, now: @escaping Clock = Clocks.system) {
        self.database = database
        self.now = now
        persons = SyncedStore(database: database, entityType: .persons, now: now)
        loans = SyncedStore(database: database, entityType: .loans, now: now)
        payments = SyncedStore(database: database, entityType: .loanPayments, now: now)
        transactions = SyncedStore(database: database, entityType: .transactions, now: now)
        categories = SyncedStore(database: database, entityType: .categories, now: now)
    }

    // MARK: - Personnes

    public func observePersons() -> AsyncStream<[Person]> {
        database.observe { db in
            try PersonRecord
                .filter(Column("deletedAt") == nil)
                .order(Column("name").collating(.localizedCaseInsensitiveCompare))
                .fetchAll(db)
                .map(\.domain)
        }
    }

    public func savePerson(_ person: Person) async throws {
        try await persons.save(id: person.id, createdAt: person.createdAt) { PersonRecord(person, meta: $0) }
    }

    // MARK: - Lecture

    public func observeSummaries(now: EpochMillis, calendar: Calendar) -> AsyncStream<[LoanSummary]> {
        database.observe { db in
            try Self.summaries(db, now: now, calendar: calendar, loanId: nil)
        }
    }

    public func observeDetail(id: EntityID, now: EpochMillis, calendar: Calendar) -> AsyncStream<LoanDetail?> {
        database.observe { db in
            guard let summary = try Self.summaries(db, now: now, calendar: calendar, loanId: id).first else { return nil }
            let account = try AccountRecord.fetchOne(db, key: summary.loan.accountId).flatMap { $0.deletedAt == nil ? $0.domain : nil }
            let payments = try LoanPaymentRecord
                .filter(Column("loanId") == id && Column("deletedAt") == nil)
                .order(Column("date").desc, Column("createdAt").desc, Column("id").desc)
                .fetchAll(db)
                .map(\.domain)
            return LoanDetail(summary: summary, account: account, payments: payments)
        }
    }

    static func summaries(_ db: Database, now: EpochMillis, calendar: Calendar, loanId: EntityID?) throws -> [LoanSummary] {
        var request = LoanRecord.filter(Column("deletedAt") == nil)
        if let loanId { request = request.filter(Column("id") == loanId) }
        let records = try request.order(Column("startDate").desc, Column("createdAt").desc, Column("id").desc).fetchAll(db)
        guard !records.isEmpty else { return [] }

        let repaid = try repaidAmounts(db)
        let personsById = Dictionary(uniqueKeysWithValues: try PersonRecord.filter(Column("deletedAt") == nil).fetchAll(db).map { ($0.id, $0.domain) })
        // Devise du compte du prêt, même supprimé (le prêt reste dans sa devise d'origine).
        let currencies = Dictionary(uniqueKeysWithValues: try Row.fetchAll(db, sql: "SELECT id, currencyCode FROM accounts").map { (row: Row) -> (String, String) in (row["id"], row["currencyCode"]) })
        return records.map { record in
            LoanSummary(
                loan: record.domain,
                person: personsById[record.personId],
                currencyCode: currencies[record.accountId] ?? SupportedCurrency.defaultCode,
                amountRepaid: repaid[record.id] ?? 0,
                now: now,
                calendar: calendar
            )
        }
    }

    /// Somme des remboursements non supprimés, par prêt (index sur `loanId`).
    static func repaidAmounts(_ db: Database) throws -> [EntityID: MinorUnits] {
        let rows = try Row.fetchAll(db, sql: """
            SELECT loanId, SUM(amount) AS total FROM loan_payments WHERE deletedAt IS NULL GROUP BY loanId
            """)
        return Dictionary(uniqueKeysWithValues: rows.map { (row: Row) -> (EntityID, MinorUnits) in (row["loanId"], row["total"]) })
    }

    // MARK: - Écriture

    public func create(_ loan: Loan, firstPayment: LoanPayment?) async throws -> Loan {
        let timestamp = now()
        let store = self
        return try await database.writer.write { db in
            let categoryId = try LocalTransactionRepository.systemCategoryId(loan.type.disbursementCategory, db, store: store.categories, timestamp: timestamp)
            let disbursement = Self.disbursement(of: loan, id: EntityIDs.generate(), categoryId: categoryId, createdAt: timestamp)
            try store.transactions.save(db, id: disbursement.id, createdAt: timestamp, timestamp: timestamp) { TransactionRecord(disbursement, meta: $0) }

            var saved = loan
            saved.transactionId = disbursement.id
            saved.createdAt = timestamp
            try store.saveLoan(db, saved, timestamp: timestamp)
            if var payment = firstPayment {
                payment.loanId = saved.id
                try store.insertPayment(db, payment, loan: saved, timestamp: timestamp)
            }
            return try store.refreshStoredProgress(db, loanId: saved.id, timestamp: timestamp)
        }
    }

    public func update(_ loan: Loan) async throws {
        let timestamp = now()
        let store = self
        try await database.writer.write { db in
            guard let existing = try LoanRecord.fetchOne(db, key: loan.id), existing.deletedAt == nil else { throw LoanWriteError.loanNotFound }
            guard loan.amount >= (try Self.repaid(db, loanId: loan.id)) else { throw LoanWriteError.amountBelowRepaid }
            var saved = loan
            saved.type = existing.domain.type
            saved.transactionId = existing.transactionId
            saved.createdAt = existing.createdAt
            // Transaction de décaissement alignée sur le prêt (montant, compte, date, description),
            // mise à jour EN PLACE ; recréée si elle a disparu.
            let categoryId = try LocalTransactionRepository.systemCategoryId(saved.type.disbursementCategory, db, store: store.categories, timestamp: timestamp)
            let current = try TransactionRecord.fetchOne(db, key: existing.transactionId)
            var disbursement = Self.disbursement(of: saved, id: existing.transactionId, categoryId: categoryId, createdAt: current?.createdAt ?? timestamp)
            if let current {
                let domain = current.domain
                disbursement.latitude = domain.latitude
                disbursement.longitude = domain.longitude
                disbursement.paymentMethod = domain.paymentMethod
            }
            try store.transactions.save(db, id: disbursement.id, createdAt: disbursement.createdAt, timestamp: timestamp) { [disbursement] in TransactionRecord(disbursement, meta: $0) }
            try store.saveLoan(db, saved, timestamp: timestamp)
            _ = try store.refreshStoredProgress(db, loanId: saved.id, timestamp: timestamp)
        }
    }

    public func delete(id: EntityID) async throws {
        let timestamp = now()
        let store = self
        try await database.writer.write { db in
            try store.delete(db, loanId: id, timestamp: timestamp)
        }
    }

    /// Suppression en cascade dans une transaction SQL ouverte par l'appelant (aussi utilisée par
    /// la suppression d'un compte).
    func delete(_ db: Database, loanId: EntityID, timestamp: Int64) throws {
        guard let loan = try LoanRecord.fetchOne(db, key: loanId), loan.deletedAt == nil else { return }
        let loanPayments = try LoanPaymentRecord.filter(Column("loanId") == loanId && Column("deletedAt") == nil).fetchAll(db)
        for payment in loanPayments {
            try transactions.softDelete(db, id: payment.transactionId, timestamp: timestamp)
            try payments.softDelete(db, id: payment.id, timestamp: timestamp)
        }
        try transactions.softDelete(db, id: loan.transactionId, timestamp: timestamp)
        try loans.softDelete(db, id: loanId, timestamp: timestamp)
    }

    public func recordPayment(_ payment: LoanPayment) async throws {
        let timestamp = now()
        let store = self
        try await database.writer.write { db in
            guard let record = try LoanRecord.fetchOne(db, key: payment.loanId), record.deletedAt == nil else { throw LoanWriteError.loanNotFound }
            let loan = record.domain
            // Revérifié ICI : un remboursement a pu arriver par synchronisation depuis la saisie.
            guard payment.amount <= loan.amount - (try Self.repaid(db, loanId: loan.id)) else { throw LoanWriteError.amountExceedsRemaining }
            try store.insertPayment(db, payment, loan: loan, timestamp: timestamp)
            _ = try store.refreshStoredProgress(db, loanId: loan.id, timestamp: timestamp)
        }
    }

    public func deletePayment(id: EntityID) async throws {
        let timestamp = now()
        let store = self
        try await database.writer.write { db in
            try store.deletePayment(db, paymentId: id, timestamp: timestamp)
        }
    }

    /// Supprime un remboursement et sa transaction, puis met à jour son prêt (transaction SQL
    /// ouverte par l'appelant).
    func deletePayment(_ db: Database, paymentId: EntityID, timestamp: Int64) throws {
        guard let payment = try LoanPaymentRecord.fetchOne(db, key: paymentId), payment.deletedAt == nil else { return }
        try transactions.softDelete(db, id: payment.transactionId, timestamp: timestamp)
        try payments.softDelete(db, id: paymentId, timestamp: timestamp)
        if let loan = try LoanRecord.fetchOne(db, key: payment.loanId), loan.deletedAt == nil {
            _ = try refreshStoredProgress(db, loanId: payment.loanId, timestamp: timestamp)
        }
    }

    // MARK: - Interne

    /// Transaction de décaissement d'un prêt : la description du prêt, sinon le nom de la
    /// catégorie, comme Android.
    private static func disbursement(of loan: Loan, id: EntityID, categoryId: EntityID, createdAt: EpochMillis) -> Transaction {
        Transaction(
            id: id,
            amount: loan.amount,
            type: loan.type.disbursementTransactionType,
            accountId: loan.accountId,
            categoryId: categoryId,
            date: loan.startDate,
            description: loan.description.isEmpty ? loan.type.disbursementCategory.canonicalName : loan.description,
            createdAt: createdAt
        )
    }

    private func saveLoan(_ db: Database, _ loan: Loan, timestamp: Int64) throws {
        try loans.save(db, id: loan.id, createdAt: loan.createdAt, timestamp: timestamp) { LoanRecord(loan, meta: $0) }
    }

    /// Remboursement + sa transaction (sens inverse du décaissement, catégorie de remboursement).
    private func insertPayment(_ db: Database, _ payment: LoanPayment, loan: Loan, timestamp: Int64) throws {
        let categoryId = try LocalTransactionRepository.systemCategoryId(loan.type.repaymentCategory, db, store: categories, timestamp: timestamp)
        let transaction = Transaction(
            id: EntityIDs.generate(),
            amount: payment.amount,
            type: loan.type.repaymentTransactionType,
            accountId: payment.accountId,
            categoryId: categoryId,
            date: payment.date,
            description: payment.note.isEmpty ? loan.type.repaymentCategory.canonicalName : payment.note,
            createdAt: timestamp
        )
        try transactions.save(db, id: transaction.id, createdAt: timestamp, timestamp: timestamp) { TransactionRecord(transaction, meta: $0) }
        var saved = payment
        saved.transactionId = transaction.id
        saved.createdAt = timestamp
        try payments.save(db, id: saved.id, createdAt: timestamp, timestamp: timestamp) { [saved] in LoanPaymentRecord(saved, meta: $0) }
    }

    /// Recopie dans la ligne `loans` le remboursé, le reste et le statut DÉDUITS des
    /// remboursements : Android et le Web, qui lisent ces champs, affichent ainsi les bonnes valeurs.
    private func refreshStoredProgress(_ db: Database, loanId: EntityID, timestamp: Int64) throws -> Loan {
        guard var loan = try LoanRecord.fetchOne(db, key: loanId)?.domain else { throw LoanWriteError.loanNotFound }
        let repaid = try Self.repaid(db, loanId: loanId)
        let status = LoanStatusRule.status(amount: loan.amount, amountRepaid: repaid, startDate: loan.startDate, dueDate: loan.dueDate, now: timestamp, calendar: ArzikinaCalendar.current)
        guard loan.amountRepaid != repaid || loan.remainingAmount != loan.amount - repaid || loan.status != status else { return loan }
        loan.amountRepaid = repaid
        loan.remainingAmount = loan.amount - repaid
        loan.status = status
        try saveLoan(db, loan, timestamp: timestamp)
        return loan
    }

    static func repaid(_ db: Database, loanId: EntityID) throws -> MinorUnits {
        try MinorUnits.fetchOne(db, sql: "SELECT COALESCE(SUM(amount), 0) FROM loan_payments WHERE loanId = ? AND deletedAt IS NULL", arguments: [loanId]) ?? 0
    }
}
