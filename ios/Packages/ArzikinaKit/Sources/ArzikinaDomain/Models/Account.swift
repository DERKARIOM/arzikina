/// Type FONCTIONNEL d'un compte (valeurs identiques à Android `AccountType` et à l'API).
public enum AccountType: String, CaseIterable, Codable, Sendable {
    case cash = "CASH"
    case bank = "BANK"
    case mobileMoney = "MOBILE_MONEY"
    case savings = "SAVINGS"
    /// Seules les 4 derniers chiffres et l'expiration sont conservés, jamais le numéro complet.
    case creditCard = "CREDIT_CARD"
    /// Objectif d'épargne : un compte à part entière qui porte en plus un montant cible
    /// (`Account.savingsTargetAmount`). Passer d'un compte classique à un objectif (ou l'inverse)
    /// ne change que ce type et la cible : même compte, mêmes transactions.
    case savingsGoal = "SAVINGS_GOAL"
}

/// Icône d'un compte — choix purement visuel (valeurs Android `AccountIcon`).
public enum AccountIcon: String, CaseIterable, Codable, Sendable {
    case cash = "CASH"
    case bank = "BANK"
    case mobileMoney = "MOBILE_MONEY"
    case savings = "SAVINGS"
    case wallet = "WALLET"
    case creditCard = "CREDIT_CARD"
    case other = "OTHER"
}

/// Compte financier (espèces, banque, Mobile Money, carte, épargne, objectif d'épargne).
///
/// [initialBalance] est le solde de départ ; le solde COURANT n'est jamais stocké, il est calculé
/// à partir des transactions (voir `AccountBalances`).
public struct Account: Identifiable, Equatable, Hashable, Sendable {
    public var id: EntityID
    public var name: String
    public var icon: AccountIcon
    /// Couleur ARGB 32 bits (même encodage qu'Android et l'API : `colorArgb`).
    public var colorArgb: Int64
    public var currencyCode: String
    public var initialBalance: MinorUnits
    public var type: AccountType
    public var cardLastFourDigits: String?
    public var cardExpiryMonth: Int?
    public var cardExpiryYear: Int?
    public var isExcludedFromStatistics: Bool
    /// Identifiant de l'app Mobile Money associée (package Android). Sans équivalent sur iOS, qui
    /// ne permet pas de lister les apps installées : conservé pour la synchronisation.
    public var mobileMoneyPackageName: String?
    public var displayOrder: Int64
    /// Montant cible d'un objectif d'épargne (unité mineure), `nil` pour les autres types.
    public var savingsTargetAmount: MinorUnits?
    public var savingsDescription: String?
    public var createdAt: EpochMillis

    public init(
        id: EntityID,
        name: String,
        icon: AccountIcon = .wallet,
        colorArgb: Int64 = 0xFF42_B998,
        currencyCode: String = SupportedCurrency.defaultCode,
        initialBalance: MinorUnits = 0,
        type: AccountType = .cash,
        cardLastFourDigits: String? = nil,
        cardExpiryMonth: Int? = nil,
        cardExpiryYear: Int? = nil,
        isExcludedFromStatistics: Bool = false,
        mobileMoneyPackageName: String? = nil,
        displayOrder: Int64 = 0,
        savingsTargetAmount: MinorUnits? = nil,
        savingsDescription: String? = nil,
        createdAt: EpochMillis = 0
    ) {
        self.id = id
        self.name = name
        self.icon = icon
        self.colorArgb = colorArgb
        self.currencyCode = currencyCode
        self.initialBalance = initialBalance
        self.type = type
        self.cardLastFourDigits = cardLastFourDigits
        self.cardExpiryMonth = cardExpiryMonth
        self.cardExpiryYear = cardExpiryYear
        self.isExcludedFromStatistics = isExcludedFromStatistics
        self.mobileMoneyPackageName = mobileMoneyPackageName
        self.displayOrder = displayOrder
        self.savingsTargetAmount = savingsTargetAmount
        self.savingsDescription = savingsDescription
        self.createdAt = createdAt
    }

    public var isSavingsGoal: Bool { type == .savingsGoal }
}
