import GRDB

/// Schéma de la base locale et ses migrations.
///
/// RÈGLES (même discipline que les migrations Room d'Android) :
/// - une migration publiée n'est JAMAIS modifiée ni supprimée : toute évolution = une nouvelle
///   migration ajoutée à la fin (« v2_… », « v3_… ») ;
/// - les noms de colonnes sont ceux du domaine Swift (camelCase), ce qui permet le mapping
///   automatique des enregistrements GRDB ;
/// - chaque table synchronisée porte les colonnes de synchronisation communes au serveur :
///   `id` (UUID partagé), `createdAt`, `updatedAt`, `deletedAt` (suppression douce), `version`
///   (version serveur connue, 0 tant que la ligne n'a jamais été envoyée) ;
/// - AUCUNE clé étrangère SQL : comme sur le serveur, la synchronisation peut livrer une
///   transaction avant son compte ; l'intégrité est assurée par les dépôts. Des index couvrent les
///   colonnes de liaison les plus utilisées.
///
/// Les colonnes reflètent `server/api/config/entity_sync_configs.php`. L'ancienne table
/// `savings_goals` n'a pas d'équivalent sur iOS : les objectifs d'épargne sont des comptes de type
/// `SAVINGS_GOAL`.
enum AppDatabaseSchema {

    static var migrator: DatabaseMigrator {
        var migrator = DatabaseMigrator()
        #if DEBUG
        // En développement, une migration modifiée après coup recrée la base au lieu de laisser un
        // schéma incohérent. Sans effet en Release (build distribué).
        migrator.eraseDatabaseOnSchemaChange = true
        #endif
        migrator.registerMigration("v1_initial", migrate: createInitialSchema)
        migrator.registerMigration("v2_sync_engine", migrate: addSyncEngineSupport)
        migrator.registerMigration("v3_dashboard_indexes", migrate: addDashboardIndexes)
        migrator.registerMigration("v4_loan_gift", migrate: addLoanGift)
        return migrator
    }

