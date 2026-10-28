package com.naniger.arzikina.domain.model

/** Ce qu'il faut créer, côté appareil, pour un utilisateur local qui vient d'être créé. */
sealed interface SeedDecision {
    /** Rien : les données viennent de la synchronisation (autre appareil) ou existent déjà. */
    data object None : SeedDecision

    /** Comptes par défaut du [mode] choisi (voir [DefaultAccountPlan]). */
    data class ForMode(val mode: UsageMode) : SeedDecision
}

/**
 * Décide si, et quoi, créer localement quand un utilisateur local vient d'être créé. Objet PUR, testable seul.
 *
 * - **Inscription** : on crée selon le mode choisi. Sans mode, on ne crée AUCUN compte : jamais de comptes
 *   inventés à la place de l'utilisateur.
 * - **Connexion** à un compte serveur existant : on ne crée rien, SAUF reprise d'une inscription interrompue,
 *   reconnaissable à : le serveur connaît un mode ET n'a encore AUCUN compte pour cet utilisateur
 *   (utilisateur créé sur le serveur, jamais configuré). Un utilisateur qui a déjà eu des comptes, même
 *   supprimés, n'en reçoit jamais de nouveaux : pas de recréation à la reconnexion ni à la synchronisation.
 * - Utilisateur sans mode (inscrit avant ce choix, ou depuis le web) : jamais de création à la connexion.
 */
object OnboardingSeedingPolicy {

    fun decide(
        isRegistration: Boolean,
        registrationMode: UsageMode?,
        sessionMode: UsageMode?,
        hasServerAccounts: Boolean
    ): SeedDecision = when {
        isRegistration -> registrationMode?.let { SeedDecision.ForMode(it) } ?: SeedDecision.None
        sessionMode != null && !hasServerAccounts -> SeedDecision.ForMode(sessionMode)
        else -> SeedDecision.None
    }

    /** Mode à conserver sur l'utilisateur local : celui choisi à l'inscription, sinon celui connu du serveur. */
    fun effectiveMode(registrationMode: UsageMode?, sessionMode: UsageMode?): UsageMode? =
        registrationMode ?: sessionMode
}
