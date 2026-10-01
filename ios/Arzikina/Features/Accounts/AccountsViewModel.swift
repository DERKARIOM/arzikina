import ArzikinaDomain
import Observation

/// État de l'écran Comptes : les comptes et leur solde, en continu, filtrés par groupe
/// (Comptes / Cartes bancaires / Épargne — Android `AccountsDisplayTab`).
@MainActor
@Observable
final class AccountsViewModel {

    /// Groupe affiché. Non persisté : l'écran s'ouvre toujours sur « Comptes », comme Android.
    var selectedGroup: AccountGroup = .accounts
    private(set) var summaries: [AccountSummary] = []
    private(set) var hasLoaded = false

    /// Comptes du groupe affiché, dans l'ordre d'affichage.
    var visibleSummaries: [AccountSummary] {
        summaries.filter { $0.account.type.group == selectedGroup }
    }

    func observe(_ repository: AccountOverviewRepository) async {
        for await summaries in repository.observeAccountSummaries() {
            self.summaries = summaries
            hasLoaded = true
        }
    }
}
