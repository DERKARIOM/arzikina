package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.SyncEngineResult
import com.arzikina.ne.domain.model.SyncPullResult

/**
 * Synchronise la file d'attente locale (`sync_queue`, voir `data/local/entity/SyncQueueEntity.kt`)
 * avec le serveur de synchronisation, dans les deux sens — voir
 * docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, section 8 (envoi) et section 10 (réception).
 *
 * ÉTAPE ACTUELLE — voir `SyncEngineImpl` : déclenché manuellement (bouton "Synchroniser
 * maintenant", voir `SettingsViewModel.syncNow`), rien d'automatique encore (pas de WorkManager, pas
 * de déclenchement sur connectivité), et seul `categories` est traité des deux côtés. Le
 * déclenchement automatique et les entités restantes suivront dans des étapes dédiées séparées.
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

    /**
     * Reçoit et applique localement les changements distants (autres appareils, ou données
     * antérieures à l'installation courante) survenus depuis le dernier pull réussi (voir
     * `SyncCursorStore`, `SyncApi.pull`) — pull INCRÉMENTAL, jamais un dump complet. À appeler
     * APRÈS [pushPendingChanges] (voir `SettingsViewModel.syncNow`) : les propres modifications de
     * cet appareil doivent être envoyées et confirmées avant de recevoir celles des autres, pour
     * un état final cohérent plus rapidement (les deux convergent de toute façon vers le même état
     * quel que soit l'ordre, voir `push.php`/`pull.php`, mais éviter un aller-retour inutile).
     */
    suspend fun pullRemoteChanges(): SyncPullResult
}
