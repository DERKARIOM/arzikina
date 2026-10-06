package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.model.PushRegistrationOutcome

/**
 * Enregistrement de CET appareil auprès du serveur Arzikina pour recevoir des notifications push
 * (voir `server/api/devices/register.php`).
 *
 * L'enregistrement réel passe toujours par une tâche WorkManager ([requestRegistration]) : il
 * survit à une absence de réseau, à la fermeture de l'application et réessaie seul. Rien n'est
 * envoyé tant qu'aucune session serveur n'est ouverte ; le token FCM est simplement conservé sur
 * l'appareil en attendant la connexion.
 */
interface PushRegistrationRepository {

    /** Nouveau token FCM fourni par Firebase (première installation, rotation, restauration). */
    suspend fun onNewToken(token: String)

    /**
     * Programme un (ré)enregistrement. Sans effet visible si rien n'a changé depuis le dernier
     * envoi réussi (voir [registerNow]). Appelé au démarrage et après chaque connexion.
     */
    fun requestRegistration()

    /** Exécution réelle, appelée par la tâche WorkManager. */
    suspend fun registerNow(): PushRegistrationOutcome

    /**
     * Déconnexion : annule un enregistrement en attente et oublie le dernier enregistrement réussi
     * (le prochain compte connecté sera réenregistré). La révocation côté serveur est faite par
     * `auth/logout.php`. L'identifiant d'installation et le token FCM sont conservés : ils
     * n'appartiennent à aucun compte.
     */
    suspend fun onSignedOut()
}
