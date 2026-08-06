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
 * La restauration remplace TOUTES les données existantes dans une seule
 * transaction Room ([ArzikinaDatabase.withTransaction]) : soit tout réussit,
 * soit la base reste inchangée en cas d'erreur (fichier corrompu, coupure en
 * cours de route...). L'ordre de suppression/insertion respecte les clés
 * étrangères (voir [TransactionEntity][com.arzikina.ne.data.local.entity.TransactionEntity]
 * et [BudgetEntity][com.arzikina.ne.data.local.entity.BudgetEntity] pour le
 * détail des contraintes).
 */
class BackupRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BackupRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    override suspend fun exportBackup(outputStream: OutputStream): BackupResult =
        withContext(ioDispatcher) {
            val accounts = accountDao.observeAll().first()
            val categories = categoryDao.observeAll().first()
            val transactions = transactionDao.observeAll().first()
            val budgets = budgetDao.observeAll().first()
            val savingsGoals = savingsGoalDao.observeAll().first()
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

            database.withTransaction {
                // Ordre de suppression : les tables dépendantes d'abord (contraintes de clé étrangère).
                transactionDao.deleteAll()
                budgetDao.deleteAll()
                categoryDao.deleteAll()
                accountDao.deleteAll()
                savingsGoalDao.deleteAll()

                // Ordre d'insertion : les tables référencées d'abord.
                accountDao.insertAll(payload.accounts.map { it.toEntity() })
                categoryDao.insertAll(payload.categories.map { it.toEntity() })
                savingsGoalDao.insertAll(payload.savingsGoals.map { it.toEntity() })
                transactionDao.insertAll(payload.transactions.map { it.toEntity() })
                budgetDao.insertAll(payload.budgets.map { it.toEntity() })
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
}
