import ArzikinaDomain
import Observation

/// État du tableau de bord : suit en continu l'instantané de la base locale (saisies locales et
/// données reçues par la synchronisation) et le met en forme pour les cartes de l'écran.
///
/// Tous les calculs métier sont dans le domaine (`DashboardRules`) et la couche données
/// (`DashboardRepository`) ; ce ViewModel ne fait que présenter.
@MainActor
@Observable
final class DashboardViewModel {

    /// Revenus et dépenses du mois pour UNE devise.
    struct MonthRow: Identifiable, Equatable {
        let currencyCode: String
        let income: MinorUnits
        let expense: MinorUnits

        var id: String { currencyCode }
        var difference: MinorUnits { income - expense }
        /// Part des dépenses dans la barre « dépenses vs revenus » (0…1).
        var expenseShare: Double {
            let total = income + expense
            return total > 0 ? Double(expense) / Double(total) : 0
        }
    }

    private(set) var snapshot: DashboardSnapshot = .empty
    /// `false` jusqu'au premier instantané : évite d'afficher « aucun compte » une fraction de
    /// seconde au lancement.
    private(set) var hasLoaded = false

    var hasAccounts: Bool { !snapshot.accounts.isEmpty }
    var totalBalances: [CurrencyAmount] { snapshot.totalBalances }
    var recentTransactions: [TransactionListItem] { snapshot.recentTransactions }

    /// Une ligne par devise ayant des revenus ou des dépenses ce mois-ci, dans l'ordre des comptes.
    var monthRows: [MonthRow] {
        let income = Dictionary(uniqueKeysWithValues: snapshot.month.income.map { ($0.currencyCode, $0.amountMinor) })
        let expense = Dictionary(uniqueKeysWithValues: snapshot.month.expense.map { ($0.currencyCode, $0.amountMinor) })
        let currencies = DashboardRules.sorted(
            income.merging(expense) { lhs, _ in lhs },
            by: DashboardRules.currencyOrder(of: snapshot.accounts)
        ).map(\.currencyCode)
        let rows = currencies.map { MonthRow(currencyCode: $0, income: income[$0] ?? 0, expense: expense[$0] ?? 0) }
        // Mois sans mouvement : une ligne à zéro dans la devise principale, plutôt qu'une carte vide.
        if rows.isEmpty, let main = snapshot.totalBalances.first?.currencyCode {
            return [MonthRow(currencyCode: main, income: 0, expense: 0)]
        }
        return rows
    }

    /// Suit [repository] pour le mois `[monthStart, monthEnd[` jusqu'à l'annulation de la tâche
    /// (changement de mois ou d'utilisateur, écran quitté).
    func observe(_ repository: DashboardRepository, monthStart: EpochMillis, monthEnd: EpochMillis) async {
        let stream = repository.observeDashboard(
            monthStart: monthStart,
            monthEnd: monthEnd,
            recentLimit: DashboardRules.recentTransactionsLimit
        )
        for await snapshot in stream {
            self.snapshot = snapshot
            hasLoaded = true
        }
    }
}
