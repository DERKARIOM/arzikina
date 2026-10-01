import GRDB

/// Opérations de la base locale propres à la synchronisation. Chaque méthode est UNE transaction
/// SQL : un échec (ou une coupure) en plein milieu ne laisse jamais un état à moitié appliqué.
struct SyncStore: Sendable {

    let database: AppDatabase

    // MARK: - Envoi

    /// Une modification prête à partir : ce qui est envoyé + de quoi reconnaître, au retour, si
    /// l'utilisateur a modifié l'entité pendant que la requête était en vol.
    struct OutgoingChange: Sendable {
        let entityId: String
        let operation: SyncOperation
        let revision: Int64
        let payload: [String: JSONValue]
    }

    /// Toutes les modifications en attente pour [schema], les plus anciennes d'abord.
    /// Les entrées dont la ligne a disparu localement (cas anormal) sont retirées de la file.
    func outgoingChanges(for schema: SyncEntitySchema) async throws -> [OutgoingChange] {
        try await database.writer.write { db in
            let entries = try SyncQueueRecord
                .filter(Column("entityType") == schema.type.rawValue)
                .order(Column("enqueuedAt"), Column("id"))
                .fetchAll(db)
            var changes: [OutgoingChange] = []
            for entry in entries {
                guard let payload = try SyncRowCodec.payload(db, schema: schema, id: entry.entityId, operation: entry.operation) else {
                    try entry.delete(db)
                    continue
                }
                changes.append(OutgoingChange(entityId: entry.entityId, operation: entry.operation, revision: entry.revision, payload: payload))
            }
            return changes
        }
    }

    /// Ce que le moteur doit faire d'une entrée de la file, une fois la réponse du serveur reçue.
    enum PushOutcome: Sendable {
        /// Le serveur a enregistré (ou arbitré) l'entité : voici son état final.
        case applied(serverEntity: [String: JSONValue])
        /// `not_found` sur une modification : le serveur n'a jamais reçu la création (base
        /// serveur restaurée, création perdue…). L'entité sera renvoyée comme une création.
        case resendAsCreate
        /// `not_found` sur une suppression : rien à supprimer côté serveur.
        case dropped
        /// Échec temporaire ou refus : l'entrée reste en file, l'erreur est notée.
        case failed(code: String)
    }

    /// Applique la réponse du serveur pour [change].
    ///
    /// Si l'entrée a été modifiée pendant l'envoi (révision différente), le contenu LOCAL est le
    /// plus récent : on n'enregistre que la nouvelle version serveur (pour le prochain
    /// `baseVersion`) et l'entrée reste en file pour un prochain envoi.
    func apply(_ outcome: PushOutcome, to change: OutgoingChange, schema: SyncEntitySchema, now: Int64) async throws {
        try await database.writer.write { db in
            let entry = try SyncQueueRecord
                .filter(Column("entityType") == schema.type.rawValue && Column("entityId") == change.entityId)
                .fetchOne(db)
            let unchanged = entry?.revision == change.revision

            switch outcome {
            case .applied(let serverEntity):
                guard let decoded = SyncRowCodec.localValues(from: serverEntity, schema: schema) else {
                    try markFailed(db, entry: entry, unchanged: unchanged, code: "invalid_server_entity", now: now)
                    return
                }
                let version = Int64.fromDatabaseValue(decoded.values["version"] ?? .null) ?? 0
                if var entry, !unchanged {
                    try SyncRowCodec.setVersion(db, schema: schema, id: change.entityId, version: version)
                    // Le serveur connaît désormais l'entité : une création encore en file doit
                    // repartir comme une modification (un second CREATE serait ignoré par
                    // `push.php`, qui renverrait l'ancienne version).
                    if entry.operation == .create {
                        entry.operation = .update
                        try entry.update(db)
                    }
                } else if let entry {
                    try SyncRowCodec.upsert(db, schema: schema, values: decoded.values)
                    try entry.delete(db)
                } else if try isDeletedLocally(db, schema: schema, id: change.entityId) {
                    // Entrée disparue pendant l'envoi : créée puis supprimée hors ligne (fusion
                    // CREATE + DELETE). Le serveur, lui, vient de la créer : il faut la supprimer.
                    try SyncRowCodec.setVersion(db, schema: schema, id: change.entityId, version: version)
                    try SyncQueue.enqueue(db, type: schema.type, entityId: change.entityId, operation: .delete, now: now)
                } else {
                    try SyncRowCodec.upsert(db, schema: schema, values: decoded.values)
                }

            case .resendAsCreate:
                guard var entry else { return }
                entry.operation = .create
                entry.revision += 1
                try entry.update(db)

            case .dropped:
                if let entry, unchanged { try entry.delete(db) }

            case .failed(let code):
                try markFailed(db, entry: entry, unchanged: unchanged, code: code, now: now)
            }
        }
    }

