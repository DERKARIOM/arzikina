import ArzikinaDomain
import GRDB

/// Ligne de `financial_plans`, en LECTURE seule pour l'instant : les planifications arrivent par
/// la synchronisation (créées sur Android / le Web) et sont incluses dans l'export.
struct FinancialPlanRecord: Decodable, FetchableRecord, TableRecord {
    static let databaseTableName = "financial_plans"

    var id: String
    var name: String
    var description: String?
    var availableAmount: Int64
    var targetAmount: Int64?
    var periodType: String
    var startDate: Int64?
    var endDate: Int64?
    var icon: String
    var colorArgb: Int64
    var status: String
    var createdAt: Int64
    var updatedAt: Int64

    var domain: FinancialPlan {
        FinancialPlan(
            id: id,
            name: name,
            description: description,
            availableAmount: availableAmount,
            targetAmount: targetAmount,
            periodType: PlanPeriodType(rawValue: periodType) ?? .none,
            startDate: startDate,
            endDate: endDate,
            icon: FinancialPlanIcon(rawValue: icon) ?? .other,
            colorArgb: colorArgb,
            status: PlanStatus(rawValue: status) ?? .active,
            createdAt: createdAt,
            updatedAt: updatedAt
        )
    }
}

/// Ligne de `financial_plan_items`, en lecture seule (voir `FinancialPlanRecord`).
struct FinancialPlanItemRecord: Decodable, FetchableRecord, TableRecord {
    static let databaseTableName = "financial_plan_items"

    var id: String
    var planId: String
    var name: String
    var amount: Int64
    var actualAmount: Int64?
    var categoryId: String?
    var description: String?
    var plannedDate: Int64?
    var priority: String
    var status: String
    var transactionId: String?
    var createdAt: Int64
    var updatedAt: Int64

    var domain: FinancialPlanItem {
        FinancialPlanItem(
            id: id,
            planId: planId,
            name: name,
            amount: amount,
            actualAmount: actualAmount,
            categoryId: categoryId,
            description: description,
            plannedDate: plannedDate,
            priority: PlanItemPriority(rawValue: priority) ?? .important,
            status: PlanItemStatus(rawValue: status) ?? .toPlan,
            transactionId: transactionId,
            createdAt: createdAt,
            updatedAt: updatedAt
        )
    }
}