    /// Version 1 : toutes les entités synchronisées, la file d'envoi et les curseurs de réception.
    private static func createInitialSchema(_ db: Database) throws {
        try db.create(table: "accounts") { t in
            t.primaryKey("id", .text)
            t.column("name", .text).notNull()
            t.column("icon", .text).notNull()
            t.column("colorArgb", .integer).notNull()
            t.column("currencyCode", .text).notNull()
            t.column("initialBalance", .integer).notNull()
            t.column("type", .text).notNull()
            t.column("cardLastFourDigits", .text)
            t.column("cardExpiryMonth", .integer)
            t.column("cardExpiryYear", .integer)
            t.column("isExcludedFromStatistics", .boolean).notNull().defaults(to: false)
            t.column("mobileMoneyPackageName", .text)
            t.column("displayOrder", .integer).notNull().defaults(to: 0)
            t.column("savingsTargetAmount", .integer)
            t.column("savingsDescription", .text)
            syncColumns(t)
        }

        try db.create(table: "categories") { t in
            t.primaryKey("id", .text)
            t.column("name", .text).notNull()
            t.column("icon", .text).notNull()
            t.column("colorArgb", .integer).notNull()
            t.column("type", .text).notNull()
            syncColumns(t)
        }

        try db.create(table: "transactions") { t in
            t.primaryKey("id", .text)
            t.column("amount", .integer).notNull()
            t.column("type", .text).notNull()
            t.column("accountId", .text).notNull().indexed()
            t.column("transferAccountId", .text).indexed()
            t.column("categoryId", .text).indexed()
            t.column("date", .integer).notNull().indexed()
            t.column("description", .text).notNull().defaults(to: "")
            t.column("latitude", .double)
            t.column("longitude", .double)
            t.column("paymentMethod", .text)
            t.column("feeTransactionId", .text)
            t.column("feeType", .text)
            syncColumns(t)
        }

        try db.create(table: "budgets") { t in
            t.primaryKey("id", .text)
            t.column("categoryId", .text).notNull().indexed()
            t.column("period", .text).notNull()
            t.column("limitAmount", .integer).notNull()
            t.column("currencyCode", .text).notNull()
            t.column("startDate", .integer)
            t.column("endDate", .integer)
            syncColumns(t)
        }

        try db.create(table: "persons") { t in
            t.primaryKey("id", .text)
            t.column("name", .text).notNull()
            t.column("phone", .text)
            syncColumns(t)
        }

        try db.create(table: "loans") { t in
            t.primaryKey("id", .text)
            t.column("personId", .text).notNull().indexed()
            t.column("accountId", .text).notNull()
            t.column("type", .text).notNull()
            t.column("amount", .integer).notNull()
            t.column("amountRepaid", .integer).notNull()
            t.column("remainingAmount", .integer).notNull()
            t.column("startDate", .integer).notNull()
            t.column("dueDate", .integer).notNull()
            t.column("reason", .text).notNull()
            t.column("reasonCustomText", .text)
            t.column("repaymentMode", .text).notNull()
            t.column("description", .text).notNull().defaults(to: "")
            t.column("status", .text).notNull()
            t.column("transactionId", .text).notNull()
            syncColumns(t)
        }

        try db.create(table: "loan_payments") { t in
            t.primaryKey("id", .text)
            t.column("loanId", .text).notNull().indexed()
            t.column("accountId", .text).notNull()
            t.column("amount", .integer).notNull()
            t.column("date", .integer).notNull()
            t.column("note", .text).notNull().defaults(to: "")
            t.column("transactionId", .text).notNull()
            syncColumns(t)
        }

        try db.create(table: "recurring_transactions") { t in
            t.primaryKey("id", .text)
            t.column("type", .text).notNull()
            t.column("amount", .integer).notNull()
            t.column("accountId", .text).notNull()
            t.column("categoryId", .text)
            t.column("description", .text).notNull().defaults(to: "")
            t.column("paymentMethod", .text)
            t.column("startDate", .integer).notNull()
            t.column("endDate", .integer)
            t.column("frequency", .text).notNull()
            t.column("nextExecutionDate", .integer).notNull()
            t.column("isActive", .boolean).notNull().defaults(to: true)
            t.column("triggerHour", .integer).notNull()
            t.column("triggerMinute", .integer).notNull()
            syncColumns(t)
        }

        try db.create(table: "recurring_transaction_occurrences") { t in
            t.primaryKey("id", .text)
            t.column("recurringTransactionId", .text).notNull().indexed()
            t.column("scheduledDate", .integer).notNull()
            t.column("status", .text).notNull()
            t.column("transactionId", .text)
            t.column("processedAt", .integer)
            syncColumns(t)
        }

        try db.create(table: "financial_plans") { t in
            t.primaryKey("id", .text)
            t.column("name", .text).notNull()
            t.column("description", .text)
            t.column("availableAmount", .integer).notNull()
            t.column("targetAmount", .integer)
            t.column("periodType", .text).notNull()
            t.column("startDate", .integer)
            t.column("endDate", .integer)
            t.column("icon", .text).notNull()
            t.column("colorArgb", .integer).notNull()
            t.column("status", .text).notNull()
            syncColumns(t)
        }

        try db.create(table: "financial_plan_items") { t in
            t.primaryKey("id", .text)
            t.column("planId", .text).notNull().indexed()
            t.column("name", .text).notNull()
            t.column("amount", .integer).notNull()
            t.column("actualAmount", .integer)
            t.column("categoryId", .text)
            t.column("description", .text)
            t.column("plannedDate", .integer)
            t.column("priority", .text).notNull()
            t.column("status", .text).notNull()
            t.column("transactionId", .text)
            syncColumns(t)
        }

        try db.create(table: "transaction_templates") { t in
            t.primaryKey("id", .text)
            t.column("name", .text).notNull()
            t.column("type", .text).notNull()
            t.column("amount", .integer).notNull()
            t.column("categoryId", .text).notNull()
            t.column("accountId", .text).notNull()
            t.column("description", .text).notNull().defaults(to: "")
            t.column("isFavorite", .boolean).notNull().defaults(to: false)
            t.column("defaultHour", .integer)
            t.column("defaultMinute", .integer)
            t.column("sourceTransactionId", .text)
            syncColumns(t)
        }

        try db.create(table: "user_preferences") { t in
            t.primaryKey("id", .text)
            t.column("themeMode", .text).notNull()
            t.column("currencyCode", .text).notNull()
            syncColumns(t)
        }

        // File d'envoi : UNE entrée au plus par entité (les modifications successives sont
        // fusionnées, voir `SyncQueue`). Le contenu envoyé est relu dans la table au moment de
        // l'envoi : toujours l'état le plus récent, jamais un instantané périmé.
        try db.create(table: "sync_queue") { t in
            t.autoIncrementedPrimaryKey("id")
            t.column("entityType", .text).notNull()
            t.column("entityId", .text).notNull()
            t.column("operation", .text).notNull()
            t.column("enqueuedAt", .integer).notNull()
            t.column("attemptCount", .integer).notNull().defaults(to: 0)
            t.column("lastAttemptAt", .integer)
            t.column("lastError", .text)
            t.uniqueKey(["entityType", "entityId"])
        }

        // Curseur de réception par type d'entité (horloge serveur), pour les pulls incrémentaux.
        try db.create(table: "sync_cursors") { t in
            t.primaryKey("entityType", .text)
            t.column("lastPulledAt", .integer).notNull()
        }
    }

