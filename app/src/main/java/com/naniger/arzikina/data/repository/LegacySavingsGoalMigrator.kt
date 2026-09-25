package com.naniger.arzikina.data.repository

import androidx.room.withTransaction
import com.naniger.arzikina.data.local.dao.AccountDao
import com.naniger.arzikina.data.local.dao.SavingsGoalDao
import com.naniger.arzikina.data.local.database.ArzikinaDatabase
import com.naniger.arzikina.data.local.entity.AccountEntity
import com.naniger.arzikina.data.local.entity.SavingsGoalEntity
import com.naniger.arzikina.data.mapper.toSyncPayload
import com.naniger.arzikina.data.remote.dto.AccountSyncPayload
import com.naniger.arzikina.data.remote.dto.SavingsGoalSyncPayload
import com.naniger.arzikina.domain.model.AccountIcon
import com.naniger.arzikina.domain.model.AccountType
import com.naniger.arzikina.domain.model.SyncOperation
import com.naniger.arzikina.domain.repository.SessionManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Convertit les objectifs de l'ANCIEN utilitaire « Épargne » (table `savings_goals`, montant épargné
 * saisi à la main) en comptes [AccountType.SAVINGS_GOAL] — voir la doc de ce type.
 *
 * NON DESTRUCTIF et IDEMPOTENT :
 * - l'ancien objectif n'est jamais effacé, seulement supprimé en douceur (`deletedAt`), comme toute
 *   suppression du projet ; ses données (échéance comprise) restent en base ;
 * - le compte créé reprend le `syncId` de l'objectif (même UUID) : si le serveur l'a déjà converti
 *   (`database/migrations/006_savings_goal_accounts.sql`) ou si un autre appareil l'a fait avant,
 *   le push CREATE retombe sur la même ligne serveur (création idempotente de `push.php`) — jamais
 *   de doublon, quel que soit l'ordre dans lequel appareils et serveur migrent ;
 * - un compte portant déjà ce `syncId` localement (reçu par pull) n'est jamais recréé.
 *
 * Exécuté au démarrage de l'application, après chaque pull (un ancien objectif peut encore arriver
 * d'un appareil pas encore mis à jour) et après la restauration d'une ancienne sauvegarde : c'est
 * pourquoi cette conversion n'est pas une migration Room (elle doit aussi enfiler la synchronisation).
 */
@Singleton
class LegacySavingsGoalMigrator @Inject constructor(
    private val database: ArzikinaDatabase,
    private val accountDao: AccountDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val sessionManager: SessionManager,
    private val syncQueueEnqueuer: SyncQueueEnqueuer,
    private val json: Json
) {

    /** Plusieurs déclencheurs peuvent se chevaucher (démarrage + pull) : un seul passage à la fois,
     * sans quoi deux passages concurrents pourraient convertir le même objectif deux fois. */
    private val mutex = Mutex()

    /** @return le nombre d'objectifs convertis (0 sans utilisateur connecté ou sans ancien objectif). */
    suspend fun migrateCurrentUser(): Int {
        val userId = sessionManager.getCurrentUserIdOnce() ?: return 0
        return mutex.withLock { migrate(userId) }
    }

    private suspend fun migrate(userId: Long): Int {
        val goals = savingsGoalDao.getActiveForUser(userId)
        if (goals.isEmpty()) return 0

        val now = System.currentTimeMillis()
        val createdAccounts = mutableListOf<AccountEntity>()
        val deletedGoals = mutableListOf<SavingsGoalEntity>()

        database.withTransaction {
            var nextDisplayOrder = (accountDao.maxDisplayOrder(userId) ?: -1L) + 1L
            goals.forEach { goal ->
                val syncId = goal.syncId ?: UUID.randomUUID().toString()
                if (accountDao.getBySyncId(syncId) == null) {
                    val account = LegacySavingsGoalConversion.toAccountEntity(
                        goal = goal,
                        userId = userId,
                        syncId = syncId,
                        displayOrder = nextDisplayOrder++,
                        now = now
                    )
                    val newId = accountDao.upsert(account)
                    createdAccounts += account.copy(id = newId)
                }
                savingsGoalDao.softDeleteById(goal.id, userId, now)
                // Jamais synchronisé (pas de syncId) : le serveur ne le connaît pas, rien à supprimer.
                if (goal.syncId != null) deletedGoals += goal.copy(deletedAt = now, updatedAt = now)
            }
        }

        // Enfilés APRÈS la transaction Room (même principe que AccountRepositoryImpl.deleteAccount).
        createdAccounts.forEach { account ->
            syncQueueEnqueuer.enqueue(
                entityType = "accounts",
                entitySyncId = requireNotNull(account.syncId),
                operation = SyncOperation.CREATE,
                payloadJson = json.encodeToString(AccountSyncPayload.serializer(), account.toSyncPayload(baseVersion = null))
            )
        }
        deletedGoals.forEach { goal ->
            val payload = SavingsGoalSyncPayload(
                id = requireNotNull(goal.syncId),
                baseVersion = goal.version,
                name = goal.name,
                targetAmount = goal.targetAmount,
                currentAmount = goal.currentAmount,
                currencyCode = goal.currencyCode,
                deadline = goal.deadline,
                createdAt = goal.createdAt,
                updatedAt = now
            )
            syncQueueEnqueuer.enqueue(
                entityType = "savings_goals",
                entitySyncId = payload.id,
                operation = SyncOperation.DELETE,
                payloadJson = json.encodeToString(SavingsGoalSyncPayload.serializer(), payload)
            )
        }
        return goals.size
    }
}

/**
 * Règles de conversion d'un ancien objectif en compte — fonction pure, testée par
 * `LegacySavingsGoalConversionTest`. Mêmes règles que `006_savings_goal_accounts.sql` côté serveur.
 */
internal object LegacySavingsGoalConversion {

    /** Couleur par défaut du sélecteur de couleur des comptes (`ColorPalette.COLORS.first()`), pour
     * qu'elle reste sélectionnée dans « Modifier le compte » (#42B998, absente de cette palette, est
     * réservée aux barres de progression). */
    const val SAVINGS_GOAL_COLOR_ARGB: Long = 0xFF10B981L

    fun toAccountEntity(
        goal: SavingsGoalEntity,
        userId: Long,
        syncId: String,
        displayOrder: Long,
        now: Long
    ): AccountEntity = AccountEntity(
        userId = userId,
        name = goal.name,
        icon = AccountIcon.SAVINGS,
        colorArgb = SAVINGS_GOAL_COLOR_ARGB,
        currencyCode = goal.currencyCode,
        // L'ancien montant épargné (sans transaction) devient le solde initial du compte.
        initialBalanceMinor = goal.currentAmount.coerceAtLeast(0L),
        createdAt = goal.createdAt,
        type = AccountType.SAVINGS_GOAL,
        // Cet argent n'était compté dans AUCUN solde total jusqu'ici : l'inclure d'office ferait
        // bondir le solde total (voire compter deux fois un montant déjà présent sur un autre compte).
        // L'utilisateur peut réactiver l'inclusion depuis « Modifier le compte ».
        isExcludedFromStatistics = true,
        displayOrder = displayOrder,
        // Un montant cible est obligatoire pour un objectif (voir AccountFormViewModel.save).
        savingsTargetAmount = goal.targetAmount.coerceAtLeast(1L),
        syncId = syncId,
        updatedAt = now
    )
}
