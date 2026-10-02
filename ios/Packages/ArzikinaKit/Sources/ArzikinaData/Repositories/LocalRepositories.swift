import ArzikinaDomain
import Foundation
import GRDB

/// Horloge des dépôts locaux (millisecondes epoch), remplaçable dans les tests.
public typealias Clock = @Sendable () -> EpochMillis

public enum Clocks {
    public static let system: Clock = { EpochMillis(Date().timeIntervalSince1970 * 1000) }
}

/// [AccountRepository] adossé à la base locale.
public struct LocalAccountRepository: AccountRepository {

    private let database: AppDatabase
    private let store: SyncedStore<AccountRecord>
    private let transactions: SyncedStore<TransactionRecord>
    private let loanRepository: LocalLoanRepository
    private let now: Clock

    public init(database: AppDatabase, now: @escaping Clock = Clocks.system) {
        self.database = database
        self.store = SyncedStore(database: database, entityType: .accounts, now: now)
        self.transactions = SyncedStore(database: database, entityType: .transactions, now: now)
        self.loanRepository = LocalLoanRepository(database: database, now: now)
        self.now = now
    }

    public func observeAccounts() -> AsyncStream<[Account]> {
        database.observe { db in try Self.activeAccounts(db) }
    }

    public func observeBalances() -> AsyncStream<[EntityID: MinorUnits]> {
        database.observe { db in try Self.balances(db) }
    }

    /// Comptes non supprimés, dans l'ordre d'affichage.
    static func activeAccounts(_ db: Database) throws -> [Account] {
        try AccountRecord
            .filter(Column("deletedAt") == nil)
            .order(Column("displayOrder"), Column("createdAt"))
            .fetchAll(db)
            .map(\.domain)
    }

    /// Solde courant de chaque compte, calculé en SQL (une seule requête, sans charger les
    /// transactions en mémoire) avec EXACTEMENT la formule d'`AccountBalances` — vérifié par les
    /// fixtures partagées.
    static func balances(_ db: Database) throws -> [EntityID: MinorUnits] {
        let rows = try Row.fetchAll(db, sql: """
            SELECT a.id AS id,
                   a.initialBalance
                   + COALESCE((SELECT SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE -t.amount END)
                               FROM transactions t
                               WHERE t.accountId = a.id AND t.deletedAt IS NULL), 0)
                   + COALESCE((SELECT SUM(t.amount)
                               FROM transactions t
                               WHERE t.type = 'TRANSFER' AND t.transferAccountId = a.id AND t.deletedAt IS NULL), 0)
                   AS balance
            FROM accounts a
            WHERE a.deletedAt IS NULL
            """)
        var balances: [EntityID: MinorUnits] = [:]
        for row in rows {
            balances[row["id"]] = row["balance"]
        }
        return balances
    }

    public func account(id: EntityID) async throws -> Account? {
        try await store.fetchActive(id: id)?.domain
    }

    public func nextDisplayOrder() async throws -> Int64 {
        try await database.writer.read { db in
            try Int64.fetchOne(db, sql: "SELECT COALESCE(MAX(displayOrder), -1) + 1 FROM accounts WHERE deletedAt IS NULL") ?? 0
        }
    }

    public func save(_ account: Account) async throws {
        try await store.save(id: account.id, createdAt: account.createdAt) { AccountRecord(account, meta: $0) }
    }

    public func deletionImpact(id: EntityID) async throws -> AccountDeletionImpact {
        try await database.writer.read { db in
            let arguments: StatementArguments = [id, id]
            return AccountDeletionImpact(
                transactions: try Int.fetchOne(db, sql: """
                    SELECT COUNT(*) FROM transactions
                    WHERE deletedAt IS NULL AND (accountId = ? OR transferAccountId = ?)
                    """, arguments: arguments) ?? 0,
                loans: try Int.fetchOne(db, sql: "SELECT COUNT(*) FROM loans WHERE deletedAt IS NULL AND accountId = ?", arguments: [id]) ?? 0,
                automations: try Int.fetchOne(db, sql: """
                    SELECT (SELECT COUNT(*) FROM recurring_transactions WHERE deletedAt IS NULL AND accountId = ?)
                         + (SELECT COUNT(*) FROM transaction_templates WHERE deletedAt IS NULL AND accountId = ?)
                    """, arguments: arguments) ?? 0
            )
        }
    }