    /// Version 2 : ce dont le moteur de synchronisation a besoin.
    ///
    /// - `sync_queue.revision` : compteur incrémenté à CHAQUE modification d'une entrée. Le moteur
    ///   le compare avant/après un envoi pour savoir si l'utilisateur a modifié l'entité pendant
    ///   que la requête était en vol (un horodatage à la milliseconde ne suffit pas à le garantir).
    /// - `sync_state` : petites valeurs persistantes du moteur (date de la dernière synchronisation
    ///   réussie…), par clé.
    private static func addSyncEngineSupport(_ db: Database) throws {
        try db.alter(table: "sync_queue") { t in
            t.add(column: "revision", .integer).notNull().defaults(to: 0)
        }
        try db.create(table: "sync_state") { t in
            t.primaryKey("key", .text)
            t.column("value", .integer).notNull()
        }
    }

    /// Version 3 : index du tableau de bord. Les dernières transactions excluent les transactions
    /// de frais (`id NOT IN (SELECT feeTransactionId …)`) : sans index, cette sous-requête
    /// parcourrait toute la table à chaque mise à jour de l'écran.
    private static func addDashboardIndexes(_ db: Database) throws {
        try db.create(index: "transactions_on_feeTransactionId", on: "transactions", columns: ["feeTransactionId"])
    }

    /// Version 4 : « Transformer un prêt en cadeau » (Android Room 33, MySQL 008) — trois colonnes
    /// ajoutées, sans toucher aux lignes existantes (0 / NULL = jamais transformé).
    private static func addLoanGift(_ db: Database) throws {
        try db.alter(table: "loans") { t in
            t.add(column: "giftedAmount", .integer).notNull().defaults(to: 0)
            t.add(column: "giftTransactionId", .text)
            t.add(column: "giftedAt", .integer)
        }
        // Retrouver le prêt d'une transaction cadeau (verrou du formulaire de transaction).
        try db.create(index: "loans_on_giftTransactionId", on: "loans", columns: ["giftTransactionId"])
        // Les prêts déjà reçus l'ont été SANS ces champs (version précédente de l'app) : ils sont
        // tous redemandés au serveur à la prochaine synchronisation, pour qu'un prêt transformé
        // en cadeau sur Android ou le Web apparaisse comme tel (les prêts modifiés localement et
        // pas encore envoyés ne sont pas écrasés, voir `SyncStore.applyPulled`).
        try db.execute(sql: "UPDATE sync_cursors SET lastPulledAt = 0 WHERE entityType = 'loans'")
    }

    /// Colonnes de synchronisation communes à toutes les entités.
    private static func syncColumns(_ t: TableDefinition) {
        t.column("createdAt", .integer).notNull()
        t.column("updatedAt", .integer).notNull()
        t.column("deletedAt", .integer).indexed()
        t.column("version", .integer).notNull().defaults(to: 0)
    }
}
