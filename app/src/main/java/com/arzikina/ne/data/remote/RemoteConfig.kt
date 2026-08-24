package com.arzikina.ne.data.remote

/**
 * URL de base du serveur Arzikina (API REST de synchronisation, voir `server/api/`).
 *
 * Isolée dans une seule constante, jamais codée en dur ailleurs (voir
 * docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, section 6.4 sur HTTPS) : remplacer cette valeur
 * suffira le jour où le serveur passera derrière un nom de domaine/HTTPS, ou pour pointer vers un
 * environnement de test différent — aucun autre fichier n'a à changer.
 *
 * TEMPORAIRE : constante en dur pointant vers le serveur LAN de développement. Migrera vers une
 * valeur configurable (écran Réglages, voir cahier des charges section 12 — "Synchroniser
 * maintenant") dans une étape ultérieure du chantier — volontairement pas fait ici pour ne pas
 * mélanger deux préoccupations (fondation réseau vs. UI de configuration) dans la même étape.
 *
 * Doit se terminer par `/` : `SyncAuthApi` (voir `data/remote/api/`) concatène directement cette
 * constante avec le chemin de chaque endpoint (ex. `api/auth/login.php`, sans `/` en tête).
 */
object RemoteConfig {
    const val BASE_URL = "http://192.168.49.1:2222/arzikina/"
}
