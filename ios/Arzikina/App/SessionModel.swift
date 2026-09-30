import ArzikinaDomain
import Observation

/// État de connexion de l'application, partagé par tous les écrans (`.environment`).
///
/// Décide de ce que montre `RootView` : écran de démarrage le temps de relire la session, parcours
/// de connexion, ou application principale.
@MainActor
@Observable
final class SessionModel {

    enum State: Equatable {
        /// Lecture de la session enregistrée (quasi instantanée, sans réseau).
        case restoring
        case signedOut
        case signedIn(AuthSession)
    }

    private(set) var state: State = .restoring

    @ObservationIgnored
    let authRepository: AuthRepository

    init(authRepository: AuthRepository) {
        self.authRepository = authRepository
    }

    var currentSession: AuthSession? {
        if case .signedIn(let session) = state { return session }
        return nil
    }

    /// Au lancement : reprend la session enregistrée si elle est encore valide (fonctionne hors
    /// ligne).
    func restore() async {
        guard state == .restoring else { return }
        if let session = await authRepository.restoreSession() {
            state = .signedIn(session)
        } else {
            state = .signedOut
        }
    }

    /// Appelé par les écrans de connexion/inscription une fois le compte authentifié.
    func didAuthenticate(_ session: AuthSession) {
        state = .signedIn(session)
    }

    func logout() async {
        await authRepository.logout()
        state = .signedOut
    }
}
