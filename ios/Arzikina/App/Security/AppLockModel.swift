import ArzikinaDomain
import Foundation
import Observation

/// Moyen d'authentification proposé par l'iPhone.
enum DeviceAuthentication: Equatable, Sendable {
    case faceID
    case touchID
    case opticID
    /// Code de l'iPhone seulement (biométrie absente ou désactivée).
    case passcode
}

/// Accès à l'authentification du système (Face ID, Touch ID, code) — protocole pour garder
/// `AppLockModel` testable et indépendant de `LocalAuthentication`.
protocol DeviceAuthenticator: Sendable {
    /// `nil` si l'iPhone n'a ni biométrie ni code : le verrou ne peut alors pas s'appliquer.
    func availableMethod() -> DeviceAuthentication?
    /// `true` si la personne s'est authentifiée (Face ID / Touch ID, avec repli sur le code).
    /// Jamais d'erreur : un refus ou une annulation renvoie `false`.
    func authenticate(reason: String) async -> Bool
}

/// Verrouillage de l'app : état, réglage (par appareil, comme Android) et règles de
/// `AppLockPolicy` appliquées au cycle de vie de l'app.
///
/// L'écran de verrouillage ne s'affiche que par-dessus une session ouverte (voir `RootView`) ;
/// une connexion par mot de passe ne redemande rien.
@MainActor
@Observable
final class AppLockModel {

    /// Réglage NON sensible (un simple choix), par appareil → UserDefaults.
    static let storageKey = "security.appLockEnabled"

    private(set) var isEnabled: Bool
    private(set) var isLocked: Bool
    /// Une demande d'authentification est à l'écran : le cache de confidentialité ne doit pas la
    /// recouvrir (la scène passe « inactive » pendant Face ID).
    private(set) var isAuthenticating = false

    @ObservationIgnored private let authenticator: DeviceAuthenticator
    @ObservationIgnored private let defaults: UserDefaults
    @ObservationIgnored private let now: () -> EpochMillis
    @ObservationIgnored private var backgroundedAt: EpochMillis?

    init(
        authenticator: DeviceAuthenticator,
        defaults: UserDefaults = .standard,
        now: @escaping () -> EpochMillis = { EpochMillis((Date().timeIntervalSince1970 * 1000).rounded()) }
    ) {
        self.authenticator = authenticator
        self.defaults = defaults
        self.now = now
        let enabled = defaults.bool(forKey: Self.storageKey)
        isEnabled = enabled
        isLocked = AppLockPolicy.locksAtLaunch(isEnabled: enabled, isAvailable: authenticator.availableMethod() != nil)
    }

    /// Moyen proposé (pour les libellés « Face ID », « Touch ID »…), `nil` si indisponible.
    var method: DeviceAuthentication? { authenticator.availableMethod() }

    // MARK: - Cycle de vie

    func appEnteredBackground() {
        backgroundedAt = now()
    }

    func appBecameActive() {
        if AppLockPolicy.locksOnReturn(isEnabled: isEnabled, isAvailable: method != nil, backgroundedAt: backgroundedAt, now: now()) {
            isLocked = true
        }
        backgroundedAt = nil
    }

    /// Déconnexion : la prochaine connexion (par mot de passe) n'est pas suivie d'un verrou.
    func sessionEnded() {
        isLocked = false
        backgroundedAt = nil
    }

    // MARK: - Actions

    /// Déverrouille si l'authentification réussit.
    func unlock(reason: String) async {
        guard isLocked, !isAuthenticating else { return }
        // Plus aucun moyen d'authentification (code retiré de l'iPhone) : jamais bloqué.
        guard method != nil else {
            isLocked = false
            return
        }
        isAuthenticating = true
        defer { isAuthenticating = false }
        if await authenticator.authenticate(reason: reason) {
            isLocked = false
        }
    }

    /// Activer demande d'abord une authentification réussie (preuve que la personne pourra
    /// déverrouiller) ; désactiver aussi (sinon n'importe qui tenant l'iPhone déverrouillé
    /// pourrait retirer la protection). Retourne le nouvel état.
    @discardableResult
    func setEnabled(_ enabled: Bool, reason: String) async -> Bool {
        guard enabled != isEnabled, method != nil, !isAuthenticating else { return isEnabled }
        isAuthenticating = true
        defer { isAuthenticating = false }
        guard await authenticator.authenticate(reason: reason) else { return isEnabled }
        isEnabled = enabled
        defaults.set(enabled, forKey: Self.storageKey)
        return isEnabled
    }
}
