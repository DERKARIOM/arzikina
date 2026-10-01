import GRDB

/// Types d'entité synchronisés — valeurs identiques au paramètre `entity_type` de l'API
/// (`pull.php`) et au champ `entityType` de `push.php`.
public enum SyncEntityType: String, CaseIterable, Codable, Sendable {
    case accounts
    case categories
    case transactions
    case budgets
    case persons
    case loans
    case loanPayments = "loan_payments"
    case recurringTransactions = "recurring_transactions"
    case recurringTransactionOccurrences = "recurring_transaction_occurrences"
    case financialPlans = "financial_plans"
    case financialPlanItems = "financial_plan_items"
    case transactionTemplates = "transaction_templates"
    case userPreferences = "user_preferences"
}

/// Opération à envoyer au serveur (valeurs de `push.php`).
public enum SyncOperation: String, Codable, Sendable {
    case create = "CREATE"
    case update = "UPDATE"
    case delete = "DELETE"
}

/// Entrée de la file d'envoi (table `sync_queue`).
struct SyncQueueRecord: Codable, Equatable, Sendable, FetchableRecord, MutablePersistableRecord {
    static let databaseTableName = "sync_queue"

    var id: Int64?
    var entityType: SyncEntityType
    var entityId: String
    var operation: SyncOperation
    var enqueuedAt: Int64
    var attemptCount: Int
    var lastAttemptAt: Int64?
    var lastError: String?
    /// Incrémenté à chaque modification de l'entrée (voir migration `v2_sync_engine`).
    var revision: Int64

    mutating func didInsert(_ inserted: InsertionSuccess) {
        id = inserted.rowID
    }
}

/// Inscription des modifications locales dans la file d'envoi, DANS la même transaction SQL que
/// l'écriture elle-même : une modification ne peut jamais être enregistrée sans être notée pour
/// la synchronisation (ni l'inverse).
enum SyncQueue {

    /// Fusionne [operation] avec l'entrée déjà en attente pour cette entité :
    ///
    /// | En attente | Nouvelle  | Résultat                                             |
    /// |------------|-----------|------------------------------------------------------|
    /// | —          | X         | X                                                    |
    /// | CREATE     | UPDATE    | CREATE (le serveur ne connaît pas encore l'entité)   |
    /// | CREATE     | DELETE    | rien à envoyer (créée puis supprimée hors ligne)     |
    /// | UPDATE     | UPDATE    | UPDATE                                               |
    /// | UPDATE     | DELETE    | DELETE                                               |
    /// | DELETE     | X         | DELETE (une entité supprimée ne revient pas)         |
    static func enqueue(_ db: Database, type: SyncEntityType, entityId: String, operation: SyncOperation, now: Int64) throws {
        let existing = try SyncQueueRecord
            .filter(Column("entityType") == type.rawValue && Column("entityId") == entityId)
            .fetchOne(db)

        guard var entry = existing else {
            var record = SyncQueueRecord(
                id: nil, entityType: type, entityId: entityId, operation: operation,
                enqueuedAt: now, attemptCount: 0, lastAttemptAt: nil, lastError: nil, revision: 0
            )
            try record.insert(db)
            return
        }

        switch (entry.operation, operation) {
        case (.create, .delete):
            try entry.delete(db)
            return
        case (.delete, _), (.create, _):
            break // l'opération en attente reste la bonne
        case (.update, _):
            entry.operation = operation
        }
        entry.enqueuedAt = now
        entry.revision += 1
        // Nouvelle modification : les échecs précédents ne concernent plus ce contenu.
        entry.attemptCount = 0
        entry.lastError = nil
        try entry.update(db)
    }
}
