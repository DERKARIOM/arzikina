package com.arzikina.ne.data.repository

import androidx.room.withTransaction
import com.arzikina.ne.data.backup.BACKUP_SCHEMA_VERSION
import com.arzikina.ne.data.backup.BackupPayload
import com.arzikina.ne.data.backup.toDomain
import com.arzikina.ne.data.backup.toDto
import com.arzikina.ne.data.backup.toEntity
import com.arzikina.ne.data.local.dao.AccountDao
import com.arzikina.ne.data.local.dao.BudgetDao
import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.SavingsGoalDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.BackupResult
import com.arzikina.ne.domain.repository.BackupRepository
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * Implémentation [BackupRepository] : sérialise/désérialise directement les
 * entités Room (pas les modèles du domaine) vers les DTO de sauvegarde
 * ([com.arzikina.ne.data.backup]), pour éviter un aller-retour Entity ->
 * Domaine -> DTO sans valeur ajoutée ici.
 *
 * Isolation multi-utilisateurs : export et import ne portent QUE sur les
 * données de l'utilisateur connecté au moment de l'appel (voir
 * [SessionManager]) — ni l'un ni l'autre ne touchent aux données des autres
 * comptes présents sur le même appareil (voir `deleteAllForUser` dans
 * chaque DAO, qui remplace l'ancien `deleteAll()` global).
 *
 * La restauration remplace les données existantes DE CET UTILISATEUR dans
 * une seule transaction Room ([ArzikinaDatabase.withTransaction]) : soit
 * tout réussit, soit la base reste inchangée en cas d'erreur (fichier
 * corrompu, coupure en cours de route...). L'ordre de suppression/insertion
 * respecte les clés étrangères (voir [TransactionEntity][com.arzikina.ne.data.local.entity.TransactionEntity]
 * et [BudgetEntity][com.arzikina.ne.data.local.entity.BudgetEntity] pour le
 * détail des contraintes).
 *
 * Limite connue (voir `data/backup/BackupMappers`) : cette restauration
 * réutilise les `id` du fichier de sauvegarde, ce qui n'est fiable que
 * lorsqu'un seul utilisateur existe sur l'appareil — à corriger avant
 * l'ouverture réelle du multi-compte.
 */
class BackupRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val sessionManager: SessionManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BackupRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override suspend fun exportBackup(outputStream: OutputStream): BackupResult =
        withContext(ioDispatcher) {
            val userId = requireCurrentUserId()
            val accounts = accountDao.observeAllForUser(userId).first()
            val categories = categoryDao.observeAllForUser(userId).first()
            val transactions = transactionDao.observeAllForUser(userId).first()
            val budgets = budgetDao.observeAllForUser(userId).first()
            val savingsGoals = savingsGoalDao.observeAllForUser(userId).first()
            val preferences = userPreferencesRepository.observePreferences().first()

            val payload = BackupPayload(
                exportedAtEpochMillis = System.currentTimeMillis(),
                preferences = preferences.toDto(),
                accounts = accounts.map { it.toDto() },
                categories = categories.map { it.toDto() },
                transactions = transactions.map { it.toDto() },
                budgets = budgets.map { it.toDto() },
                savingsGoals = savingsGoals.map { it.toDto() }
            )

            outputStream.use { stream ->
                stream.write(json.encodeToString(payload).encodeToByteArray())
            }

            BackupResult(
                accountsCount = accounts.size,
                categoriesCount = categories.size,
                transactionsCount = transactions.size,
                budgetsCount = budgets.size,
                savingsGoalsCount = savingsGoals.size
            )
        }

    override suspend fun importBackup(inputStream: InputStream): BackupResult =
        withContext(ioDispatcher) {
            val text = inputStream.use { it.readBytes().decodeToString() }
            val payload = json.decodeFromString<BackupPayload>(text)

            check(payload.schemaVersion <= BACKUP_SCHEMA_VERSION) {
                "Ce fichier de sauvegarde provient d'une version plus récente d'Arzikina."
            }
            val userId = requireCurrentUserId()

            database.withTransaction {
                // Ordre de suppression : les tables dépendantes d'abord (contraintes de clé étrangère).
                // Scopé à l'utilisateur courant : ne touche jamais aux données d'un autre compte.
                transactionDao.deleteAllForUser(userId)
                budgetDao.deleteAllForUser(userId)
                categoryDao.deleteAllForUser(userId)
                accountDao.deleteAllForUser(userId)
                savingsGoalDao.deleteAllForUser(userId)

                // Ordre d'insertion : les tables référencées d'abord. Le fichier n'a jamais
                // connu d'utilisateur (voir BackupMappers) : il est assigné ici à celui
                // actuellement connecté, qu'il ait ou non exporté ce fichier lui-même.
                accountDao.insertAll(payload.accounts.map { it.toEntity(userId) })
                categoryDao.insertAll(payload.categories.map { it.toEntity(userId) })
                savingsGoalDao.insertAll(payload.savingsGoals.map { it.toEntity(userId) })
                transactionDao.insertAll(payload.transactions.map { it.toEntity(userId) })
                budgetDao.insertAll(payload.budgets.map { it.toEntity(userId) })
            }

            val restoredPreferences = payload.preferences.toDomain()
            userPreferencesRepository.setThemeMode(restoredPreferences.themeMode)
            userPreferencesRepository.setCurrencyCode(restoredPreferences.currencyCode)

            BackupResult(
                accountsCount = payload.accounts.size,
                categoriesCount = payload.categories.size,
                transactionsCount = payload.transactions.size,
                budgetsCount = payload.budgets.size,
                savingsGoalsCount = payload.savingsGoals.size
            )
        }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
