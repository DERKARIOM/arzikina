import ArzikinaDomain
import Foundation
import Observation

/// Écran « Budgets » : chaque budget avec sa progression, filtrable par statut — Android
/// `BudgetViewModel`. La progression n'est jamais stockée : la base la recalcule à chaque
/// modification (`BudgetRepository.observeSummaries`).
@MainActor
@Observable
final class BudgetsViewModel {

    var filter: BudgetStatusFilter = .all
    private(set) var summaries: [BudgetSummary] = []
    private(set) var hasLoaded = false

    /// Budgets du filtre : en cours d'abord, puis à venir, puis terminés ; à statut égal, la
    /// progression la plus élevée d'abord (le plus urgent en haut).
    var visibleSummaries: [BudgetSummary] {
        summaries
            .filter { filter.matches($0.periodStatus) }
            .sorted { lhs, rhs in
                let left = Self.rank(lhs.periodStatus), right = Self.rank(rhs.periodStatus)
                return left != right ? left < right : lhs.progress > rhs.progress
            }
    }

    func observe(_ repository: BudgetRepository, today: CalendarDay, calendar: Calendar) async {
        for await summaries in repository.observeSummaries(today: today, calendar: calendar) {
            self.summaries = summaries
            hasLoaded = true
        }
    }

    /// `false` en cas d'échec d'écriture.
    func delete(_ budget: Budget, using repository: BudgetRepository) async -> Bool {
        do {
            try await repository.delete(id: budget.id)
            return true
        } catch {
            return false
        }
    }

    private static func rank(_ status: BudgetPeriodStatus?) -> Int {
        switch status {
        case .ongoing, nil: return 0
        case .upcoming: return 1
        case .completed: return 2
        }
    }
}
