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

    public init(database: AppDatabase, now: @escaping Clock = Clocks.system) {
        self.database = database
        self.store = SyncedStore(database: database, entityType: .accounts, now: now)
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

    public func save(_ account: Account) async throws {
        try await store.save(id: account.id, createdAt: account.createdAt) { AccountRecord(account, meta: $0) }
    }

    public func delete(id: EntityID) async throws {
        try await store.softDelete(id: id)
    }
}

/// [CategoryRepository] adossé à la base locale.
public struct LocalCategoryRepository: CategoryRepository {

    private let database: AppDatabase
    private let store: SyncedStore<CategoryRecord>

    public init(database: AppDatabase, now: @escaping Clock = Clocks.system) {
        self.database = database
        self.store = SyncedStore(database: database, entityType: .categories, now: now)
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

    public func delete(id: EntityID) async throws {
        try await store.softDelete(id: id)
    }
}

/// [TransactionRepository] adossé à la base locale (index sur la date : listes rapides même avec
/// des dizaines de milliers de transactions).
public struct LocalTransactionRepository: TransactionRepository {

    private let database: AppDatabase
    private let store: SyncedStore<TransactionRecord>

    public init(database: AppDatabase, now: @escaping Clock = Clocks.system) {
        self.database = database
        self.store = SyncedStore(database: database, entityType: .transactions, now: now)
    }

    public func observeTransactions(from: EpochMillis?, to: EpochMillis?) -> AsyncStream<[Transaction]> {
        database.observe { db in
            var request = TransactionRecord.filter(Column("deletedAt") == nil)
            if let from { request = request.filter(Column("date") >= from) }
            if let to { request = request.filter(Column("date") < to) }
            return try request
                .order(Column("date").desc, Column("createdAt").desc)
                .fetchAll(db)
                .map(\.domain)
        }
    }

    public func observeRecentTransactions(limit: Int) -> AsyncStream<[Transaction]> {
        database.observe { db in
            try TransactionRecord
                .filter(Column("deletedAt") == nil)
                .order(Column("date").desc, Column("createdAt").desc)
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

    public func delete(id: EntityID) async throws {
        try await store.softDelete(id: id)
    }
}
