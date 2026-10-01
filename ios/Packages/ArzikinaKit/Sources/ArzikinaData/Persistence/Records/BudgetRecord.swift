import ArzikinaDomain
import GRDB

/// Ligne de la table `budgets`.
struct BudgetRecord: SyncedRecord, Equatable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "budgets"

    var id: String
    var categoryId: String
    var period: String
    var limitAmount: Int64
    var currencyCode: String
    var startDate: Int64?
    var endDate: Int64?
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var version: Int64

    var meta: SyncMetadata {
        get { SyncMetadata(createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt, version: version) }
        set { createdAt = newValue.createdAt; updatedAt = newValue.updatedAt; deletedAt = newValue.deletedAt; version = newValue.version }
    }

    init(_ budget: Budget, meta: SyncMetadata) {
        id = budget.id
        categoryId = budget.categoryId
        period = budget.period.rawValue
        limitAmount = budget.limitAmount
        currencyCode = budget.currencyCode
        // Les deux dates, ou aucune (jamais une seule, comme Android).
        startDate = budget.hasFixedPeriod ? budget.startDate : nil
        endDate = budget.hasFixedPeriod ? budget.endDate : nil
        createdAt = meta.createdAt
        updatedAt = meta.updatedAt
        deletedAt = meta.deletedAt
        version = meta.version
    }

    var domain: Budget {
        Budget(
            id: id,
            categoryId: categoryId,
            period: BudgetPeriod(rawValue: period) ?? .monthly,
            limitAmount: limitAmount,
            currencyCode: currencyCode,
            startDate: startDate,
            endDate: endDate,
            createdAt: createdAt
        )
    }
}
