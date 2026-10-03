import ArzikinaDomain
import Foundation
import GRDB

/// [TransactionTemplateRepository] adossé à la base locale — Android
/// `TransactionTemplateRepositoryImpl`. Chaque écriture est inscrite dans la file d'envoi.
public struct LocalTransactionTemplateRepository: TransactionTemplateRepository {

    private let database: AppDatabase
    private let now: Clock
    private let store: SyncedStore<TransactionTemplateRecord>

    public init(database: AppDatabase, now: @escaping Clock = Clocks.system) {
        self.database = database
        self.now = now
        store = SyncedStore(database: database, entityType: .transactionTemplates, now: now)
    }

    public func observeTemplates() -> AsyncStream<[TransactionTemplate]> {
        database.observe { db in
            try TransactionTemplateRecord
                .filter(Column("deletedAt") == nil)
                .order(Column("isFavorite").desc, Column("name").collating(.localizedCaseInsensitiveCompare), Column("id"))
                .fetchAll(db)
                .map(\.domain)
        }
    }

    public func template(id: EntityID) async throws -> TransactionTemplate? {
        try await store.fetchActive(id: id)?.domain
    }

    public func template(createdFromTransaction transactionId: EntityID) async throws -> TransactionTemplate? {
        try await database.writer.read { db in
            try Self.linkedTemplate(db, transactionId: transactionId)?.domain
        }
    }

    static func linkedTemplate(_ db: Database, transactionId: EntityID) throws -> TransactionTemplateRecord? {
        try TransactionTemplateRecord
            .filter(Column("sourceTransactionId") == transactionId && Column("deletedAt") == nil)
            .order(Column("createdAt"), Column("id"))
            .fetchOne(db)
    }

    public func save(_ template: TransactionTemplate) async throws {
        let timestamp = now()
        let store = self.store
        try await database.writer.write { db in
            var saved = template
            if let existing = try TransactionTemplateRecord.fetchOne(db, key: template.id) {
                // Supprimé entre-temps (sur un autre appareil) : jamais ressuscité.
                guard existing.deletedAt == nil else { throw TemplateWriteError.templateNotFound }
                // Lien avec la transaction d'origine figé à la création (Android).
                saved.sourceTransactionId = existing.sourceTransactionId
                saved.createdAt = existing.createdAt
            } else if let source = saved.sourceTransactionId,
                      let linked = try Self.linkedTemplate(db, transactionId: source) {
                // Un seul modèle par transaction, même après une double validation (Android
                // `TemplateAlreadyLinkedException`).
                throw TemplateWriteError.alreadyLinked(existingTemplateId: linked.id)
            }
            try store.save(db, id: saved.id, createdAt: saved.createdAt, timestamp: timestamp) { [saved] in TransactionTemplateRecord(saved, meta: $0) }
        }
    }

    public func duplicate(id: EntityID, name: String) async throws -> EntityID {
        let timestamp = now()
        let store = self.store
        return try await database.writer.write { db in
            guard let original = try TransactionTemplateRecord.fetchOne(db, key: id), original.deletedAt == nil else {
                throw TemplateWriteError.templateNotFound
            }
            var copy = original.domain
            copy.id = EntityIDs.generate()
            copy.name = name
            // Une copie n'est ni favorite ni « créée à partir de la transaction » (sinon la
            // transaction semblerait liée à deux modèles).
            copy.isFavorite = false
            copy.sourceTransactionId = nil
            copy.createdAt = timestamp
            try store.save(db, id: copy.id, createdAt: timestamp, timestamp: timestamp) { [copy] in TransactionTemplateRecord(copy, meta: $0) }
            return copy.id
        }
    }

    public func setFavorite(id: EntityID, isFavorite: Bool) async throws {
        let timestamp = now()
        let store = self.store
        try await database.writer.write { db in
            guard var template = try TransactionTemplateRecord.fetchOne(db, key: id).flatMap({ $0.deletedAt == nil ? $0.domain : nil }) else {
                throw TemplateWriteError.templateNotFound
            }
            guard template.isFavorite != isFavorite else { return }
            template.isFavorite = isFavorite
            try store.save(db, id: template.id, createdAt: template.createdAt, timestamp: timestamp) { [template] in TransactionTemplateRecord(template, meta: $0) }
        }
    }

    public func delete(id: EntityID) async throws {
        try await store.softDelete(id: id)
    }
}
