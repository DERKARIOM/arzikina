package com.arzikina.ne.domain.model

/**
 * Statut d'une entrée de la file de synchronisation locale (voir
 * `data/local/entity/SyncQueueEntity.kt`, section 8 de docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md).
 *
 * Cycle de vie : PENDING → (le Sync Engine prend le lot) → SYNCING → SYNCED (succès, ligne
 * conservée un temps pour audit puis purgée) ou FAILED (échec réseau/serveur → nouvelle tentative
 * avec recul exponentiel — voir la future implémentation du Sync Engine — jamais abandonné
 * silencieusement, juste espacé).
 */
enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED
}
