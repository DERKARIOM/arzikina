import ArzikinaDomain
import Foundation
import Observation

/// État du détail d'un compte : son solde et ses transactions groupées par jour (du plus récent au
/// plus ancien), en continu.
@MainActor
@Observable
final class AccountDetailViewModel {

    enum State: Equatable {
        case loading
        case loaded(AccountDetail)
        /// Compte introuvable : supprimé (ex. depuis un autre appareil, reçu par synchronisation).
        case missing
    }

    private(set) var state: State = .loading
    /// Transactions groupées par jour, recalculées à chaque nouvel instantané.
    private(set) var sections: [DayGrouping.Section<TransactionListItem>] = []

    func observe(_ repository: AccountOverviewRepository, accountId: EntityID, calendar: Calendar) async {
        for await detail in repository.observeAccountDetail(id: accountId) {
            guard let detail else {
                state = .missing
                sections = []
                continue
            }
            state = .loaded(detail)
            sections = DayGrouping.group(detail.transactions, calendar: calendar) { $0.transaction.date }
        }
    }
}
