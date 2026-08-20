package com.arzikina.ne.data.repository

import androidx.room.withTransaction
import com.arzikina.ne.data.backup.BACKUP_SCHEMA_VERSION
import com.arzikina.ne.data.backup.BackupPayload
import com.arzikina.ne.data.backup.remapIds
import com.arzikina.ne.data.backup.securityQuestionOrDefault
import com.arzikina.ne.data.backup.toDomain
import com.arzikina.ne.data.backup.toDto
import com.arzikina.ne.data.backup.toEntity
import com.arzikina.ne.data.local.dao.AccountDao
import com.arzikina.ne.data.local.dao.BudgetDao
import com.arzikina.ne.data.local.dao.CategoryDao
import com.arzikina.ne.data.local.dao.FinancialPlanDao
import com.arzikina.ne.data.local.dao.FinancialPlanItemDao
import com.arzikina.ne.data.local.dao.LoanDao
import com.arzikina.ne.data.local.dao.LoanPaymentDao
import com.arzikina.ne.data.local.dao.PersonDao
import com.arzikina.ne.data.local.dao.ReceiptDao
import com.arzikina.ne.data.local.dao.RecurringTransactionDao
import com.arzikina.ne.data.local.dao.RecurringTransactionOccurrenceDao
import com.arzikina.ne.data.local.dao.SavingsGoalDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.dao.UserDao
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.receipts.ReceiptFileStorage
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
import java.util.Base64
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
 * respecte les clés étrangères (voir [TransactionEntity][com.arzikina.ne.data.local.entity.TransactionEntity],
 * [BudgetEntity][com.arzikina.ne.data.local.entity.BudgetEntity],
 * [LoanEntity][com.arzikina.ne.data.local.entity.LoanEntity] et
 * [LoanPaymentEntity][com.arzikina.ne.data.local.entity.LoanPaymentEntity] pour le
 * détail des contraintes) : personnes/prêts/remboursements font désormais partie
 * intégrante de la sauvegarde (voir `BackupPayload`) — les exclure aurait signifié
 * perdre tout l'historique Prêts/Emprunts à chaque restauration.
 *
 * "Planification financière" (Étape 10) fait également partie intégrante de la sauvegarde
 * (`financialPlans`/`financialPlanItems`) — fonctionnalité INDÉPENDANTE d'"Automatisation" (voir la
 * doc de [com.arzikina.ne.domain.model.FinancialPlan]), traitée ici en parallèle, sans aucune
 * donnée partagée entre les deux.
 *
 * "Gestion des reçus" (Étape 9) fait également partie intégrante de la sauvegarde (`receipts`) —
 * SEULE table dont le fichier physique associé (le PDF, voir [ReceiptFileStorage]) est lui aussi
 * sauvegardé, et pas seulement ses métadonnées Room (voir [com.arzikina.ne.data.backup.ReceiptDto]) :
 * l'exclure aurait signifié perdre tous les reçus déjà reçus/importés à chaque restauration, ou pire,
 * restaurer des lignes Room qui ne pointent plus vers aucun fichier réel. Les anciens fichiers
 * physiques de l'utilisateur restauré (avant l'import) ne sont supprimés qu'APRÈS le succès complet
 * de la transaction ci-dessous (voir la fin de [importBackup]) : si l'import échoue et annule la
 * transaction, aucun fichier existant n'a alors été perdu.
 *
 * Réattribution des ids (voir la doc de tête de `data/backup/BackupMappers`) : l'import
 * n'insère JAMAIS les `id` du fichier tels quels — chaque table reçoit `id = 0L` (nouvel id
 * généré par SQLite), et les ids générés alimentent des tables de correspondance ancien → nouvel
 * id (`accountIdMap`, `categoryIdMap`, ...) utilisées pour réécrire les clés étrangères des tables
 * suivantes, dans l'ordre de dépendance. Fiable même avec plusieurs utilisateurs partageant le
 * même appareil : un id du fichier ne peut plus jamais entrer en collision avec une ligne
 * existante, qu'elle appartienne à l'utilisateur courant ou à un autre.
 */
class BackupRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val savingsGoalDao: SavingsGoalDao,
    private val personDao: PersonDao,
    private val loanDao: LoanDao,
    private val loanPaymentDao: LoanPaymentDao,
    private val recurringTransactionDao: RecurringTransactionDao,
    private val recurringTransactionOccurrenceDao: RecurringTransactionOccurrenceDao,
    private val financialPlanDao: FinancialPlanDao,
    private val financialPlanItemDao: FinancialPlanItemDao,
    private val receiptDao: ReceiptDao,
    private val receiptFileStorage: ReceiptFileStorage,
    private val userDao: UserDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val sessionManager: SessionManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : BackupRepository {

    // `encodeDefaults = true` est indispensable ici : sans lui, kotlinx.serialization omet du
    // JSON toute propriété égale à sa valeur par défaut, ce qui inclut `BackupPayload.schemaVersion`
    // (par défaut = BACKUP_SCHEMA_VERSION). Le fichier exporté n'embarquerait alors JAMAIS la
    // version réelle du schéma qui l'a produit, et le contrôle `check(payload.schemaVersion <= ...)`
    // à l'import (voir plus bas) deviendrait inopérant — un fichier d'une future version
    // incompatible serait accepté en silence, `ignoreUnknownKeys` faisant disparaître les champs
    // inconnus sans avertissement.
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun exportBackup(outputStream: OutputStream): BackupResult =
        withContext(ioDispatcher) {
            // Tout le corps est à l'intérieur de `use { }` (et non seulement l'écriture finale) :
            // `requireCurrentUserId()` et les lectures DAO qui suivent peuvent lever une exception,
            // et le flux fourni par le Storage Access Framework doit être refermé même dans ce cas
            // (BackupFragment ne le referme jamais lui-même, voir sa doc de tête).
            outputStream.use { stream ->
                val userId = requireCurrentUserId()
                val accounts = accountDao.observeAllForUser(userId).first()
                val categories = categoryDao.observeAllForUser(userId).first()
                val transactions = transactionDao.observeAllForUser(userId).first()
                val budgets = budgetDao.observeAllForUser(userId).first()
                val savingsGoals = savingsGoalDao.observeAllForUser(userId).first()
                val persons = personDao.observeAllForUser(userId).first()
                val loans = loanDao.observeAllForUser(userId).first()
                val loanPayments = loans.flatMap { loanPaymentDao.getAllForLoan(it.id, userId) }
                val recurringTransactions = recurringTransactionDao.observeAllForUser(userId).first()
                val recurringTransactionOccurrences = recurringTransactionOccurrenceDao.observeAllForUser(userId).first()
                val financialPlans = financialPlanDao.observeAllForUser(userId).first()
                val financialPlanItems = financialPlanItemDao.observeAllForUser(userId).first()
                // Voir la doc de tête de cette classe et de `ReceiptDto` : chaque ligne exportée
                // embarque le contenu binaire du PDF, lu ici (jamais dans BackupMappers, qui reste
                // pur). `mapNotNull` plutôt que `map` : une ligne Room dont le fichier n'existe déjà
                // plus sur le disque (normalement impossible, voir `ReceiptRepositoryImpl.deleteReceipt`,
                // mais jamais garanti à 100% — appareil corrompu, intervention manuelle...) est
                // silencieusement exclue de l'export plutôt que de faire échouer TOUT l'export à
                // cause d'une seule ligne déjà incohérente.
                val receipts = receiptDao.observeAllForUser(userId).first()
                val receiptDtos = receipts.mapNotNull { entity ->
                    val file = receiptFileStorage.resolveFile(entity.localPath)
                    if (!file.exists()) return@mapNotNull null
                    runCatching { entity.toDto(pdfBytes = file.readBytes()) }.getOrNull()
                }
                val preferences = userPreferencesRepository.observePreferences().first()
                // `findById` : pas de flux dédié à un seul utilisateur ici, une lecture ponctuelle
                // suffit pour un export (voir UserDao.findById). Ne devrait normalement jamais être
                // `null` (session active, voir requireCurrentUserId) — `?.toDto()` reste défensif.
                val user = userDao.findById(userId)

                val payload = BackupPayload(
                    exportedAtEpochMillis = System.currentTimeMillis(),
                    preferences = preferences.toDto(),
                    accounts = accounts.map { it.toDto() },
                    categories = categories.map { it.toDto() },
                    transactions = transactions.map { it.toDto() },
                    budgets = budgets.map { it.toDto() },
                    savingsGoals = savingsGoals.map { it.toDto() },
                    persons = persons.map { it.toDto() },
                    loans = loans.map { it.toDto() },
                    loanPayments = loanPayments.map { it.toDto() },
                    user = user?.toDto(),
                    recurringTransactions = recurringTransactions.map { it.toDto() },
                    recurringTransactionOccurrences = recurringTransactionOccurrences.map { it.toDto() },
                    financialPlans = financialPlans.map { it.toDto() },
                    financialPlanItems = financialPlanItems.map { it.toDto() },
                    receipts = receiptDtos
                )

                stream.write(json.encodeToString(payload).encodeToByteArray())

                BackupResult(
                    accountsCount = accounts.size,
                    categoriesCount = categories.size,
                    transactionsCount = transactions.size,
                    budgetsCount = budgets.size,
                    savingsGoalsCount = savingsGoals.size,
                    loansCount = loans.size,
                    recurringTransactionsCount = recurringTransactions.size,
                    occurrencesCount = recurringTransactionOccurrences.size,
                    plansCount = financialPlans.size,
                    planItemsCount = financialPlanItems.size,
                    receiptsCount = receiptDtos.size
                )
            }
        }

    override suspend fun importBackup(inputStream: InputStream): BackupResult =
        withContext(ioDispatcher) {
            val text = inputStream.use { it.readBytes().decodeToString() }
            val payload = json.decodeFromString<BackupPayload>(text)

            check(payload.schemaVersion <= BACKUP_SCHEMA_VERSION) {
                "Ce fichier de sauvegarde provient d'une version plus récente d'Arzikina."
            }
            val userId = requireCurrentUserId()

            // Vérification préalable, AVANT toute écriture : un profil qui entrerait en collision
            // avec un AUTRE utilisateur du même appareil doit échouer immédiatement, sans purger
            // ni réinsérer quoi que ce soit d'autre (voir la doc de classe, "Réattribution des
            // ids" — l'utilisateur restauré, lui, n'a jamais de nouvel id : c'est toujours celui
            // actuellement connecté qui est mis à jour en place).
            payload.user?.let { userDto ->
                check(userDao.findByUsernameExcluding(userDto.username, userId) == null) {
                    "Le nom d'utilisateur \"${userDto.username}\" est déjà utilisé par un autre compte sur cet appareil."
                }
                check(userDao.findByEmailExcluding(userDto.email, userId) == null) {
                    "L'adresse e-mail \"${userDto.email}\" est déjà utilisée par un autre compte sur cet appareil."
                }
            }

            // Capturé AVANT la transaction (voir la doc de tête de cette classe) : les anciens
            // fichiers physiques de reçus de cet utilisateur ne sont supprimés qu'une fois la
            // transaction ci-dessous entièrement validée — sinon un import qui échoue en cours de
            // route (fichier corrompu, coupure...) aurait déjà détruit des reçus existants alors
            // même que la base, elle, reste inchangée (rollback Room).
            val oldReceiptFilePaths = receiptDao.observeAllForUser(userId).first().map { it.localPath }

            database.withTransaction {
                // Profil utilisateur : mis à jour EN PLACE (voir la doc de tête de UserDto), pas
                // d'ordre de dépendance avec le reste (aucune FK SQL vers `users`, voir les
                // entités) — fait en premier par simple clarté de lecture.
                payload.user?.let { userDto ->
                    userDao.restoreProfileFromBackup(
                        userId = userId,
                        fullName = userDto.fullName,
                        username = userDto.username,
                        email = userDto.email,
                        phoneNumber = userDto.phoneNumber,
                        passwordHash = userDto.passwordHash,
                        securityQuestion = userDto.securityQuestionOrDefault(),
                        securityAnswerHash = userDto.securityAnswerHash
                    )
                }

                // Ordre de suppression : les tables dépendantes d'abord (contraintes de clé
                // étrangère) — loan_payments/loans avant persons/accounts dont ils dépendent ;
                // recurring_transaction_occurrences avant recurring_transactions, qui dépend lui-
                // même de accounts/categories ; financial_plan_items (FK NO_ACTION vers categories,
                // voir FinancialPlanItemEntity) avant categoryDao ci-dessous, et avant
                // financial_plans dont il dépend en CASCADE. Scopé à l'utilisateur courant : ne
                // touche jamais aux données d'un autre compte.
                loanPaymentDao.deleteAllForUser(userId)
                loanDao.deleteAllForUser(userId)
                recurringTransactionOccurrenceDao.deleteAllForUser(userId)
                recurringTransactionDao.deleteAllForUser(userId)
                transactionDao.deleteAllForUser(userId)
                budgetDao.deleteAllForUser(userId)
                financialPlanItemDao.deleteAllForUser(userId)
                financialPlanDao.deleteAllForUser(userId)
                personDao.deleteAllForUser(userId)
                categoryDao.deleteAllForUser(userId)
                accountDao.deleteAllForUser(userId)
                savingsGoalDao.deleteAllForUser(userId)
                // Voir la doc de tête de cette classe : seules les LIGNES Room sont purgées ici — les
                // anciens fichiers physiques restent sur le disque jusqu'au succès complet de la
                // transaction (voir `oldReceiptFilePaths` capturé plus haut et sa suppression après
                // `database.withTransaction { }` ci-dessous).
                receiptDao.deleteAllForUser(userId)

                // Ordre d'insertion : les tables référencées d'abord (accounts/categories/persons
                // avant transactions, transactions avant loans/loan_payments qui y font référence
                // via transactionId — voir LoanEntity/LoanPaymentEntity). Chaque table est insérée
                // avec id = 0L (voir la doc de classe ci-dessus) ; les ids générés par SQLite,
                // renvoyés dans le même ordre que la liste d'entrée (garantie Room), construisent
                // une table de correspondance ancien → nouvel id pour les tables suivantes. Le
                // fichier n'a jamais connu d'utilisateur (voir BackupMappers) : il est assigné ici
                // à celui actuellement connecté, qu'il ait ou non exporté ce fichier lui-même.
                val accountIdMap = payload.accounts
                    .zip(accountDao.insertAll(payload.accounts.map { it.toEntity(userId).copy(id = 0L) }))
                    .associate { (dto, newId) -> dto.id to newId }

                val categoryIdMap = payload.categories
                    .zip(categoryDao.insertAll(payload.categories.map { it.toEntity(userId).copy(id = 0L) }))
                    .associate { (dto, newId) -> dto.id to newId }

                val personIdMap = payload.persons
                    .zip(personDao.insertAll(payload.persons.map { it.toEntity(userId).copy(id = 0L) }))
                    .associate { (dto, newId) -> dto.id to newId }

                // Aucune autre table ne référence l'id d'un objectif d'épargne : pas de table de
                // correspondance à construire, un simple id neuf par ligne suffit.
                savingsGoalDao.insertAll(payload.savingsGoals.map { it.toEntity(userId).copy(id = 0L) })

                // Reçus (voir la doc de tête de cette classe) : depuis `Transaction.receiptId`
                // (cahier des charges "Créer une transaction depuis un reçu"), UNE AUTRE table fait
                // désormais référence à l'id d'un reçu — receiptIdMap construite ici, MÊME principe
                // que accountIdMap/categoryIdMap ci-dessus, et déjà entièrement connue au moment où
                // les transactions sont insérées plus bas (aucune 2ème passe nécessaire pour ce
                // pointeur, contrairement à feeTransactionId). Chaque PDF est d'abord réécrit sur le
                // disque (nouveau nom UUID, voir `ReceiptFileStorage.writeBytes`) AVANT que la ligne
                // Room correspondante ne soit construite avec son [ReceiptEntity.localPath] réel —
                // si `writeBytes` échoue pour un seul reçu (ex. stockage plein), l'exception remonte
                // et annule TOUTE la transaction (aucune ligne Room orpheline, voir la doc de classe
                // "soit tout réussit, soit la base reste inchangée") ; les fichiers déjà écrits pour
                // les reçus précédents dans cette même boucle deviennent alors simplement des
                // fichiers orphelins invisibles (aucune ligne Room ne les référence), jamais un
                // risque d'incohérence.
                val receiptEntities = payload.receipts.map { dto ->
                    val bytes = Base64.getDecoder().decode(dto.pdfBase64)
                    val written = receiptFileStorage.writeBytes(bytes)
                    dto.toEntity(userId, localPath = written.relativePath, fileSize = written.fileSize)
                }
                val receiptIdMap = payload.receipts
                    .zip(receiptDao.insertAll(receiptEntities))
                    .associate { (dto, newId) -> dto.id to newId }

                // Règles récurrentes : avant les transactions (aucune dépendance entre les deux),
                // mais APRÈS accounts/categories dont elles ont besoin. Les occurrences, elles,
                // doivent attendre à la fois cette map ET transactionIdMap (voir plus bas).
                val recurringTransactionIdMap = payload.recurringTransactions
                    .zip(
                        recurringTransactionDao.insertAll(
                            payload.recurringTransactions.map { it.remapIds(0L, accountIdMap, categoryIdMap).toEntity(userId) }
                        )
                    )
                    .associate { (dto, newId) -> dto.id to newId }

                // Transactions, 1ère passe (voir TransactionDto.remapIds) : accountId/categoryId/
                // receiptId déjà connus (comptes, catégories et reçus tous insérés plus haut),
                // feeTransactionId encore laissé à `null` (la transaction de frais qu'il désigne
                // peut être plus loin dans cette même liste, pas encore insérée).
                val transactionIdMap = payload.transactions
                    .zip(
                        transactionDao.insertAll(
                            payload.transactions.map {
                                it.remapIds(0L, accountIdMap, categoryIdMap, receiptIdMap = receiptIdMap).toEntity(userId)
                            }
                        )
                    )
                    .associate { (dto, newId) -> dto.id to newId }

                // 2ème passe : réécrit feeTransactionId maintenant que la correspondance de TOUTE
                // la table est connue — une mise à jour ciblée par transaction qui a des frais
                // (upsert sur un id déjà existant depuis la 1ère passe = UPDATE, jamais une
                // insertion supplémentaire, voir TransactionDao.insertAll). receiptIdMap explicitement
                // repassé ici aussi (voir la doc de TransactionDto.remapIds) : l'omettre effacerait
                // silencieusement le receiptId déjà résolu à la 1ère passe pour ces lignes.
                payload.transactions
                    .filter { it.feeTransactionId != null }
                    .forEach { dto ->
                        val newId = transactionIdMap.getValue(dto.id)
                        val remapped = dto.remapIds(newId, accountIdMap, categoryIdMap, transactionIdMap, receiptIdMap)
                        transactionDao.upsert(remapped.toEntity(userId))
                    }

                // Occurrences : APRÈS recurring_transactions ET transactions, dont chacune dépend
                // (recurringTransactionId obligatoire, transactionId optionnel — voir
                // RecurringTransactionOccurrenceDto.remapIds).
                recurringTransactionOccurrenceDao.insertAll(
                    payload.recurringTransactionOccurrences.map {
                        it.remapIds(0L, recurringTransactionIdMap, transactionIdMap).toEntity(userId)
                    }
                )

                // Planification financière (Étape 10) : financial_plans n'a aucune clé étrangère
                // (voir FinancialPlanEntity), insérable dès que possible — placé ici pour rester
                // groupé avec financial_plan_items juste en dessous, qui a lui besoin de
                // categoryIdMap ET transactionIdMap, déjà connus à ce stade.
                val financialPlanIdMap = payload.financialPlans
                    .zip(financialPlanDao.insertAll(payload.financialPlans.map { it.toEntity(userId).copy(id = 0L) }))
                    .associate { (dto, newId) -> dto.id to newId }

                financialPlanItemDao.insertAll(
                    payload.financialPlanItems.map {
                        it.remapIds(0L, financialPlanIdMap, categoryIdMap, transactionIdMap).toEntity(userId)
                    }
                )

                budgetDao.insertAll(payload.budgets.map { it.remapIds(0L, categoryIdMap).toEntity(userId) })

                val loanIdMap = payload.loans
                    .zip(
                        loanDao.insertAll(
                            payload.loans.map { it.remapIds(0L, personIdMap, accountIdMap, transactionIdMap).toEntity(userId) }
                        )
                    )
                    .associate { (dto, newId) -> dto.id to newId }

                loanPaymentDao.insertAll(
                    payload.loanPayments.map { it.remapIds(0L, loanIdMap, accountIdMap, transactionIdMap).toEntity(userId) }
                )
            }

            // Voir `oldReceiptFilePaths` et la doc de tête de cette classe : atteint UNIQUEMENT si
            // la transaction ci-dessus a réussi dans son intégralité — les anciens fichiers physiques
            // (déjà remplacés en base par les nouveaux reçus insérés juste au-dessus) peuvent
            // maintenant être supprimés sans risque. `runCatching` par fichier (jamais un seul bloc
            // englobant) : l'échec de suppression d'un fichier ne doit jamais empêcher la suppression
            // des suivants, ni faire échouer un import par ailleurs déjà entièrement réussi — au pire,
            // un fichier orphelin invisible reste sur le disque (même trade-off que
            // `ReceiptRepositoryImpl.deleteReceipt`).
            oldReceiptFilePaths.forEach { relativePath -> runCatching { receiptFileStorage.deleteFile(relativePath) } }

            val restoredPreferences = payload.preferences.toDomain()
            userPreferencesRepository.setThemeMode(restoredPreferences.themeMode)
            userPreferencesRepository.setCurrencyCode(restoredPreferences.currencyCode)

            BackupResult(
                accountsCount = payload.accounts.size,
                categoriesCount = payload.categories.size,
                transactionsCount = payload.transactions.size,
                budgetsCount = payload.budgets.size,
                savingsGoalsCount = payload.savingsGoals.size,
                loansCount = payload.loans.size,
                recurringTransactionsCount = payload.recurringTransactions.size,
                occurrencesCount = payload.recurringTransactionOccurrences.size,
                plansCount = payload.financialPlans.size,
                planItemsCount = payload.financialPlanItems.size,
                receiptsCount = payload.receipts.size
            )
        }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
