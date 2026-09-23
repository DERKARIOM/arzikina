package com.naniger.arzikina.domain.model

/**
 * Bilan d'une exécution de [com.naniger.arzikina.domain.repository.SyncEngine.pushPendingChanges] —
 * voir cette interface pour le raisonnement. Volontairement un simple décompte (pas la liste
 * détaillée des entrées) : suffisant pour un futur indicateur visuel ("3 en attente", "1 erreur",
 * voir docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md) sans exposer `SyncQueueEntity` (type de la couche
 * DATA) jusqu'à la Présentation.
 */
data class SyncEngineResult(
    val pushed: Int,
    val succeeded: Int,
    val failed: Int
)
