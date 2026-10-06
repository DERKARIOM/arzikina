package com.naniger.arzikina.domain.model

/**
 * Résultat d'une tentative d'enregistrement de l'appareil pour les notifications push (voir
 * `PushRegistrationRepository.registerNow`). Piloté par la tâche WorkManager : seul [Retry]
 * provoque un nouvel essai.
 */
sealed interface PushRegistrationOutcome {
    /** Appareil enregistré (ou déjà à jour : rien à envoyer). */
    data object Registered : PushRegistrationOutcome

    /** Aucune session serveur valide : rien à faire, l'enregistrement aura lieu après la connexion. */
    data object NotSignedIn : PushRegistrationOutcome

    /** Échec temporaire (réseau, serveur indisponible, token Firebase pas encore disponible). */
    data object Retry : PushRegistrationOutcome

    /** Refus définitif du serveur (requête invalide) : inutile de réessayer avec les mêmes données. */
    data object Rejected : PushRegistrationOutcome
}
