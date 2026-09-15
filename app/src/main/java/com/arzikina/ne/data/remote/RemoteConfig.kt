package com.arzikina.ne.data.remote

/**
 * URL de base du serveur Arzikina (API REST de synchronisation, voir `server/api/`).
 *
 * Isolée dans une seule constante, jamais codée en dur ailleurs (voir
 * docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, section 6.4 sur HTTPS) : remplacer cette valeur
 * suffira le jour où le serveur passera derrière un nom de domaine/HTTPS, ou pour pointer vers un
 * environnement de test différent — aucun autre fichier n'a à changer.
 *
 * API PHP déployée sur Hostinger (voir docs/DEPLOIEMENT-HOSTINGER.md) — HTTPS, plus besoin de
 * l'exception de trafic en clair qui pointait vers le serveur LAN de développement (voir
 * `AndroidManifest.xml`/`res/xml/`, supprimés en même temps que ce changement).
 *
 * Migration future vers une valeur configurable (écran Réglages, voir cahier des charges
 * section 12 — "Synchroniser maintenant") toujours prévue, pas encore faite.
 *
 * Doit se terminer par `/` : `SyncAuthApi` (voir `data/remote/api/`) concatène directement cette
 * constante avec le chemin de chaque endpoint (ex. `api/auth/login.php`, sans `/` en tête).
 */
object RemoteConfig {
    const val BASE_URL = "https://api.opal-niger.com/"
}
