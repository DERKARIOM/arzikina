import ArzikinaData
import ArzikinaDomain
import Observation

/// État de connexion de l'application, partagé par tous les écrans (`.environment`).
///
/// Décide de ce que montre `RootView` : écran de démarrage le temps de relire la session, parcours
/// de connexion, ou application principale. Ouvre aussi la base locale de l'utilisateur connecté
/// ([dataSpace]) et sa synchronisation ([sync]), et les ferme à la déconnexion.
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
    /// Données locales de l'utilisateur connecté (`nil` hors connexion).
    private(set) var dataSpace: UserDataSpace?
    /// `false` si la base locale n'a pas pu être ouverte (données non conservées).
    private(set) var isDataPersistent = true
    /// Synchronisation de [dataSpace] avec le serveur (`nil` hors connexion ou dans les aperçus).
    private(set) var sync: SyncCoordinator?
    /// `true` après une déconnexion causée par un jeton refusé par le serveur : l'écran de
    /// connexion explique pourquoi (les données locales sont conservées).
    private(set) var sessionExpired = false

    @ObservationIgnored
    let authRepository: AuthRepository
    @ObservationIgnored
    private let openDataSpace: (AuthSession) -> (space: UserDataSpace, isPersistent: Bool)
    @ObservationIgnored
    private let makeSyncEngine: (UserDataSpace) -> SyncEngine?

    init(
        authRepository: AuthRepository,
        openDataSpace: @escaping (AuthSession) -> (space: UserDataSpace, isPersistent: Bool),
        makeSyncEngine: @escaping (UserDataSpace) -> SyncEngine? = { _ in nil }
    ) {
        self.authRepository = authRepository
        self.openDataSpace = openDataSpace
        self.makeSyncEngine = makeSyncEngine
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
            signIn(session)
        } else {
            state = .signedOut
        }
    }

    /// Appelé par l'écran de connexion une fois le compte authentifié.
    func didAuthenticate(_ session: AuthSession) {
        signIn(session)
    }

    /// Appelé par l'écran d'inscription : le compte serveur est NEUF, ses comptes et catégories
    /// par défaut sont créés (comme Android). Jamais à la connexion, où ils viendraient en double
    /// de la synchronisation.
    func didRegister(_ session: AuthSession) {
        signIn(session, isNewAccount: true)
    }

    /// Ferme la base locale (le fichier reste sur l'iPhone) et oublie la session.
    func logout() async {
        await authRepository.logout()
        closeSpace()
        state = .signedOut
    }

    /// Le serveur refuse le jeton (expiré ou révoqué) : retour à la connexion. Les données
    /// locales, y compris les modifications pas encore envoyées, restent sur l'iPhone et partiront
    /// à la prochaine synchronisation après reconnexion avec le même compte.
    func handleSessionExpired() async {
        guard currentSession != nil else { return }
        await logout()
        sessionExpired = true
    }

    /// L'app revient au premier plan.
    func appDidBecomeActive() {
        sync?.requestSync(.automatic)
    }

    /// Supprime la base locale de l'utilisateur connecté et en recrée une vide.
    func clearLocalData() throws {
        guard let session = currentSession, let space = dataSpace else { return }
        stopSync()
        dataSpace = nil
        try space.closeAndErase()
        openSpace(for: session)
    }

    private func signIn(_ session: AuthSession, isNewAccount: Bool = false) {
        sessionExpired = false
        if dataSpace?.userId != session.userId {
            closeSpace()
            openSpace(for: session, seedingDefaults: isNewAccount)
        }
        state = .signedIn(session)
    }

    private func openSpace(for session: AuthSession, seedingDefaults: Bool = false) {
        let opened = openDataSpace(session)
        if seedingDefaults {
            // Avant le démarrage de la synchronisation : le premier envoi les contient. Un échec
            // n'empêche pas d'utiliser l'app (l'utilisateur peut créer ses comptes lui-même).
            _ = try? opened.space.seedDefaultDataForNewAccount()
        }
        dataSpace = opened.space
        isDataPersistent = opened.isPersistent
        if let engine = makeSyncEngine(opened.space) {
            let coordinator = SyncCoordinator(engine: engine) { [weak self] in
                Task { await self?.handleSessionExpired() }
            }
            sync = coordinator
            coordinator.start()
        }
    }

    private func closeSpace() {
        stopSync()
        dataSpace?.close()
        dataSpace = nil
    }

    private func stopSync() {
        sync?.stop()
        sync = nil
    }
}
