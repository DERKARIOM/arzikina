/// Description déclarative des entités synchronisées — miroir EXACT du registre serveur
/// `server/api/config/entity_sync_configs.php` (`ENTITY_CONFIGS`).
///
/// Pour chaque type : la table locale et, pour chaque colonne, son nom local (camelCase du
/// domaine), sa clé dans le JSON de l'API et sa nature. Le moteur de synchronisation lit et écrit
/// toutes les entités à partir de cette seule description (aucun code dupliqué par entité), comme
/// `push.php`/`pull.php` côté serveur.
///
/// Les colonnes IMPLICITES (`id`, `createdAt`, `updatedAt`, `deletedAt`, `version`) sont communes
/// à toutes les entités et ne sont pas répétées ici. `userId` n'est jamais stocké localement (une
/// base par utilisateur) ni envoyé (le serveur le déduit du token).
struct SyncEntitySchema: Sendable {

    enum Kind: Sendable {
        case text
        case integer
        case real
        /// Booléen stocké 0/1 (le serveur le reçoit et le renvoie comme entier).
        case bool
    }

    struct Field: Sendable {
        /// Colonne locale.
        let local: String
        /// Clé JSON de l'API.
        let payload: String
        let kind: Kind
        let nullable: Bool
        /// Valeur vide (0 / nul) NON envoyée : le serveur garde la sienne (`push.php` : champ
        /// absent = conserver). Pour un état que d'autres appareils posent et que celui-ci ne
        /// remet jamais à zéro (prêt transformé en cadeau) : une copie locale en retard ne peut
        /// pas l'effacer.
        var sentOnlyWhenSet = false
    }

    let type: SyncEntityType
    let fields: [Field]

    var table: String { type.rawValue }

    /// Ordre de synchronisation : les entités « parentes » avant celles qui y font référence
    /// (le serveur n'impose aucune contrainte, mais l'état local reste ainsi cohérent à tout moment).
    static let all: [SyncEntitySchema] = [
        userPreferences, categories, accounts, persons, transactions, budgets, loans, loanPayments,
        recurringTransactions, recurringTransactionOccurrences, financialPlans, financialPlanItems,
        transactionTemplates
    ]

    static func schema(for type: SyncEntityType) -> SyncEntitySchema {
        all.first { $0.type == type }!
    }
}

extension SyncEntitySchema.Field {
    fileprivate func whenSetOnly() -> Self {
        var copy = self
        copy.sentOnlyWhenSet = true
        return copy
    }
}

// MARK: - Registre (voir entity_sync_configs.php)

/// Champ dont la clé API est identique au nom de colonne local.
private func field(_ local: String, _ kind: SyncEntitySchema.Kind, nullable: Bool = false) -> SyncEntitySchema.Field {
    SyncEntitySchema.Field(local: local, payload: local, kind: kind, nullable: nullable)
}

/// Champ renommé dans l'API (références `*SyncId`, `initialBalanceMinor`).
private func field(_ local: String, api payload: String, _ kind: SyncEntitySchema.Kind, nullable: Bool = false) -> SyncEntitySchema.Field {
    SyncEntitySchema.Field(local: local, payload: payload, kind: kind, nullable: nullable)
}

extension SyncEntitySchema {

    static let userPreferences = SyncEntitySchema(type: .userPreferences, fields: [
        field("themeMode", .text),
        field("currencyCode", .text)
    ])

    static let categories = SyncEntitySchema(type: .categories, fields: [
        field("name", .text),
        field("icon", .text),
        field("colorArgb", .integer),
        field("type", .text)
    ])

    static let accounts = SyncEntitySchema(type: .accounts, fields: [
        field("name", .text),
        field("icon", .text),
        field("colorArgb", .integer),
        field("currencyCode", .text),
        field("initialBalance", api: "initialBalanceMinor", .integer),
        field("type", .text),
        field("cardLastFourDigits", .text, nullable: true),
        field("cardExpiryMonth", .integer, nullable: true),
        field("cardExpiryYear", .integer, nullable: true),
        field("isExcludedFromStatistics", .bool),
        field("mobileMoneyPackageName", .text, nullable: true),
        field("displayOrder", .integer),
        field("savingsTargetAmount", .integer, nullable: true),
        field("savingsDescription", .text, nullable: true)
    ])

    static let persons = SyncEntitySchema(type: .persons, fields: [
        field("name", .text),
        field("phone", .text, nullable: true)
    ])

