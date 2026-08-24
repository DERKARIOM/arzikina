package com.arzikina.ne.domain.model

/**
 * Type d'opération en attente dans la file de synchronisation locale (voir
 * `data/local/entity/SyncQueueEntity.kt`) — reflète directement les trois opérations possibles
 * côté API (`server/api/sync/push.php`), voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, section 8.
 */
enum class SyncOperation {
    CREATE,
    UPDATE,
    DELETE
}
