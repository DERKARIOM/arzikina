import ArzikinaDomain
import Foundation
import Observation

/// Écran « Automatisations » — Android `RecurringTransactionsViewModel` : échéances à traiter
/// (valider / rejeter), à venir, historique, et pause des règles.
@MainActor
@Observable
final class AutomationsViewModel {

    private(set) var overview: AutomationOverview = .empty
    private(set) var hasLoaded = false
    /// Échéances dont l'action est en cours (boutons désactivés, pas de double validation).
    private(set) var busyIds: Set<EntityID> = []
    private(set) var actionFailed = false

    /// Historique limité aux plus récentes (le reste reste dans les transactions).
    static let historyLimit = 30

    var isEmpty: Bool { overview.rules.isEmpty && overview.pending.isEmpty && overview.history.isEmpty }

    func observe(_ repository: RecurringRepository) async {
        for await overview in repository.observeOverview() {
            self.overview = overview
            hasLoaded = true
        }
    }

    func accept(_ occurrenceId: EntityID, using repository: RecurringRepository) async -> Bool {
        await perform(occurrenceId) { try await repository.accept(occurrenceId: occurrenceId) }
    }

    func reject(_ occurrenceId: EntityID, using repository: RecurringRepository) async -> Bool {
        await perform(occurrenceId) { try await repository.reject(occurrenceId: occurrenceId) }
    }

    func setActive(_ ruleId: EntityID, _ isActive: Bool, using repository: RecurringRepository) async -> Bool {
        await perform(ruleId) { try await repository.setActive(ruleId: ruleId, isActive: isActive) }
    }

    func dismissFailure() { actionFailed = false }

    private func perform(_ id: EntityID, _ action: () async throws -> Void) async -> Bool {
        guard !busyIds.contains(id) else { return false }
        busyIds.insert(id)
        defer { busyIds.remove(id) }
        do {
            try await action()
            return true
        } catch {
            // Déjà traitée sur un autre appareil, par exemple : la liste se met à jour seule.
            actionFailed = true
            return false
        }
    }
}
