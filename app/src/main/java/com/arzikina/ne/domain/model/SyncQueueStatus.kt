package com.arzikina.ne.domain.model

/**
 * Photographie EN CONTINU de l'état de `sync_queue` (voir [com.arzikina.ne.domain.repository.SyncEngine.observeQueueStatus]),
 * pour le futur indicateur visuel de synchronisation (écran Paramètres). Contrairement à
 * [SyncEngineResult] (bilan PONCTUEL d'une exécution passée), ceci reflète l'état ACTUEL de la
 * file, mis à jour en direct — aussi bien après une synchronisation manuelle
 * ([com.arzikina.ne.presentation.settings.SettingsViewModel.syncNow]) qu'après un déclenchement
 * automatique en arrière-plan (voir `work/SyncWorker.kt`).
 *
 * Simples décomptes, pas la liste détaillée des entrées : suffisant pour l'indicateur visé, sans
 * exposer `SyncQueueEntity` (type de la couche DATA) jusqu'à la Présentation — même raisonnement
 * que [SyncEngineResult].
 */
data class SyncQueueStatus(
    val pending: Int,
    val syncing: Int,
    val failed: Int
)
