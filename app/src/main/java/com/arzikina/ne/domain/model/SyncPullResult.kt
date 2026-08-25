package com.arzikina.ne.domain.model

/**
 * Bilan d'une exécution de [com.arzikina.ne.domain.repository.SyncEngine.pullRemoteChanges] — voir
 * cette interface. [received] : nombre total de lignes reçues du serveur (tous lots confondus).
 * [applied] : parmi elles, celles effectivement appliquées localement ([received] - [applied] =
 * lignes ignorées, ex. JSON malformé — voir `SyncEngineImpl`, ne bloque jamais le reste du lot).
 */
data class SyncPullResult(
    val received: Int,
    val applied: Int
)
