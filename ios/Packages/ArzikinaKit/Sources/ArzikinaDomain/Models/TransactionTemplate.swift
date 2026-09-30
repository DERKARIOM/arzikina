/// Modèle de transaction réutilisable (favoris, heure par défaut, créé éventuellement à partir
/// d'une transaction existante).
public struct TransactionTemplate: Identifiable, Equatable, Hashable, Sendable {
    public var id: EntityID
    public var name: String
    public var type: TransactionType
    public var amount: MinorUnits
    public var categoryId: EntityID
    public var accountId: EntityID
    public var description: String
    public var isFavorite: Bool
    /// Heure par défaut proposée à l'utilisation du modèle (`nil` = heure courante).
    public var defaultHour: Int?
    public var defaultMinute: Int?
    /// Transaction à partir de laquelle le modèle a été créé, le cas échéant.
    public var sourceTransactionId: EntityID?
    public var createdAt: EpochMillis
    public var updatedAt: EpochMillis

    public init(
        id: EntityID,
        name: String,
        type: TransactionType,
        amount: MinorUnits,
        categoryId: EntityID,
        accountId: EntityID,
        description: String = "",
        isFavorite: Bool = false,
        defaultHour: Int? = nil,
        defaultMinute: Int? = nil,
        sourceTransactionId: EntityID? = nil,
        createdAt: EpochMillis = 0,
        updatedAt: EpochMillis = 0
    ) {
        self.id = id
        self.name = name
        self.type = type
        self.amount = amount
        self.categoryId = categoryId
        self.accountId = accountId
        self.description = description
        self.isFavorite = isFavorite
        self.defaultHour = defaultHour
        self.defaultMinute = defaultMinute
        self.sourceTransactionId = sourceTransactionId
        self.createdAt = createdAt
        self.updatedAt = updatedAt
    }
}
