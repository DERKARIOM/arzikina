public enum CategoryIcon: String, CaseIterable, Codable, Sendable {
    case food = "FOOD"
    case transport = "TRANSPORT"
    case health = "HEALTH"
    case salary = "SALARY"
    case shopping = "SHOPPING"
    case gifts = "GIFTS"
    case internet = "INTERNET"
    case water = "WATER"
    case electricity = "ELECTRICITY"
    case education = "EDUCATION"
    case home = "HOME"
    case other = "OTHER"
    case loan = "LOAN"
    case fee = "FEE"
}

/// Catégorie de transaction. [type] vaut `.income` ou `.expense` (jamais `.transfer`).
///
/// Les catégories créées par défaut portent un nom de référence français (ex. « Nourriture ») que
/// l'interface traduit à l'affichage — même mécanisme qu'Android (`SystemCategoryKey`), qui sera
/// porté avec l'écran Catégories.
public struct Category: Identifiable, Equatable, Hashable, Sendable {
    public var id: EntityID
    public var name: String
    public var icon: CategoryIcon
    public var colorArgb: Int64
    public var type: TransactionType
    public var createdAt: EpochMillis

    public init(
        id: EntityID,
        name: String,
        icon: CategoryIcon = .other,
        colorArgb: Int64 = 0xFF42_B998,
        type: TransactionType,
        createdAt: EpochMillis = 0
    ) {
        self.id = id
        self.name = name
        self.icon = icon
        self.colorArgb = colorArgb
        self.type = type
        self.createdAt = createdAt
    }
}
