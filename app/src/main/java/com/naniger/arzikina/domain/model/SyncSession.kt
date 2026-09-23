package com.naniger.arzikina.domain.model

/**
 * Vue domaine d'une session active sur le serveur de synchronisation : identifie SI l'appareil
 * est connecté et JUSQU'À QUAND, sans jamais exposer le token lui-même.
 *
 * Le token brut ne quitte JAMAIS la couche data (voir `data/security/TokenCipher.kt` et
 * `data/repository/SyncAuthRepositoryImpl.kt`) : ni le domaine ni la présentation n'en ont
 * besoin — seul un futur intercepteur réseau (couche data) doit le lire pour l'en-tête
 * `Authorization`. Ce type est ce que l'UI (ex. indicateur de statut de synchronisation, écran
 * Réglages) peut afficher sans risque : "connecté en tant que [serverUserId], jusqu'à
 * [expiresAt]".
 */
data class SyncSession(
    val serverUserId: String,
    val expiresAt: Long,
    /** Nom complet réel (`users.full_name` côté serveur) — utile à
     *  [com.naniger.arzikina.data.repository.UnifiedAuthRepositoryImpl] pour créer/afficher le profil
     *  LOCAL correspondant, sans avoir à rappeler le serveur une seconde fois pour l'obtenir. */
    val fullName: String
)
