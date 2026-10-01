import Foundation

/// Génère les identifiants des nouvelles entités : UUID v4 en minuscules, même format qu'Android
/// (`UUID.randomUUID().toString()`) et que le serveur (`generateUuidV4()`).
public enum EntityIDs {
    public static func generate() -> EntityID {
        UUID().uuidString.lowercased()
    }
}

/// Comptes de l'utilisateur connecté.
///
/// Les écritures sont LOCALES et immédiates (l'app fonctionne hors ligne) ; chacune est aussi
/// notée pour être envoyée au serveur à la prochaine synchronisation.
public protocol AccountRepository: Sendable {
    /// Comptes non supprimés, dans l'ordre d'affichage, mis à jour à chaque modification.
    func observeAccounts() -> AsyncStream<[Account]>
    /// Solde courant de chaque compte non supprimé (même formule que `AccountBalances`).
    func observeBalances() -> AsyncStream<[EntityID: MinorUnits]>
    func account(id: EntityID) async throws -> Account?
    /// Crée le compte s'il n'existe pas, sinon le met à jour.
    func save(_ account: Account) async throws
    /// Suppression douce : le compte disparaît des listes et la suppression est synchronisée.
    func delete(id: EntityID) async throws
}

/// Catégories de l'utilisateur connecté.
public protocol CategoryRepository: Sendable {
    /// Catégories non supprimées (toutes, ou d'un seul [type]), triées par nom.
    func observeCategories(type: TransactionType?) -> AsyncStream<[Category]>
    func category(id: EntityID) async throws -> Category?
    func save(_ category: Category) async throws
    func delete(id: EntityID) async throws
}

/// Transactions de l'utilisateur connecté.
public protocol TransactionRepository: Sendable {
    /// Transactions non supprimées dont la date est dans [from, to[ (bornes en millisecondes),
    /// de la plus récente à la plus ancienne. `nil` = sans borne.
    func observeTransactions(from: EpochMillis?, to: EpochMillis?) -> AsyncStream<[Transaction]>
    /// Les [limit] transactions les plus récentes.
    func observeRecentTransactions(limit: Int) -> AsyncStream<[Transaction]>
    func transaction(id: EntityID) async throws -> Transaction?
    func save(_ transaction: Transaction) async throws
    func delete(id: EntityID) async throws
}

// MARK: - Tableau de bord

/// Une dernière transaction telle que l'affiche le tableau de bord, avec ce qu'elle référence.
public struct RecentTransaction: Identifiable, Equatable, Sendable {
    public var transaction: Transaction
    /// `nil` si la référence est orpheline (données partiellement synchronisées).
    public var account: Account?
    public var transferAccount: Account?
    public var category: Category?
    /// Montant de la transaction de frais liée, `nil` sans frais.
    public var feeAmount: MinorUnits?

    public var id: EntityID { transaction.id }

    public init(transaction: Transaction, account: Account?, transferAccount: Account? = nil, category: Category? = nil, feeAmount: MinorUnits? = nil) {
        self.transaction = transaction
        self.account = account
        self.transferAccount = transferAccount
        self.category = category
        self.feeAmount = feeAmount
    }
}

/// Instantané COHÉRENT du tableau de bord (lu en une seule transaction SQL).
public struct DashboardSnapshot: Equatable, Sendable {
    /// Comptes non supprimés (y compris ceux exclus des statistiques).
    public var accounts: [Account]
    /// Solde total par devise des comptes inclus dans les statistiques (`DashboardRules`).
    public var totalBalances: [CurrencyAmount]
    /// Revenus et dépenses du mois en cours, par devise.
    public var month: DashboardRules.PeriodTotals
    /// Dernières transactions, hors transactions de frais.
    public var recentTransactions: [RecentTransaction]

    public init(accounts: [Account], totalBalances: [CurrencyAmount], month: DashboardRules.PeriodTotals, recentTransactions: [RecentTransaction]) {
        self.accounts = accounts
        self.totalBalances = totalBalances
        self.month = month
        self.recentTransactions = recentTransactions
    }

    public static let empty = DashboardSnapshot(accounts: [], totalBalances: [], month: .init(), recentTransactions: [])
}

/// Données du tableau de bord, mises à jour en continu (saisie locale ou synchronisation).
public protocol DashboardRepository: Sendable {
    /// [monthStart, monthEnd[ : bornes du mois affiché (voir `DashboardRules.monthInterval`).
    func observeDashboard(monthStart: EpochMillis, monthEnd: EpochMillis, recentLimit: Int) -> AsyncStream<DashboardSnapshot>
}
