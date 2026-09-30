public enum BudgetPeriod: String, CaseIterable, Codable, Sendable {
    case weekly = "WEEKLY"
    case monthly = "MONTHLY"
}

/// Plafond de dépenses pour une catégorie.
///
/// Deux modes, comme sur Android :
/// - période FIXE : [startDate] et [endDate] renseignés (jours inclus), [period] ignoré ;
/// - budget RÉCURRENT : dates `nil`, la période est la semaine ISO ou le mois civil EN COURS.
public struct Budget: Identifiable, Equatable, Hashable, Sendable {
    public var id: EntityID
    public var categoryId: EntityID
    public var period: BudgetPeriod
    public var limitAmount: MinorUnits
    /// Seules les dépenses des comptes dans cette devise sont comptées (aucune conversion).
    public var currencyCode: String
    public var startDate: EpochMillis?
    public var endDate: EpochMillis?
    public var createdAt: EpochMillis

    public init(
        id: EntityID,
        categoryId: EntityID,
        period: BudgetPeriod = .monthly,
        limitAmount: MinorUnits,
        currencyCode: String = SupportedCurrency.defaultCode,
        startDate: EpochMillis? = nil,
        endDate: EpochMillis? = nil,
        createdAt: EpochMillis = 0
    ) {
        self.id = id
        self.categoryId = categoryId
        self.period = period
        self.limitAmount = limitAmount
        self.currencyCode = currencyCode
        self.startDate = startDate
        self.endDate = endDate
        self.createdAt = createdAt
    }

    public var hasFixedPeriod: Bool { startDate != nil && endDate != nil }
}
