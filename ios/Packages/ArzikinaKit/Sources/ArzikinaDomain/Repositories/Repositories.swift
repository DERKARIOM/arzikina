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
    /// Ce que la suppression du compte emporterait (affiché dans la confirmation).
    func deletionImpact(id: EntityID) async throws -> AccountDeletionImpact
    /// Supprime le compte, ses transactions (et leurs frais), ses prêts / emprunts et les
    /// remboursements faits depuis ce compte — tout est synchronisé.
    func delete(id: EntityID) async throws
}

/// Ce que la suppression d'un compte emporterait.
public struct AccountDeletionImpact: Equatable, Sendable {
    /// Transactions dont le compte est la source ou la destination.
    public var transactions: Int
    /// Prêts / emprunts rattachés au compte (supprimés avec leurs remboursements).
    public var loans: Int
    /// Automatisations et modèles qui utilisent le compte : ils ne sont PAS supprimés (pas encore
    /// gérés sur iOS) et ne fonctionneront plus.
    public var automations: Int

    public init(transactions: Int, loans: Int, automations: Int) {
        self.transactions = transactions
        self.loans = loans
        self.automations = automations
    }
}

/// Catégories de l'utilisateur connecté.
public protocol CategoryRepository: Sendable {
    /// Catégories non supprimées (toutes, ou d'un seul [type]), triées par nom.
    func observeCategories(type: TransactionType?) -> AsyncStream<[Category]>
    func category(id: EntityID) async throws -> Category?
    func save(_ category: Category) async throws
    /// Supprime la catégorie SEULEMENT si rien ne l'utilise et si l'app ne la gère pas elle-même
    /// (vérifié dans la même transaction SQL que la suppression).
    @discardableResult
    func delete(id: EntityID) async throws -> CategoryDeletion
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

// MARK: - Liste des transactions

/// Écran « Transactions » : toutes les transactions, mises à jour en continu.
public protocol TransactionLedgerRepository: Sendable {
    /// Transactions non supprimées, hors transactions de frais (portées par leur parente), de
    /// la plus récente à la plus ancienne, avec le solde de leur(s) compte(s) après chacune.
    /// Lu en UNE transaction SQL : soldes et lignes toujours cohérents entre eux.
    func observeLedger() -> AsyncStream<[TransactionLedgerEntry]>
}

// MARK: - Budgets

/// Budgets de l'utilisateur connecté.
public protocol BudgetRepository: Sendable {
    /// Budgets non supprimés.
    func observeBudgets() -> AsyncStream<[Budget]>
    func save(_ budget: Budget) async throws
    func delete(id: EntityID) async throws
    /// Chaque budget avec ce qui a été dépensé sur sa période (voir `BudgetSummary`), mis à jour à
    /// chaque modification. [today] fixe les périodes récurrentes et les statuts : l'appelant se
    /// réabonne quand le jour change.
    ///
    /// Seules les DÉPENSES de la catégorie comptent, sur les comptes non supprimés, dans la devise
    /// du budget et inclus dans les statistiques personnelles (comme Android).
    func observeSummaries(today: CalendarDay, calendar: Calendar) -> AsyncStream<[BudgetSummary]>
}

// MARK: - Rapports

/// Écran Rapports, mis à jour en continu.
public protocol ReportsRepository: Sendable {
    /// Totaux et répartition sur [period] (jours inclus ; `nil` = période invalide : totaux à
    /// zéro, l'évolution reste calculée), évolution des derniers mois jusqu'à celui de [today].
    /// Même périmètre qu'Android : comptes inclus dans les statistiques, dans la devise des
    /// rapports (`Reports.currencyCode`).
    func observeReport(
        period: (start: CalendarDay, end: CalendarDay)?,
        breakdownType: BreakdownType,
        today: CalendarDay,
        calendar: Calendar
    ) -> AsyncStream<ReportSnapshot>
}

// MARK: - Prêts et emprunts

/// Détail d'un prêt / emprunt et ses remboursements (du plus récent au plus ancien).
public struct LoanDetail: Equatable, Sendable {
    public var summary: LoanSummary
    public var account: Account?
    public var payments: [LoanPayment]

