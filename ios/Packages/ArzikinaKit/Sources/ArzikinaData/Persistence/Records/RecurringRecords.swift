import ArzikinaDomain
import GRDB

/// Ligne de la table `recurring_transactions`.
struct RecurringTransactionRecord: SyncedRecord, Equatable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "recurring_transactions"

    var id: String
    var type: String
    var amount: Int64
    var accountId: String
    var categoryId: String?
    var description: String
    var paymentMethod: String?
    var startDate: Int64
    var endDate: Int64?
    var frequency: String
    var nextExecutionDate: Int64
    var isActive: Bool
    var triggerHour: Int
    var triggerMinute: Int
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var version: Int64

    var meta: SyncMetadata {
        get { SyncMetadata(createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt, version: version) }
        set { createdAt = newValue.createdAt; updatedAt = newValue.updatedAt; deletedAt = newValue.deletedAt; version = newValue.version }
    }

    init(_ rule: RecurringTransaction, meta: SyncMetadata) {
        id = rule.id
        type = rule.type.rawValue
        amount = rule.amount
        accountId = rule.accountId
        categoryId = rule.categoryId
        description = rule.description
        paymentMethod = rule.paymentMethod?.rawValue
        startDate = rule.startDate
        endDate = rule.endDate
        frequency = rule.frequency.rawValue
        nextExecutionDate = rule.nextExecutionDate
        isActive = rule.isActive
        triggerHour = rule.triggerHour
        triggerMinute = rule.triggerMinute
        createdAt = meta.createdAt
        updatedAt = meta.updatedAt
        deletedAt = meta.deletedAt
        version = meta.version
    }

    var domain: RecurringTransaction {
        RecurringTransaction(
            id: id,
            type: TransactionType(rawValue: type) ?? .expense,
            amount: amount,
            accountId: accountId,
            categoryId: categoryId,
            description: description,
            paymentMethod: paymentMethod.flatMap(PaymentMethod.init(rawValue:)),
            startDate: startDate,
            endDate: endDate,
            frequency: RecurringFrequency(rawValue: frequency) ?? .monthly,
            nextExecutionDate: nextExecutionDate,
            isActive: isActive,
            triggerHour: triggerHour,
            triggerMinute: triggerMinute,
            createdAt: createdAt,
            updatedAt: updatedAt
        )
    }
}

/// Ligne de la table `recurring_transaction_occurrences`.
struct RecurringOccurrenceRecord: SyncedRecord, Equatable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "recurring_transaction_occurrences"

    var id: String
    var recurringTransactionId: String
    var scheduledDate: Int64
    var status: String
    var transactionId: String?
    var processedAt: Int64?
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var version: Int64

    var meta: SyncMetadata {
        get { SyncMetadata(createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt, version: version) }
        set { createdAt = newValue.createdAt; updatedAt = newValue.updatedAt; deletedAt = newValue.deletedAt; version = newValue.version }
    }

    init(_ occurrence: RecurringTransactionOccurrence, meta: SyncMetadata) {
        id = occurrence.id
        recurringTransactionId = occurrence.recurringTransactionId
        scheduledDate = occurrence.scheduledDate
        status = occurrence.status.rawValue
        transactionId = occurrence.transactionId
        processedAt = occurrence.processedAt
        createdAt = meta.createdAt
        updatedAt = meta.updatedAt
        deletedAt = meta.deletedAt
        version = meta.version
    }

    var domain: RecurringTransactionOccurrence {
        RecurringTransactionOccurrence(
            id: id,
            recurringTransactionId: recurringTransactionId,
            scheduledDate: scheduledDate,
            status: OccurrenceStatus(rawValue: status) ?? .pending,
            transactionId: transactionId,
            processedAt: processedAt,
            createdAt: createdAt
        )
    }
}
