package com.naniger.arzikina.domain.versioning

/**
 * Accès à la politique de version du serveur. Implémentation : `data/versioning`. Prévu pour
 * rester valable quand le backend évoluera (Firebase Remote Config, autre REST) : seul ce contrat
 * est connu du domaine.
 */
interface VersionPolicyRepository {

    /** Dernière politique reçue et conservée sur l'appareil, `null` si aucune. Ne touche pas au réseau. */
    suspend fun cachedPolicy(): AppVersionPolicy?

    /**
     * Interroge le serveur (endpoint public, jamais bloqué par la politique) et conserve le
     * résultat. `null` si le serveur est injoignable ou répond de façon inexploitable : l'appelant
     * se rabat alors sur [cachedPolicy].
     */
    suspend fun fetchPolicy(): AppVersionPolicy?
}