    public init(summary: LoanSummary, account: Account?, payments: [LoanPayment]) {
        self.summary = summary
        self.account = account
        self.payments = payments
    }
}

/// Prêts, emprunts, remboursements et personnes de l'utilisateur connecté.
///
/// Chaque écriture se fait en UNE transaction SQL avec les transactions Arzikina qu'elle crée ou
/// supprime (décaissement, remboursements), toutes inscrites dans la file d'envoi.
public protocol LoanRepository: Sendable {
    /// Personnes non supprimées, triées par nom.
    func observePersons() -> AsyncStream<[Person]>
    func savePerson(_ person: Person) async throws
    /// Prêts / emprunts non supprimés, du plus récent au plus ancien ; statut calculé à [now].
    func observeSummaries(now: EpochMillis, calendar: Calendar) -> AsyncStream<[LoanSummary]>
    /// `nil` si le prêt n'existe pas ou a été supprimé.
    func observeDetail(id: EntityID, now: EpochMillis, calendar: Calendar) -> AsyncStream<LoanDetail?>
    /// Crée le prêt / emprunt, sa transaction de décaissement (dans la catégorie système du
    /// prêt, recréée si besoin) et son premier remboursement éventuel. Retourne le prêt enregistré.
    @discardableResult
    func create(_ loan: Loan, firstPayment: LoanPayment?) async throws -> Loan
    /// Met à jour un prêt existant (personne, compte, montant, dates, description) et sa
    /// transaction de décaissement. Le type n'est jamais modifié.
    func update(_ loan: Loan) async throws
    /// Supprime le prêt / emprunt, ses remboursements et toutes leurs transactions.
    func delete(id: EntityID) async throws
    /// Enregistre un remboursement et sa transaction (sens inverse du décaissement).
    func recordPayment(_ payment: LoanPayment) async throws
    /// Supprime un remboursement et sa transaction.
    func deletePayment(id: EntityID) async throws
    /// Transforme le reste dû en cadeau (voir `LoanGift`) : reclassement du décaissement et
    /// transaction « Cadeaux » de [description], sans aucun mouvement d'argent. Retourne
    /// l'identifiant de la transaction cadeau.
    @discardableResult
    func convertToGift(loanId: EntityID, description: String) async throws -> EntityID
}

/// Écriture refusée par la base (état changé entre la saisie et l'enregistrement, par exemple
/// par une synchronisation).
public enum LoanWriteError: Error, Equatable, Sendable {
    case loanNotFound
    case amountExceedsRemaining
    case amountBelowRepaid
    /// Prêt transformé en cadeau : remboursements, montant, compte et personne verrouillés.
    case loanGifted
    /// Transformation en cadeau impossible : dette déjà remboursée ou déjà offerte (par exemple
    /// sur un autre appareil depuis l'affichage).
    case notConvertible
}

// MARK: - Modèles de transactions

/// Modèles de transactions (Android « Marketplace personnelle ») : raccourcis vers une
/// transaction pré-remplie. Utiliser un modèle ne le modifie jamais.
public protocol TransactionTemplateRepository: Sendable {
    /// Modèles non supprimés : favoris en tête, puis par nom.
    func observeTemplates() -> AsyncStream<[TransactionTemplate]>
    func template(id: EntityID) async throws -> TransactionTemplate?
    /// Modèle actif créé à partir de [transactionId] (« Voir le modèle »), `nil` sinon.
    func template(createdFromTransaction transactionId: EntityID) async throws -> TransactionTemplate?
    /// Crée ou met à jour un modèle (`sourceTransactionId` n'est retenu qu'à la création). Refuse
    /// un second modèle pour la même transaction (`TemplateWriteError.alreadyLinked`).
    func save(_ template: TransactionTemplate) async throws
    /// Copie de [id] nommée [name], jamais favorite ni liée à une transaction. Retourne son id.
    @discardableResult
    func duplicate(id: EntityID, name: String) async throws -> EntityID
    func setFavorite(id: EntityID, isFavorite: Bool) async throws
    /// Suppression douce ; les transactions déjà créées à partir du modèle ne changent pas.
    func delete(id: EntityID) async throws
}

/// Écriture refusée sur un modèle.
public enum TemplateWriteError: Error, Equatable, Sendable {
    case templateNotFound
    /// La transaction a déjà un modèle (créé entre-temps, ou sur un autre appareil).
    case alreadyLinked(existingTemplateId: EntityID)
}

// MARK: - Automatisations

/// Automatisations (transactions récurrentes) et leurs échéances.
public protocol RecurringRepository: Sendable {
    /// Règles, échéances à traiter, à venir et historique, mis à jour en continu.
    func observeOverview() -> AsyncStream<AutomationOverview>
    /// Fusionne les échéances en double reçues d'autres appareils, puis crée les échéances dues à
    /// [now]. Retourne le nombre d'échéances créées (à envoyer au serveur).
    @discardableResult
    func generateDueOccurrences(now: EpochMillis, calendar: Calendar) async throws -> Int
    /// Valide une échéance en attente : crée la transaction de la règle.
    func accept(occurrenceId: EntityID) async throws
    /// Rejette une échéance en attente (aucune transaction).
    func reject(occurrenceId: EntityID) async throws
    /// Met en pause ou réactive une règle.
    func setActive(ruleId: EntityID, isActive: Bool) async throws
    /// Règle enregistrée, `nil` si elle n'existe pas (ou plus).
    func rule(id: EntityID) async throws -> RecurringTransaction?
    /// Crée ou modifie une règle (voir `AutomationForm.ruleToStore` pour ce qui est conservé).
    func save(_ rule: RecurringTransaction) async throws
    /// Ce que [delete] emporterait.
    func deletionImpact(ruleId: EntityID) async throws -> AutomationDeletionImpact
    /// Supprime la règle et ses échéances ; les transactions déjà créées par elle sont supprimées
    /// seulement si [deleteCreatedTransactions].
    func delete(ruleId: EntityID, deleteCreatedTransactions: Bool) async throws
    /// Valide une échéance en attente avec une transaction modifiée (la règle ne change pas).
    func acceptWithChanges(occurrenceId: EntityID, transaction: Transaction) async throws
}

/// Action refusée : l'échéance a déjà été traitée (par exemple sur un autre appareil).
public enum RecurringWriteError: Error, Equatable, Sendable {
    case occurrenceNotPending
    case ruleNotFound
}