    /// Suppression en cascade, en UNE transaction SQL — Android `deleteAccount` :
    /// 1. les transactions du compte (source OU destination d'un transfert) disparaissent, avec
    ///    leurs frais même s'ils sont sur un autre compte ; une transaction restante dont les frais
    ///    disparaissent perd ce lien (modification ENVOYÉE au serveur, contrairement à Android) ;
    /// 2. les prêts du compte disparaissent avec leurs remboursements et toutes leurs transactions ;
    /// 3. les remboursements faits depuis ce compte pour un prêt d'un AUTRE compte disparaissent,
    ///    et ce prêt est mis à jour ;
    /// 4. le compte est supprimé.
    /// Automatisations et modèles rattachés au compte sont laissés tels quels (comme Android,
    /// voir `deletionImpact`).
    public func delete(id: EntityID) async throws {
        let timestamp = now()
        let store = self.store, transactions = self.transactions, loanRepository = self.loanRepository
        try await database.writer.write { db in
            guard let account = try AccountRecord.fetchOne(db, key: id), account.deletedAt == nil else { return }

            let disappearing = try TransactionRecord.fetchAll(db, sql: """
                SELECT * FROM transactions WHERE deletedAt IS NULL AND (accountId = ? OR transferAccountId = ?)
                """, arguments: [id, id])
            let disappearingIds = Set(disappearing.map(\.id))
            for transaction in disappearing {
                if let feeId = transaction.feeTransactionId, !disappearingIds.contains(feeId) {
                    try transactions.softDelete(db, id: feeId, timestamp: timestamp)
                }
            }
            // Transactions restantes dont les frais disparaissent : lien retiré (et synchronisé).
            if !disappearingIds.isEmpty {
                let parents = try TransactionRecord
                    .filter(disappearingIds.contains(Column("feeTransactionId")) && Column("deletedAt") == nil)
                    .fetchAll(db)
                    .filter { !disappearingIds.contains($0.id) }
                for parent in parents {
                    var updated = parent.domain
                    updated.feeTransactionId = nil
                    try transactions.save(db, id: updated.id, createdAt: updated.createdAt, timestamp: timestamp) { [updated] in TransactionRecord(updated, meta: $0) }
                }
            }

            let loanIds = try String.fetchAll(db, sql: "SELECT id FROM loans WHERE deletedAt IS NULL AND accountId = ?", arguments: [id])
            for loanId in loanIds {
                try loanRepository.delete(db, loanId: loanId, timestamp: timestamp)
            }
            let foreignPayments = try LoanPaymentRecord.filter(Column("accountId") == id && Column("deletedAt") == nil).fetchAll(db)
            for payment in foreignPayments {
                try loanRepository.deletePayment(db, paymentId: payment.id, timestamp: timestamp)
            }

            for transaction in disappearing {
                try transactions.softDelete(db, id: transaction.id, timestamp: timestamp)
            }
            try store.softDelete(db, id: id, timestamp: timestamp)
        }
    }
}

/// [CategoryRepository] adossé à la base locale.
public struct LocalCategoryRepository: CategoryRepository {

    private let database: AppDatabase
    private let store: SyncedStore<CategoryRecord>
    private let now: Clock

    public init(database: AppDatabase, now: @escaping Clock = Clocks.system) {
        self.database = database
        self.store = SyncedStore(database: database, entityType: .categories, now: now)
        self.now = now
    }

    public func observeCategories(type: TransactionType?) -> AsyncStream<[ArzikinaDomain.Category]> {
        database.observe { db in
            var request = CategoryRecord.filter(Column("deletedAt") == nil)
            if let type {
                request = request.filter(Column("type") == type.rawValue)
            }
            return try request
                .order(Column("name").collating(.localizedCaseInsensitiveCompare))
                .fetchAll(db)
                .map(\.domain)
        }
    }

