import GRDB

/// Écritures communes à toutes les entités synchronisées : création/mise à jour et suppression
/// douce, chacune inscrite dans la file d'envoi DANS la même transaction SQL.
///
/// Évite de répéter cette logique (métadonnées + file d'envoi) dans chaque dépôt.
struct SyncedStore<Record: SyncedRecord & FetchableRecord & PersistableRecord> {

    let database: AppDatabase
    let entityType: SyncEntityType
    let now: @Sendable () -> Int64

    /// Insère ou met à jour la ligne produite par [makeRecord], qui reçoit les métadonnées à
    /// appliquer : nouvelles pour une création (date de création [createdAt] si l'appelant en
    /// fournit une, sinon « maintenant »), conservées et datées pour une mise à jour.
    func save(id: String, createdAt: Int64, _ makeRecord: @escaping @Sendable (SyncMetadata) -> Record) async throws {
        let timestamp = now()
        let type = entityType
        try await database.writer.write { db in
            if let existing = try Record.fetchOne(db, key: id) {
                try makeRecord(existing.meta.touched(now: timestamp)).update(db)
                try SyncQueue.enqueue(db, type: type, entityId: id, operation: .update, now: timestamp)
            } else {
                try makeRecord(.new(createdAt: createdAt, now: timestamp)).insert(db)
                try SyncQueue.enqueue(db, type: type, entityId: id, operation: .create, now: timestamp)
            }
        }
    }

    /// Suppression douce. Sans effet si la ligne n'existe pas ou est déjà supprimée.
    func softDelete(id: String) async throws {
        let timestamp = now()
        let type = entityType
        try await database.writer.write { db in
            guard var record = try Record.fetchOne(db, key: id), record.meta.deletedAt == nil else { return }
            record.meta = record.meta.deleted(now: timestamp)
            try record.update(db)
            try SyncQueue.enqueue(db, type: type, entityId: id, operation: .delete, now: timestamp)
        }
    }

    /// Ligne non supprimée d'identifiant [id].
    func fetchActive(id: String) async throws -> Record? {
        try await database.writer.read { db in
            guard let record = try Record.fetchOne(db, key: id), record.meta.deletedAt == nil else { return nil }
            return record
        }
    }
}
