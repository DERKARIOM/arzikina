/// Cadence d'une automatisation (valeurs Android `RecurringFrequency` / API).
public enum RecurringFrequency: String, CaseIterable, Codable, Sendable {
    /// Une seule occurrence, jamais reconduite.
    case once = "ONCE"
    case daily = "DAILY"
    case weekly = "WEEKLY"
    /// Toutes les 2 semaines.
    case biweekly = "BIWEEKLY"
    case monthly = "MONTHLY"
    /// Tous les 3 mois.
    case quarterly = "QUARTERLY"
    /// Tous les 6 mois.
    case semiannual = "SEMIANNUAL"
    case yearly = "YEARLY"
}

/// Décision prise sur une occurrence générée.
public enum OccurrenceStatus: String, CaseIterable, Codable, Sendable {
    /// En attente de validation par l'utilisateur.
    case pending = "PENDING"
    case accepted = "ACCEPTED"
    /// Acceptée avec un montant/des détails modifiés.
    case modified = "MODIFIED"
    case rejected = "REJECTED"
}

/// Règle d'automatisation (transaction planifiée/récurrente). Chaque échéance arrivée produit une
/// occurrence `PENDING` que l'utilisateur valide ou rejette (voir `Recurrence`).
public struct RecurringTransaction: Identifiable, Equatable, Hashable, Sendable {
    public static let defaultTriggerHour = 8
    public static let defaultTriggerMinute = 0

    public var id: EntityID
    public var type: TransactionType
    public var amount: MinorUnits
    public var accountId: EntityID
    public var categoryId: EntityID?
    public var description: String
    public var paymentMethod: PaymentMethod?
    /// Jour de début (début de journée locale).
    public var startDate: EpochMillis
    public var endDate: EpochMillis?
    public var frequency: RecurringFrequency
    /// Jour de la prochaine échéance (début de journée locale).
    public var nextExecutionDate: EpochMillis
    public var isActive: Bool
    /// Heure de déclenchement (0-23) et minute (0-59) de chaque échéance.
    public var triggerHour: Int
    public var triggerMinute: Int
    public var createdAt: EpochMillis
    public var updatedAt: EpochMillis

    public init(
        id: EntityID,
        type: TransactionType,
        amount: MinorUnits,
        accountId: EntityID,
        categoryId: EntityID? = nil,
        description: String = "",
        paymentMethod: PaymentMethod? = nil,
        startDate: EpochMillis,
        endDate: EpochMillis? = nil,
        frequency: RecurringFrequency,
        nextExecutionDate: EpochMillis,
        isActive: Bool = true,
        triggerHour: Int = RecurringTransaction.defaultTriggerHour,
        triggerMinute: Int = RecurringTransaction.defaultTriggerMinute,
        createdAt: EpochMillis = 0,
        updatedAt: EpochMillis = 0
    ) {
        self.id = id
        self.type = type
        self.amount = amount
        self.accountId = accountId
        self.categoryId = categoryId
        self.description = description
        self.paymentMethod = paymentMethod
        self.startDate = startDate
        self.endDate = endDate
        self.frequency = frequency
        self.nextExecutionDate = nextExecutionDate
        self.isActive = isActive
        self.triggerHour = triggerHour
        self.triggerMinute = triggerMinute
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}

/// Occurrence générée d'une règle d'automatisation.
public struct RecurringTransactionOccurrence: Identifiable, Equatable, Hashable, Sendable {
    public var id: EntityID
    public var recurringTransactionId: EntityID
    public var scheduledDate: EpochMillis
    public var status: OccurrenceStatus
    /// Transaction créée à la validation, `nil` tant qu'elle est en attente ou si elle est rejetée.
    public var transactionId: EntityID?
    public var processedAt: EpochMillis?
    public var createdAt: EpochMillis

    public init(
        id: EntityID,
        recurringTransactionId: EntityID,
        scheduledDate: EpochMillis,
        status: OccurrenceStatus = .pending,
        transactionId: EntityID? = nil,
        processedAt: EpochMillis? = nil,
        createdAt: EpochMillis = 0
    ) {
        self.id = id
        self.recurringTransactionId = recurringTransactionId
        self.scheduledDate = scheduledDate
        self.status = status
        self.transactionId = transactionId
        self.processedAt = processedAt
        self.createdAt = createdAt
    }
}
