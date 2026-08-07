package com.arzikina.ne.domain.repository

/**
 * Verrou biométrique optionnel (empreinte digitale / reconnaissance faciale),
 * envisagé en COMPLÉMENT d'une session déjà active — PAS un mécanisme
 * d'authentification à part entière : il ne crée ni ne vérifie jamais de mot
 * de passe, il ne fait que reconfirmer que la personne actuellement devant
 * l'appareil est bien celle dont la session est active dans [SessionManager].
 *
 * Point d'intégration prévu (non câblé aujourd'hui) : si l'utilisateur active
 * cette option depuis l'écran Profil (réglage non encore présent), l'Activity
 * principale appellerait [authenticate] avant d'afficher le Dashboard à
 * chaque lancement où une session existe déjà (voir
 * `MainActivity.resolveStartDestination`), à la place d'un accès direct.
 *
 * NON implémenté aujourd'hui (aucune classe n'implémente cette interface,
 * volontairement — voir instructions projet, "anticiper sans complexifier
 * maintenant"). Ne pas ajouter de binding Hilt tant qu'une implémentation
 * réelle n'existe pas. Une future implémentation utiliserait
 * `androidx.biometric` (`BiometricPrompt`), dépendance non ajoutée au projet
 * tant que cette fonctionnalité n'est pas développée.
 */
interface BiometricAuthenticator {

    /** `false` si le matériel ne supporte pas la biométrie ou si l'utilisateur n'en a enregistré aucune. */
    suspend fun isAvailable(): Boolean

    /**
     * `true` si la vérification biométrique a réussi. Ne lève jamais
     * d'exception pour un refus ou un échec utilisateur (empreinte non
     * reconnue, annulation...) — seule une erreur technique imprévue le
     * ferait, comme le reste des interfaces de ce module (voir [AuthResult][com.arzikina.ne.domain.model.AuthResult]
     * pour le principe équivalent côté authentification locale).
     */
    suspend fun authenticate(): Boolean
}
