import Foundation

/// Verrouillage de l'app (Face ID / Touch ID / code de l'iPhone) — règles d'Android `MainActivity`
/// (`resolveStartDestination`, `checkBiometricReentryLock`).
///
/// Un COMPLÉMENT de la session, jamais une authentification : il reconfirme que la personne devant
/// l'iPhone est bien celle dont la session est ouverte. Réglage PAR APPAREIL, comme Android.
public enum AppLockPolicy {

    /// Délai de grâce : un aller-retour plus court hors de l'app (répondre à un SMS, copier un
    /// code Mobile Money…) ne redemande rien — Android `BIOMETRIC_REENTRY_GRACE_PERIOD_MILLIS`.
    public static let gracePeriod: EpochMillis = 30_000

    /// Verrouiller au lancement : seulement si le réglage est actif ET que l'iPhone peut encore
    /// authentifier (sinon l'utilisateur serait bloqué hors de ses données).
    public static func locksAtLaunch(isEnabled: Bool, isAvailable: Bool) -> Bool {
        isEnabled && isAvailable
    }

    /// Verrouiller au retour au premier plan, après un passage en arrière-plan à [backgroundedAt].
    public static func locksOnReturn(isEnabled: Bool, isAvailable: Bool, backgroundedAt: EpochMillis?, now: EpochMillis) -> Bool {
        guard isEnabled, isAvailable, let backgroundedAt else { return false }
        // Horloge modifiée (retour en arrière) : on verrouille par prudence.
        guard now >= backgroundedAt else { return true }
        return now - backgroundedAt >= gracePeriod
    }
}