    private func isDeletedLocally(_ db: Database, schema: SyncEntitySchema, id: String) throws -> Bool {
        try Bool.fetchOne(db, sql: "SELECT deletedAt IS NOT NULL FROM \(schema.table) WHERE id = ?", arguments: [id]) ?? false
    }

    private func markFailed(_ db: Database, entry: SyncQueueRecord?, unchanged: Bool, code: String, now: Int64) throws {
        // Une entrée modifiée pendant l'envoi porte un nouveau contenu : l'échec ne la concerne pas.
        guard var entry, unchanged else { return }
        entry.attemptCount += 1
        entry.lastAttemptAt = now
        entry.lastError = code
        try entry.update(db)
    }

    // MARK: - Réception

    func cursor(for type: SyncEntityType) async throws -> Int64 {
        try await database.writer.read { db in
            try Int64.fetchOne(db, sql: "SELECT lastPulledAt FROM sync_cursors WHERE entityType = ?", arguments: [type.rawValue]) ?? 0
        }
    }

    /// Enregistre une page reçue et le nouveau curseur, ensemble.
    ///
    /// Les entités ayant une modification locale en attente ne sont PAS écrasées : la modification
    /// locale part au prochain envoi, et c'est le serveur qui arbitrera (dernière écriture arrivée).
    /// Retourne le nombre d'entités appliquées.
    @discardableResult
    func applyPulled(_ entities: [[String: JSONValue]], schema: SyncEntitySchema, newCursor: Int64) async throws -> Int {
        try await database.writer.write { db in
            let pending = try Set(String.fetchAll(
                db,
                sql: "SELECT entityId FROM sync_queue WHERE entityType = ?",
                arguments: [schema.type.rawValue]
            ))
            var applied = 0
            for entity in entities {
                guard let decoded = SyncRowCodec.localValues(from: entity, schema: schema),
                      !pending.contains(decoded.id)
                else { continue }
                try SyncRowCodec.upsert(db, schema: schema, values: decoded.values)
                applied += 1
            }
            try db.execute(
                sql: """
                INSERT INTO sync_cursors (entityType, lastPulledAt) VALUES (?, ?)
                ON CONFLICT(entityType) DO UPDATE SET lastPulledAt = excluded.lastPulledAt
                """,
                arguments: [schema.type.rawValue, newCursor]
            )
            return applied
        }
    }

    // MARK: - État

    static let lastSuccessKey = "lastSuccessfulSyncAt"

    func setLastSuccessfulSync(_ date: Int64) async throws {
        try await database.writer.write { db in
            try db.execute(
                sql: "INSERT INTO sync_state (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                arguments: [Self.lastSuccessKey, date]
            )
        }
    }

    func observeLastSuccessfulSync() -> AsyncStream<Int64?> {
        database.observe { db in
            try Int64.fetchOne(db, sql: "SELECT value FROM sync_state WHERE key = ?", arguments: [Self.lastSuccessKey])
        }
    }

    /// Entrées de la file en échec (au moins une tentative refusée par le serveur).
    func failingChangesCount() async throws -> Int {
        try await database.writer.read { db in
            try SyncQueueRecord.filter(Column("lastError") != nil).fetchCount(db)
        }
    }
}
