import ArzikinaDomain
import Foundation
import Observation

/// Écran « Transactions » : toutes les transactions groupées par jour, recherche instantanée,
/// filtres et suppression — Android `TransactionsViewModel`.
///
/// La base fournit les lignes et leurs soldes (une lecture SQL par modification) ; la recherche et
/// les filtres s'appliquent EN MÉMOIRE sur ce résultat (`TransactionListFilter`), donc sans
/// relire la base à chaque caractère tapé.
@MainActor
@Observable
final class TransactionsViewModel {

    enum DeletionOutcome: Equatable {
        case deleted
        /// La transaction fait partie d'un prêt : elle se gère depuis le prêt.
        case linkedToLoan
        case failed
    }

    var filters = TransactionFilters() {
        didSet { if filters != oldValue { recompute() } }
    }

    private(set) var accounts: [Account] = []
    private(set) var categories: [ArzikinaDomain.Category] = []
    private(set) var sections: [DayGrouping.Section<TransactionListItem>] = []
    private(set) var hasLoaded = false
    /// `true` s'il existe au moins une transaction, filtres ou non (distingue « aucune
    /// transaction » de « aucun résultat »).
    private(set) var hasTransactions = false

    @ObservationIgnored private var ledger: [TransactionLedgerEntry] = []
    @ObservationIgnored private let calendar: Calendar
    @ObservationIgnored private let today: () -> CalendarDay

    init(calendar: Calendar = ArzikinaCalendar.current, today: (() -> CalendarDay)? = nil) {
        self.calendar = calendar
        self.today = today ?? {
            CalendarDay(epochMillis: EpochMillis(Date().timeIntervalSince1970 * 1000), calendar: calendar)
        }
    }

    // MARK: - Observation

    func observeLedger(_ repository: TransactionLedgerRepository) async {
        for await ledger in repository.observeLedger() {
            self.ledger = ledger
            hasTransactions = !ledger.isEmpty
            recompute()
            hasLoaded = true
        }
    }

    func observeAccounts(_ repository: AccountRepository) async {
        for await accounts in repository.observeAccounts() {
            self.accounts = accounts
            // Compte filtré supprimé entre-temps (ex. depuis un autre appareil) : filtre retiré.
            if let id = filters.accountId, !accounts.contains(where: { $0.id == id }) {
                filters.accountId = nil
            }
        }
    }

    func observeCategories(_ repository: CategoryRepository) async {
        for await categories in repository.observeCategories(type: nil) {
            self.categories = categories
            if let id = filters.categoryId, !categories.contains(where: { $0.id == id }) {
                filters.categoryId = nil
            }
        }
    }

    /// À appeler au retour au premier plan : « cette semaine » / « ce mois » suivent le jour.
    func refreshPeriod() {
        if filters.period != .all { recompute() }
    }

    // MARK: - Suppression

    /// Supprime [transaction] et ses frais ; refusé pour une transaction de prêt.
    func delete(_ transaction: ArzikinaDomain.Transaction, using repository: TransactionRepository) async -> DeletionOutcome {
        do {
            if try await repository.isLinkedToLoan(id: transaction.id) { return .linkedToLoan }
            try await repository.delete(id: transaction.id)
            return .deleted
        } catch {
            return .failed
        }
    }

    // MARK: - Calcul

    private func recompute() {
        let items = TransactionListFilter.apply(
            ledger,
            filters: filters,
            today: today(),
            calendar: calendar,
            names: Self.searchableNames
        )
        sections = DayGrouping.group(items, calendar: calendar) { $0.transaction.date }
    }

    /// Nom affiché (traduit pour un élément par défaut) ET nom enregistré.
    private static let searchableNames = TransactionListFilter.SearchableNames(
        account: { [$0.displayName, $0.name] },
        category: { [$0.displayName, $0.name] }
    )
}
