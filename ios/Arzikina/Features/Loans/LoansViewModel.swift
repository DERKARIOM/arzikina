import ArzikinaDomain
import Foundation
import Observation

/// Écran « Prêts et emprunts » : résumé (reste à recevoir / à rembourser), filtres, recherche —
/// Android `LoansViewModel`. Le résumé suit les filtres, comme Android.
@MainActor
@Observable
final class LoansViewModel {

    var filters = LoanFilters()
    private(set) var summaries: [LoanSummary] = []
    private(set) var hasLoaded = false

    var visible: [LoanSummary] { LoanList.apply(summaries, filters: filters) }

    func observe(_ repository: LoanRepository, now: EpochMillis, calendar: Calendar) async {
        for await summaries in repository.observeSummaries(now: now, calendar: calendar) {
            self.summaries = summaries
            hasLoaded = true
        }
    }
}
