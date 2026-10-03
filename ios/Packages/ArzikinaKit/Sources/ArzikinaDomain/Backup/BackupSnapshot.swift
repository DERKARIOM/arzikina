import Foundation

/// Données de l'utilisateur à exporter : lignes NON supprimées de la base locale, lues dans une
/// seule transaction de lecture (instantané cohérent, voir `LocalBackupRepository`).
public struct BackupSnapshot: Equatable, Sendable {
    public var accounts: [Account]
    public var categories: [Category]
    public var transactions: [Transaction]
    public var budgets: [Budget]
    public var persons: [Person]
    public var loans: [Loan]
    public var loanPayments: [LoanPayment]
    public var recurringRules: [RecurringTransaction]
    public var occurrences: [RecurringTransactionOccurrence]
    public var plans: [FinancialPlan]
    public var planItems: [FinancialPlanItem]
    public var templates: [TransactionTemplate]
    /// Préférences synchronisées (`user_preferences`), `nil` si jamais reçues du serveur.
    public var themeMode: String?
    public var currencyCode: String?

    public init(
        accounts: [Account] = [],
        categories: [Category] = [],
        transactions: [Transaction] = [],
        budgets: [Budget] = [],
        persons: [Person] = [],
        loans: [Loan] = [],
        loanPayments: [LoanPayment] = [],
        recurringRules: [RecurringTransaction] = [],
        occurrences: [RecurringTransactionOccurrence] = [],
        plans: [FinancialPlan] = [],
        planItems: [FinancialPlanItem] = [],
        templates: [TransactionTemplate] = [],
        themeMode: String? = nil,
        currencyCode: String? = nil
    ) {
        self.accounts = accounts
        self.categories = categories
        self.transactions = transactions
        self.budgets = budgets
        self.persons = persons
        self.loans = loans
        self.loanPayments = loanPayments
        self.recurringRules = recurringRules
        self.occurrences = occurrences
        self.plans = plans
        self.planItems = planItems
        self.templates = templates
        self.themeMode = themeMode
        self.currencyCode = currencyCode
    }
}

/// Bilan d'un export, montré à la personne (« 3 comptes, 412 transactions… ») — Android
/// `BackupResult` (sans reçus ni anciens objectifs d'épargne, absents d'iOS).
public struct BackupSummary: Equatable, Sendable {
    public var accounts = 0
    public var categories = 0
    public var transactions = 0
    public var budgets = 0
    public var loans = 0
    public var automations = 0
    public var plans = 0
    public var templates = 0
    /// Lignes laissées de côté parce qu'elles désignent un élément absent (supprimé ailleurs, pas
    /// encore reçu) : un fichier Android les refuserait, voir `AndroidBackup`.
    public var skipped = 0

    public init() {}
}

/// Accès en lecture aux données à sauvegarder.
public protocol BackupRepository: Sendable {
    func snapshot() async throws -> BackupSnapshot
}