    public func category(id: EntityID) async throws -> ArzikinaDomain.Category? {
        try await store.fetchActive(id: id)?.domain
    }

    public func save(_ category: ArzikinaDomain.Category) async throws {
        try await store.save(id: category.id, createdAt: category.createdAt) { CategoryRecord(category, meta: $0) }
    }

    public func delete(id: EntityID) async throws -> CategoryDeletion {
        let timestamp = now()
        let store = self.store
        return try await database.writer.write { db in
            guard let record = try CategoryRecord.fetchOne(db, key: id), record.deletedAt == nil else {
                return .deleted // déjà supprimée (ex. depuis un autre appareil)
            }
            if record.domain.systemKey?.isManagedAutomatically == true { return .managedAutomatically }
            if try Self.isInUse(db, categoryId: id) { return .inUse }
            try store.softDelete(db, id: id, timestamp: timestamp)
            return .deleted
        }
    }

    /// Tables qui référencent une catégorie (lignes non supprimées). Toutes synchronisées : une
    /// référence reçue d'un autre appareil bloque aussi la suppression.
    static let referencingTables = ["transactions", "recurring_transactions", "budgets", "transaction_templates", "financial_plan_items"]

    static func isInUse(_ db: Database, categoryId: EntityID) throws -> Bool {
        let checks = referencingTables
            .map { "EXISTS(SELECT 1 FROM \($0) WHERE categoryId = ? AND deletedAt IS NULL)" }
            .joined(separator: " OR ")
        return try Bool.fetchOne(db, sql: "SELECT \(checks)", arguments: StatementArguments(Array(repeating: categoryId, count: referencingTables.count))) ?? false
    }
}

/// [TransactionRepository] adossé à la base locale (index sur la date : listes rapides même avec
/// des dizaines de milliers de transactions).
public struct LocalTransactionRepository: TransactionRepository {

    private let database: AppDatabase
    private let store: SyncedStore<TransactionRecord>
    private let categoryStore: SyncedStore<CategoryRecord>
    private let now: Clock

    public init(database: AppDatabase, now: @escaping Clock = Clocks.system) {
        self.database = database
        self.now = now
        self.store = SyncedStore(database: database, entityType: .transactions, now: now)
        self.categoryStore = SyncedStore(database: database, entityType: .categories, now: now)
    }

    public func observeTransactions(from: EpochMillis?, to: EpochMillis?) -> AsyncStream<[Transaction]> {
        database.observe { db in
            var request = TransactionRecord.filter(Column("deletedAt") == nil)
            if let from { request = request.filter(Column("date") >= from) }
            if let to { request = request.filter(Column("date") < to) }
            return try request
                .order(Column("date").desc, Column("createdAt").desc, Column("id").desc)
                .fetchAll(db)
                .map(\.domain)
        }
    }

    public func observeRecentTransactions(limit: Int) -> AsyncStream<[Transaction]> {
        database.observe { db in
            try TransactionRecord
                .filter(Column("deletedAt") == nil)
                .order(Column("date").desc, Column("createdAt").desc, Column("id").desc)
                .limit(max(limit, 0))
                .fetchAll(db)
                .map(\.domain)
        }
    }

    public func transaction(id: EntityID) async throws -> Transaction? {
        try await store.fetchActive(id: id)?.domain
    }

    public func save(_ transaction: Transaction) async throws {
        try await store.save(id: transaction.id, createdAt: transaction.createdAt) { TransactionRecord(transaction, meta: $0) }
    }

