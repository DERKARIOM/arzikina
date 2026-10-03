import ArzikinaDomain
import GRDB

/// Ligne de la table `transaction_templates`.
struct TransactionTemplateRecord: SyncedRecord, Equatable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "transaction_templates"

    var id: String
    var name: String
    var type: String
    var amount: Int64
    var categoryId: String
    var accountId: String
    var description: String
    var isFavorite: Bool
    var defaultHour: Int?
    var defaultMinute: Int?
    var sourceTransactionId: String?
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var version: Int64

    var meta: SyncMetadata {
        get { SyncMetadata(createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt, version: version) }
        set { createdAt = newValue.createdAt; updatedAt = newValue.updatedAt; deletedAt = newValue.deletedAt; version = newValue.version }
    }

    init(_ template: TransactionTemplate, meta: SyncMetadata) {
        id = template.id
        name = template.name
        type = template.type.rawValue
        amount = template.amount
        categoryId = template.categoryId
        accountId = template.accountId
        description = template.description
        isFavorite = template.isFavorite
        defaultHour = template.defaultHour
        defaultMinute = template.defaultMinute
        sourceTransactionId = template.sourceTransactionId
        createdAt = meta.createdAt
        updatedAt = meta.updatedAt
        deletedAt = meta.deletedAt
        version = meta.version
    }

    var domain: TransactionTemplate {
        TransactionTemplate(
            id: id,
            name: name,
            type: TransactionType(rawValue: type) ?? .expense,
            amount: amount,
            categoryId: categoryId,
            accountId: accountId,
            description: description,
            isFavorite: isFavorite,
            // Heure par défaut : les deux ou aucune (Android).
            defaultHour: defaultMinute == nil ? nil : defaultHour,
            defaultMinute: defaultHour == nil ? nil : defaultMinute,
            sourceTransactionId: sourceTransactionId,
            createdAt: createdAt,
            updatedAt: updatedAt
        )
    }
}
