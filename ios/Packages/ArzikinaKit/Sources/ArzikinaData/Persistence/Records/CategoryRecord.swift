import ArzikinaDomain
import GRDB

/// Ligne de la table `categories`.
struct CategoryRecord: SyncedRecord, Equatable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "categories"

    var id: String
    var name: String
    var icon: String
    var colorArgb: Int64
    var type: String
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var version: Int64

    var meta: SyncMetadata {
        get { SyncMetadata(createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt, version: version) }
        set { createdAt = newValue.createdAt; updatedAt = newValue.updatedAt; deletedAt = newValue.deletedAt; version = newValue.version }
    }

    init(_ category: ArzikinaDomain.Category, meta: SyncMetadata) {
        id = category.id
        name = category.name
        icon = category.icon.rawValue
        colorArgb = category.colorArgb
        type = category.type.rawValue
        createdAt = meta.createdAt
        updatedAt = meta.updatedAt
        deletedAt = meta.deletedAt
        version = meta.version
    }

    var domain: ArzikinaDomain.Category {
        ArzikinaDomain.Category(
            id: id,
            name: name,
            icon: CategoryIcon(rawValue: icon) ?? .other,
            colorArgb: colorArgb,
            type: TransactionType(rawValue: type) ?? .expense,
            createdAt: createdAt
        )
    }
}