    /// Android `TransactionRepositoryImpl.saveTransaction` : les frais deviennent une transaction
    /// de dépense liée (catégorie « Frais et commissions », créée si besoin), mise à jour EN PLACE
    /// d'une modification à l'autre ; des frais retirés suppriment cette transaction. Le tout dans
    /// UNE transaction SQL : jamais de transaction principale sans ses frais, ni de frais orphelins.
    public func save(_ transaction: Transaction, fee: TransactionFee?) async throws {
        let timestamp = now()
        let store = self.store
        let categoryStore = self.categoryStore
        try await database.writer.write { db in
            let existing = try TransactionRecord.fetchOne(db, key: transaction.id)
            let previousFeeId = existing?.feeTransactionId

            var feeTransactionId: EntityID?
            if let fee {
                // Réutilise la transaction de frais existante si elle est toujours active.
                let previousFee = try previousFeeId.flatMap { try TransactionRecord.fetchOne(db, key: $0) }
                let feeId = previousFee?.deletedAt == nil ? (previousFee?.id ?? EntityIDs.generate()) : EntityIDs.generate()
                let feeTransaction = Transaction(
                    id: feeId,
                    amount: fee.amount,
                    type: .expense,
                    accountId: fee.accountId,
                    categoryId: try Self.systemCategoryId(.fees, db, store: categoryStore, timestamp: timestamp),
                    date: transaction.date,
                    description: fee.description,
                    feeType: fee.type,
                    createdAt: previousFee?.createdAt ?? timestamp
                )
                try store.save(db, id: feeId, createdAt: feeTransaction.createdAt, timestamp: timestamp) {
                    TransactionRecord(feeTransaction, meta: $0)
                }
                if let previousFeeId, previousFeeId != feeId {
                    try store.softDelete(db, id: previousFeeId, timestamp: timestamp)
                }
                feeTransactionId = feeId
            } else if let previousFeeId {
                try store.softDelete(db, id: previousFeeId, timestamp: timestamp)
            }

            var main = transaction
            main.feeTransactionId = feeTransactionId
            main.feeType = nil
            try store.save(db, id: main.id, createdAt: main.createdAt, timestamp: timestamp) { TransactionRecord(main, meta: $0) }
        }
    }

    /// Supprime la transaction ET sa transaction de frais (Android `deleteTransaction`).
    public func delete(id: EntityID) async throws {
        let timestamp = now()
        let store = self.store
        try await database.writer.write { db in
            try Self.deleteWithFee(db, id: id, store: store, timestamp: timestamp)
        }
    }

    /// Supprime, dans la transaction SQL de l'appelant, la transaction [id] et ses frais.
    static func deleteWithFee(_ db: Database, id: EntityID, store: SyncedStore<TransactionRecord>, timestamp: Int64) throws {
        guard let existing = try TransactionRecord.fetchOne(db, key: id), existing.deletedAt == nil else { return }
        if let feeId = existing.feeTransactionId {
            try store.softDelete(db, id: feeId, timestamp: timestamp)
        }
        try store.softDelete(db, id: id, timestamp: timestamp)
    }

    public func isLinkedToLoan(id: EntityID) async throws -> Bool {
        try await database.writer.read { db in
            try Bool.fetchOne(db, sql: """
                SELECT EXISTS (SELECT 1 FROM loans WHERE transactionId = ? AND deletedAt IS NULL)
                    OR EXISTS (SELECT 1 FROM loans WHERE giftTransactionId = ? AND deletedAt IS NULL)
                    OR EXISTS (SELECT 1 FROM loan_payments WHERE transactionId = ? AND deletedAt IS NULL)
                """, arguments: [id, id, id]) ?? false
        }
    }

    /// Catégorie système [key] (« Frais et commissions », catégories des prêts…), retrouvée par son
    /// nom de référence et son type ; recréée si elle n'existe pas, comme Android
    /// `SystemCategoryResolver`.
    static func systemCategoryId(_ key: SystemCategoryKey, _ db: Database, store: SyncedStore<CategoryRecord>, timestamp: Int64) throws -> EntityID {
        if let id = try String.fetchOne(db, sql: """
            SELECT id FROM categories
            WHERE name = ? AND type = ? AND deletedAt IS NULL
            ORDER BY createdAt, id LIMIT 1
            """, arguments: [key.canonicalName, key.type.rawValue]) {
            return id
        }
        let category = ArzikinaDomain.Category(
            id: EntityIDs.generate(),
            name: key.canonicalName,
            icon: key.defaultIcon,
            colorArgb: key.defaultColorArgb,
            type: key.type,
            createdAt: timestamp
        )
        try store.save(db, id: category.id, createdAt: timestamp, timestamp: timestamp) { CategoryRecord(category, meta: $0) }
        return category.id
    }
}
