package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.SyncEngineResult

/**
 * Vide la file d'attente locale (`sync_queue`, voir `data/local/entity/SyncQueueEntity.kt`) vers le
 * serveur de synchronisation — voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, section 8.
 *
 * ÉTAPE ACTUELLE — voir `SyncEngineImpl` : rien n'appelle encore [pushPendingChanges]
 * automatiquement (pas de WorkManager, pas de déclenchement sur connectivité) et seul `categories`
 * est traité. Fondation posée à l'avance (même raisonnement que `SyncQueueEntity` en son temps),
 * le déclenchement (bouton manuel d'abord, automatique ensuite) et les entités restantes suivront
 * dans des étapes dédiées séparées.
 */
interface SyncEngine {

    /**
     * Envoie TOUTES les entrées `PENDING` de la file, groupées par type d'entité (le serveur
     * n'accepte qu'un seul `entityType` par appel HTTP — voir `SyncApi.push`), applique l'état
     * confirmé par le serveur sur les lignes locales correspondantes, puis marque chaque entrée
     * `SYNCED` ou `FAILED`. N'échoue jamais bruyamment : une erreur réseau ou serveur sur UNE
     * entrée n'empêche pas le traitement des autres (voir `SyncEngineImpl`).
     */
    suspend fun pushPendingChanges(): SyncEngineResult
}