    static let transactions = SyncEntitySchema(type: .transactions, fields: [
        field("amount", .integer),
        field("type", .text),
        field("accountId", api: "accountSyncId", .text),
        field("transferAccountId", api: "transferAccountSyncId", .text, nullable: true),
        field("categoryId", api: "categorySyncId", .text, nullable: true),
        field("date", .integer),
        field("description", .text),
        field("latitude", .real, nullable: true),
        field("longitude", .real, nullable: true),
        field("paymentMethod", .text, nullable: true),
        field("feeTransactionId", api: "feeTransactionSyncId", .text, nullable: true),
        field("feeType", .text, nullable: true)
    ])

    static let budgets = SyncEntitySchema(type: .budgets, fields: [
        field("categoryId", api: "categorySyncId", .text),
        field("period", .text),
        field("limitAmount", .integer),
        field("currencyCode", .text),
        field("startDate", .integer, nullable: true),
        field("endDate", .integer, nullable: true)
    ])

    static let loans = SyncEntitySchema(type: .loans, fields: [
        field("personId", api: "personSyncId", .text),
        field("accountId", api: "accountSyncId", .text),
        field("type", .text),
        field("amount", .integer),
        field("amountRepaid", .integer),
        field("remainingAmount", .integer),
        field("startDate", .integer),
        field("dueDate", .integer),
        field("reason", .text),
        field("reasonCustomText", .text, nullable: true),
        field("repaymentMode", .text),
        field("description", .text),
        field("status", .text),
        field("transactionId", api: "transactionSyncId", .text),
        // « Transformer en cadeau » (migration serveur 008) : absent d'une réponse d'un ancien
        // serveur → 0 / nul, comme `push.php` l'appliquerait.
        // Envoyés seulement s'ils sont renseignés : l'iPhone ne « dé-transforme » jamais un prêt.
        field("giftedAmount", .integer).whenSetOnly(),
        field("giftTransactionId", api: "giftTransactionSyncId", .text, nullable: true).whenSetOnly(),
        field("giftedAt", .integer, nullable: true).whenSetOnly()
    ])

    static let loanPayments = SyncEntitySchema(type: .loanPayments, fields: [
        field("loanId", api: "loanSyncId", .text),
        field("accountId", api: "accountSyncId", .text),
        field("amount", .integer),
        field("date", .integer),
        field("note", .text),
        field("transactionId", api: "transactionSyncId", .text)
    ])

    static let recurringTransactions = SyncEntitySchema(type: .recurringTransactions, fields: [
        field("type", .text),
        field("amount", .integer),
        field("accountId", api: "accountSyncId", .text),
        field("categoryId", api: "categorySyncId", .text, nullable: true),
        field("description", .text),
        field("paymentMethod", .text, nullable: true),
        field("startDate", .integer),
        field("endDate", .integer, nullable: true),
        field("frequency", .text),
        field("nextExecutionDate", .integer),
        field("isActive", .bool),
        field("triggerHour", .integer),
        field("triggerMinute", .integer)
    ])

    static let recurringTransactionOccurrences = SyncEntitySchema(type: .recurringTransactionOccurrences, fields: [
        field("recurringTransactionId", api: "recurringTransactionSyncId", .text),
        field("scheduledDate", .integer),
        field("status", .text),
        field("transactionId", api: "transactionSyncId", .text, nullable: true),
        field("processedAt", .integer, nullable: true)
    ])

    static let financialPlans = SyncEntitySchema(type: .financialPlans, fields: [
        field("name", .text),
        field("description", .text, nullable: true),
        field("availableAmount", .integer),
        field("targetAmount", .integer, nullable: true),
        field("periodType", .text),
        field("startDate", .integer, nullable: true),
        field("endDate", .integer, nullable: true),
        field("icon", .text),
        field("colorArgb", .integer),
        field("status", .text)
    ])

    static let financialPlanItems = SyncEntitySchema(type: .financialPlanItems, fields: [
        field("planId", api: "planSyncId", .text),
        field("name", .text),
        field("amount", .integer),
        field("actualAmount", .integer, nullable: true),
        field("categoryId", api: "categorySyncId", .text, nullable: true),
        field("description", .text, nullable: true),
        field("plannedDate", .integer, nullable: true),
        field("priority", .text),
        field("status", .text),
        field("transactionId", api: "transactionSyncId", .text, nullable: true)
    ])

    static let transactionTemplates = SyncEntitySchema(type: .transactionTemplates, fields: [
        field("name", .text),
        field("type", .text),
        field("amount", .integer),
        field("categoryId", api: "categorySyncId", .text),
        field("accountId", api: "accountSyncId", .text),
        field("description", .text),
        field("isFavorite", .bool),
        field("defaultHour", .integer, nullable: true),
        field("defaultMinute", .integer, nullable: true),
        field("sourceTransactionId", api: "sourceTransactionSyncId", .text, nullable: true)
    ])
}
