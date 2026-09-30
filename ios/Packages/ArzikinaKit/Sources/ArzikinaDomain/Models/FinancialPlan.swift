public enum PlanPeriodType: String, CaseIterable, Codable, Sendable {
    case none = "NONE"
    case monthly = "MONTHLY"
    case yearly = "YEARLY"
    case custom = "CUSTOM"
}

public enum PlanStatus: String, CaseIterable, Codable, Sendable {
    case active = "ACTIVE"
    case completed = "COMPLETED"
    case archived = "ARCHIVED"
}

public enum FinancialPlanIcon: String, CaseIterable, Codable, Sendable {
    case wallet = "WALLET"
    case ring = "RING"
    case motorcycle = "MOTORCYCLE"
    case plane = "PLANE"
    case home = "HOME"
    case graduation = "GRADUATION"
    case celebration = "CELEBRATION"
    case other = "OTHER"
}

public enum PlanItemPriority: String, CaseIterable, Codable, Sendable {
    case essential = "ESSENTIAL"
    case important = "IMPORTANT"
    case optional = "OPTIONAL"
}

public enum PlanItemStatus: String, CaseIterable, Codable, Sendable {
    case toPlan = "TO_PLAN"
    case done = "DONE"
    case cancelled = "CANCELLED"
}

/// Planification (projet) : un budget disponible et une liste de dépenses prévues.
public struct FinancialPlan: Identifiable, Equatable, Hashable, Sendable {
    public var id: EntityID
    public var name: String
    public var description: String?
    public var availableAmount: MinorUnits
    public var targetAmount: MinorUnits?
    public var periodType: PlanPeriodType
    public var startDate: EpochMillis?
    public var endDate: EpochMillis?
    public var icon: FinancialPlanIcon
    public var colorArgb: Int64
    public var status: PlanStatus
    public var createdAt: EpochMillis
    public var updatedAt: EpochMillis

    public init(
        id: EntityID,
        name: String,
        description: String? = nil,
        availableAmount: MinorUnits,
        targetAmount: MinorUnits? = nil,
        periodType: PlanPeriodType = .none,
        startDate: EpochMillis? = nil,
        endDate: EpochMillis? = nil,
        icon: FinancialPlanIcon = .wallet,
        colorArgb: Int64 = 0xFF42_B998,
        status: PlanStatus = .active,
        createdAt: EpochMillis = 0,
        updatedAt: EpochMillis = 0
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.availableAmount = availableAmount
        self.targetAmount = targetAmount
        self.periodType = periodType
        self.startDate = startDate
        self.endDate = endDate
        self.icon = icon
        self.colorArgb = colorArgb
        self.status = status
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}

/// Dépense prévue d'une planification. Peut être convertie en transaction ([transactionId]).
public struct FinancialPlanItem: Identifiable, Equatable, Hashable, Sendable {
    public var id: EntityID
    public var planId: EntityID
    public var name: String
    public var amount: MinorUnits
    public var actualAmount: MinorUnits?
    public var categoryId: EntityID?
    public var description: String?
    public var plannedDate: EpochMillis?
    public var priority: PlanItemPriority
    public var status: PlanItemStatus
    public var transactionId: EntityID?
    public var createdAt: EpochMillis
    public var updatedAt: EpochMillis

    public init(
        id: EntityID,
        planId: EntityID,
        name: String,
        amount: MinorUnits,
        actualAmount: MinorUnits? = nil,
        categoryId: EntityID? = nil,
        description: String? = nil,
        plannedDate: EpochMillis? = nil,
        priority: PlanItemPriority = .important,
        status: PlanItemStatus = .toPlan,
        transactionId: EntityID? = nil,
        createdAt: EpochMillis = 0,
        updatedAt: EpochMillis = 0
    ) {
        self.id = id
        self.planId = planId
        self.name = name
        self.amount = amount
        self.actualAmount = actualAmount
        self.categoryId = categoryId
        self.description = description
        self.plannedDate = plannedDate
        self.priority = priority
        self.status = status
        self.transactionId = transactionId
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}
