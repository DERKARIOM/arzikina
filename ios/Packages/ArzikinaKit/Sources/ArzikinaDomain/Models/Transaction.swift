/// Nature d'une transaction (valeurs Android `TransactionType` / API).
public enum TransactionType: String, CaseIterable, Codable, Sendable {
    case income = "INCOME"
    case expense = "EXPENSE"
    /// Débite `accountId` et crédite `transferAccountId` : neutre sur le total des comptes.
    case transfer = "TRANSFER"
}

public enum PaymentMethod: String, CaseIterable, Codable, Sendable {
    case cash = "CASH"
    case card = "CARD"
    case mobileMoney = "MOBILE_MONEY"
    case bankTransfer = "BANK_TRANSFER"
    case other = "OTHER"
}

/// Nature des frais d'une transaction « frais » liée (voir `Transaction.feeTransactionId`).
public enum FeeType: String, CaseIterable, Codable, Sendable {
    case transfer = "TRANSFER"
    case bank = "BANK"
    case commission = "COMMISSION"
    case service = "SERVICE"
    case other = "OTHER"
}

/// Transaction (revenu, dépense ou transfert). Les frais sont une transaction DÉPENSE séparée,
/// reliée par [feeTransactionId] — ils pèsent donc naturellement dans les soldes.
public struct Transaction: Identifiable, Equatable, Hashable, Sendable {
    public var id: EntityID
    /// Montant TOUJOURS positif ; le sens vient de [type] (voir [signedAmount]).
    public var amount: MinorUnits
    public var type: TransactionType
    public var accountId: EntityID
    /// Compte crédité d'un transfert, `nil` sinon.
    public var transferAccountId: EntityID?
    public var categoryId: EntityID?
    /// Date ET heure de la transaction.
    public var date: EpochMillis
    public var description: String
    public var latitude: Double?
    public var longitude: Double?
    public var paymentMethod: PaymentMethod?
    public var feeTransactionId: EntityID?
    public var feeType: FeeType?
    public var createdAt: EpochMillis

    public init(
        id: EntityID,
        amount: MinorUnits,
        type: TransactionType,
        accountId: EntityID,
        transferAccountId: EntityID? = nil,
        categoryId: EntityID? = nil,
        date: EpochMillis,
        description: String = "",
        latitude: Double? = nil,
        longitude: Double? = nil,
        paymentMethod: PaymentMethod? = nil,
        feeTransactionId: EntityID? = nil,
        feeType: FeeType? = nil,
        createdAt: EpochMillis = 0
    ) {
        self.id = id
        self.amount = amount
        self.type = type
        self.accountId = accountId
        self.transferAccountId = transferAccountId
        self.categoryId = categoryId
        self.date = date
        self.description = description
        self.latitude = latitude
        self.longitude = longitude
        self.paymentMethod = paymentMethod
        self.feeTransactionId = feeTransactionId
        self.feeType = feeType
        self.createdAt = createdAt
    }

    /// Effet sur le compte SOURCE : + pour un revenu, − pour une dépense ou un transfert sortant
    /// (Android `Transaction.signedAmount()`). Le crédit du compte destination d'un transfert est
    /// ajouté par `AccountBalances`.
    public var signedAmount: MinorUnits {
        type == .income ? amount : -amount
    }
}
