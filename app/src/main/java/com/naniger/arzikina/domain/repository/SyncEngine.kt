package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.model.SyncEngineResult
import com.naniger.arzikina.domain.model.SyncPullResult
import com.naniger.arzikina.domain.model.SyncQueueStatus
import kotlinx.coroutines.flow.Flow

/**
 * Synchronise la file d'attente locale (`sync_queue`, voir `data/local/entity/SyncQueueEntity.kt`)
 * avec le serveur de synchronisation, dans les deux sens — voir
 * docs/sync/AUDIT-ET-ARCHITECTURE-SYNC.md, section 8 (envoi) et section 10 (réception).
 *
 * ÉTAPE ACTUELLE — voir `SyncEngineImpl` : déclenché manuellement (bouton "Synchroniser
 * maintenant", voir `SettingsViewModel.syncNow`) ET automatiquement (périodique + retour de
 * connectivité, voir `work/SyncWorkScheduler.kt`/`work/SyncConnectivityObserver.kt`). Traite
 * `categories`, `savings_goals`, `financial_plans`, `persons` et `accounts` des deux côtés — voir
 * `SUPPORTED_ENTITY_TYPES` dans `SyncEngineImpl` pour la liste à jour, étendue au fil des étapes
 * dédiées.
 */
interface SyncEngine {

    /**
     * Envoie TOUTES les entrées `PENDING` de la file, plus les entrées `FAILED` déjà éligibles à
     * une nouvelle tentative (délai croissant avec `retryCount`, jamais un abandon définitif — voir
     * la KDoc de `SyncEngineImpl.pushPendingChanges`/`isEligibleForRetry`), groupées par type
     * d'entité (le serveur n'accepte qu'un seul `entityType` par appel HTTP — voir `SyncApi.push`),
     * applique l'état confirmé par le serveur sur les lignes locales correspondantes, puis marque
     * chaque entrée `SYNCED` ou `FAILED`. N'échoue jamais bruyamment : une erreur réseau ou serveur
     * sur UNE entrée n'empêche pas le traitement des autres (voir `SyncEngineImpl`).
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

    /**
     * État EN CONTINU de `sync_queue`, tous types d'entités confondus — voir [SyncQueueStatus].
     * Pour le futur indicateur visuel de synchronisation (écran Paramètres), PAS pour piloter la
     * synchronisation elle-même (ni [pushPendingChanges] ni [pullRemoteChanges] ne s'appuient
     * dessus). Se met à jour en direct après toute exécution de l'un ou l'autre, qu'elle soit
     * déclenchée manuellement ou automatiquement.
     */
    fun observeQueueStatus(): Flow<SyncQueueStatus>

    /**
     * Rattrapage ("backfill") : enfile en `CREATE` toute donnée locale de l'utilisateur COURANT
     * jamais proposée à la synchronisation — voir `CategoryDao.getUnsyncedForUser`/
     * `SavingsGoalDao.getUnsyncedForUser`. Nécessaire car certains chemins d'écriture contournent
     * volontairement les repositories câblés sur `sync_queue` (`NewUserDefaultDataSeeder` à
     * l'inscription, `BackupRepositoryImpl` lors d'une restauration) : leurs lignes existent
     * localement mais n'ont jamais généré d'entrée de file, donc jamais atteint le serveur.
     *
     * Appelé une fois après un [com.naniger.arzikina.domain.repository.SyncAuthRepository.login] réussi
     * (voir `SyncAuthRepositoryImpl`) : c'est le moment où la synchronisation vient de s'activer
     * pour cet appareil — sans cet appel, les données déjà présentes avant la connexion resteraient
     * indéfiniment invisibles du serveur (rien ne les modifie jamais après coup pour les faire
     * entrer en file via [pushPendingChanges] seul).
     */
    suspend fun enqueueUnsyncedLocalData()
}
