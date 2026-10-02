import Observation

/// Navigation demandée de l'extérieur des écrans (toucher d'une notification, et plus tard
/// widgets ou liens profonds) : l'écran concerné l'exécute puis la consomme.
@MainActor
@Observable
final class AppRouter {

    enum Destination: Equatable {
        /// Écran Automatisations (depuis l'onglet Accueil).
        case automations
    }

    private(set) var pendingDestination: Destination?

    func open(_ destination: Destination) {
        pendingDestination = destination
    }

    /// Appelé par l'écran qui a affiché [destination].
    func consume(_ destination: Destination) {
        if pendingDestination == destination { pendingDestination = nil }
    }
}
