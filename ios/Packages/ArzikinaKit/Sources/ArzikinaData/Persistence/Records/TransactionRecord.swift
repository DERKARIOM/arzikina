import ArzikinaDomain
import GRDB

/// Ligne de la table `transactions`.
struct TransactionRecord: SyncedRecord, Equatable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "transactions"

    var id: String
    var amount: Int64
    var type: String
    var accountId: String
    var transferAccountId: String?
    var categoryId: String?
    var date: Int64
    var description: String
    var latitude: Double?
    var longitude: Double?
    var paymentMethod: String?
    var feeTransactionId: String?
    var feeType: String?
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var version: Int64

    var meta: SyncMetadata {
        get { SyncMetadata(createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt, version: version) }
        set { createdAt = newValue.createdAt; updatedAt = newValue.updatedAt; deletedAt = newValue.deletedAt; version = newValue.version }
    }

    init(_ transaction: Transaction, meta: SyncMetadata) {
        id = transaction.id
        amount = transaction.amount
        type = transaction.type.rawValue
        accountId = transaction.accountId
        transferAccountId = transaction.transferAccountId
        categoryId = transaction.categoryId
        date = transaction.date
        description = transaction.description
        latitude = transaction.latitude
        longitude = transaction.longitude
        paymentMethod = transaction.paymentMethod?.rawValue
        feeTransactionId = transaction.feeTransactionId
        feeType = transaction.feeType?.rawValue
        createdAt = meta.createdAt
        updatedAt = meta.updatedAt
        deletedAt = meta.deletedAt
        version = meta.version
    }

    var domain: Transaction {
        Transaction(
            id: id,
            amount: amount,
            type: TransactionType(rawValue: type) ?? .expense,
            accountId: accountId,
            transferAccountId: transferAccountId,
            categoryId: categoryId,
            date: date,
            description: description,
            latitude: latitude,
            longitude: longitude,
            paymentMethod: paymentMethod.flatMap(PaymentMethod.init(rawValue:)),
            feeTransactionId: feeTransactionId,
            feeType: feeType.flatMap(FeeType.init(rawValue:)),
            createdAt: createdAt
        )
    }
}
