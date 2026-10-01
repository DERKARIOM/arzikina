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
    /// Position d'un nouveau compte : après tous les comptes existants.
    func nextDisplayOrder() async throws -> Int64
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
    /// Enregistre [transaction] telle quelle (`feeTransactionId` compris).
    func save(_ transaction: Transaction) async throws
    /// Enregistre [transaction] avec ses frais : créés, mis à jour ou supprimés (`fee == nil`)
    /// en même temps que la transaction. Seule écriture utilisée par le formulaire.
    func save(_ transaction: Transaction, fee: TransactionFee?) async throws
    /// Supprime la transaction et sa transaction de frais.
    func delete(id: EntityID) async throws
    /// `true` si la transaction fait partie d'un prêt ou d'un remboursement : elle se gère alors
    /// depuis le prêt (comme sur Android), jamais depuis le formulaire de transaction.
    func isLinkedToLoan(id: EntityID) async throws -> Bool
}

// MARK: - Tableau de bord

/// Une transaction telle qu'une liste l'affiche (tableau de bord, détail d'un compte…), avec ce
/// qu'elle référence.
public struct TransactionListItem: Identifiable, Equatable, Sendable {
    public var transaction: Transaction
    /// Compte de la ligne : le compte SOURCE, ou le compte consulté dans le détail d'un compte.
    /// `nil` si la référence est orpheline (données partiellement synchronisées).
    public var account: Account?
    /// Pour un transfert : l'AUTRE compte, vu depuis [account] (destination d'un transfert
    /// sortant, source d'un transfert reçu). `nil` sinon.
    public var transferAccount: Account?
    public var category: Category?
    /// Montant de la transaction de frais liée, `nil` sans frais.
    public var feeAmount: MinorUnits?
    /// Solde du compte de la ligne juste APRÈS cette transaction (détail d'un compte), `nil`
    /// ailleurs.
    public var runningBalance: MinorUnits?

    public var id: EntityID { transaction.id }

    public init(
        transaction: Transaction,
        account: Account?,
        transferAccount: Account? = nil,
        category: Category? = nil,
        feeAmount: MinorUnits? = nil,
        runningBalance: MinorUnits? = nil
    ) {
        self.transaction = transaction
        self.account = account
        self.transferAccount = transferAccount
        self.category = category
        self.feeAmount = feeAmount
        self.runningBalance = runningBalance
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
    public var recentTransactions: [TransactionListItem]

    public init(accounts: [Account], totalBalances: [CurrencyAmount], month: DashboardRules.PeriodTotals, recentTransactions: [TransactionListItem]) {
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

// MARK: - Comptes (lecture)

/// Un compte et son solde courant.
public struct AccountSummary: Identifiable, Equatable, Sendable {
    public var account: Account
    public var balance: MinorUnits

    public var id: EntityID { account.id }

    public init(account: Account, balance: MinorUnits) {
        self.account = account
        self.balance = balance
    }

    /// Progression si le compte est un objectif d'épargne avec un montant cible, `nil` sinon.
    public var savingsGoal: SavingsGoalSnapshot? {
        guard account.type == .savingsGoal else { return nil }
        return SavingsGoalProgress.snapshot(balance: balance, target: account.savingsTargetAmount)
    }
}

/// Détail d'un compte : son solde et TOUTES ses lignes (transactions dont il est la source ou la
/// destination d'un transfert), de la plus récente à la plus ancienne, avec le solde après chacune.
/// Les transactions de frais comptent dans les soldes mais n'ont pas de ligne propre.
public struct AccountDetail: Equatable, Sendable {
    public var summary: AccountSummary
    public var transactions: [TransactionListItem]

    public init(summary: AccountSummary, transactions: [TransactionListItem]) {
        self.summary = summary
        self.transactions = transactions
    }
}

/// Écrans Comptes et détail d'un compte, mis à jour en continu.
public protocol AccountOverviewRepository: Sendable {
    /// Comptes non supprimés, dans l'ordre d'affichage, avec leur solde courant.
    func observeAccountSummaries() -> AsyncStream<[AccountSummary]>
    /// `nil` si le compte n'existe pas ou a été supprimé (ex. depuis un autre appareil).
    func observeAccountDetail(id: EntityID) -> AsyncStream<AccountDetail?>
}
