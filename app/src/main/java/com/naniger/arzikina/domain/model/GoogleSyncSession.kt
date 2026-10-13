package com.naniger.arzikina.domain.model

/**
 * Résultat d'une connexion Google réussie côté serveur de synchronisation (voir
 * [com.naniger.arzikina.domain.repository.SyncAuthRepository.loginWithGoogle]) — un [SyncSession]
 * classique ENRICHI des deux informations que seule la réponse de `google_login.php` porte (voir
 * [com.naniger.arzikina.data.remote.dto.GoogleLoginResponseDto] pour le détail de chacune) :
 * [email] (déjà vérifié par le serveur) et [isNewAccount] (le compte vient-il d'être créé ?).
 *
 * Volontairement un type SÉPARÉ plutôt qu'un ajout de champs sur [SyncSession] lui-même : ces deux
 * informations n'ont de sens QUE pour une connexion Google (`login()`/`register()` classiques n'en
 * ont jamais besoin, l'appelant connaît déjà l'e-mail saisi et sait déjà lequel des deux il
 * appelle) — les ajouter à [SyncSession] aurait élargi un type partagé pour un seul appelant.
 */
data class GoogleSyncSession(
    val session: SyncSession,
    val email: String,
    val isNewAccount: Boolean
)
