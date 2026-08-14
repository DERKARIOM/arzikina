package com.arzikina.ne.domain.repository

import androidx.fragment.app.FragmentActivity

/**
 * Verrou biométrique optionnel (empreinte digitale / reconnaissance faciale),
 * utilisé en COMPLÉMENT d'une session déjà active — PAS un mécanisme
 * d'authentification à part entière : il ne crée ni ne vérifie jamais de mot
 * de passe, il ne fait que reconfirmer que la personne actuellement devant
 * l'appareil est bien celle dont la session est active dans [SessionManager].
 *
 * Points d'intégration (voir [com.arzikina.ne.data.security.BiometricAuthenticatorImpl]) :
 * activation depuis l'écran Profil (`UserPreferences.biometricLockEnabled`), vérification par
 * `MainActivity` avant d'afficher le Dashboard à chaque lancement où une session existe déjà (voir
 * `MainActivity.resolveStartDestination`) et à chaque retour au premier plan, et réauthentification
 * ponctuelle avant certaines actions sensibles (révélation carte/CVV, export/import de sauvegarde).
 *
 * EXCEPTION DÉLIBÉRÉE à la règle "le domaine n'importe jamais de type Android" (respectée partout
 * ailleurs dans ce module, y compris toutes les autres interfaces de `domain/repository`) :
 * [authenticate] prend un [FragmentActivity] en paramètre, parce que `androidx.biometric.BiometricPrompt`
 * (voir l'implémentation) EXIGE structurellement un hôte `FragmentActivity`/`Fragment` pour
 * s'attacher à son cycle de vie — il n'existe aucune abstraction plus neutre qui ne serait pas un
 * simple doublon de ce type sans aucun bénéfice réel (sur-ingénierie inutile). Cette interface est
 * la SEULE exception du module ; ne pas généraliser ce pattern à d'autres repositories.
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
     *
     * [host] : l'Activity/Fragment hôte du prompt système (voir la doc de classe ci-dessus).
     */
    suspend fun authenticate(host: FragmentActivity): Boolean
}
