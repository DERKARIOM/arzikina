package com.naniger.arzikina.data.local.database

import androidx.room.withTransaction
import com.naniger.arzikina.data.local.dao.AccountDao
import com.naniger.arzikina.data.local.dao.BudgetDao
import com.naniger.arzikina.data.local.dao.CategoryDao
import com.naniger.arzikina.data.local.dao.FinancialPlanDao
import com.naniger.arzikina.data.local.dao.FinancialPlanItemDao
import com.naniger.arzikina.data.local.dao.IssuedReceiptDao
import com.naniger.arzikina.data.local.dao.IssuerProfileDao
import com.naniger.arzikina.data.local.dao.LoanDao
import com.naniger.arzikina.data.local.dao.LoanPaymentDao
import com.naniger.arzikina.data.local.dao.PersonDao
import com.naniger.arzikina.data.local.dao.ReceiptDao
import com.naniger.arzikina.data.local.dao.RecurringTransactionDao
import com.naniger.arzikina.data.local.dao.RecurringTransactionOccurrenceDao
import com.naniger.arzikina.data.local.dao.SavingsGoalDao
import com.naniger.arzikina.data.local.dao.TransactionDao
import com.naniger.arzikina.data.local.dao.TransactionTemplateDao
import com.naniger.arzikina.data.local.dao.UserDao
import com.naniger.arzikina.data.local.dao.UserPreferencesDao
import com.naniger.arzikina.data.local.dao.UserProfilePhotoDao
import com.naniger.arzikina.data.local.dao.UserServerLinkDao
import com.naniger.arzikina.data.issuedreceipt.IssuedReceiptJson
import com.naniger.arzikina.data.issuedreceipt.IssuerLogoStorage
import com.naniger.arzikina.data.issuedreceipt.pdf.IssuedReceiptPdfStorage
import com.naniger.arzikina.data.profile.ProfilePhotoFileStorage
import com.naniger.arzikina.data.receipts.ReceiptFileStorage
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Efface TOUTES les données locales d'un utilisateur, y compris sa propre ligne `users` — seul
 * appelant prévu :
 * [com.naniger.arzikina.data.repository.UnifiedAuthRepositoryImpl.deleteAccount] ("Supprimer mon
 * compte", action irréversible). JAMAIS appelé par une simple déconnexion (voir
 * [com.naniger.arzikina.domain.repository.SessionManager.clearSession], qui n'efface que le
 * pointeur de session, aucune donnée) ni par la restauration d'une sauvegarde (voir
 * `BackupRepositoryImpl.importBackup`, qui purge puis RÉINSÈRE dans la MÊME transaction — un cas
 * d'usage différent qui réutilise les mêmes `deleteAllForUser`, voir ci-dessous).
 *
 * Room ne peut PAS cascader depuis `users` : ni `UserEntity` ni les tables filles n'ont de
 * `ForeignKey` SQL vers `users` (limitation SQLite constatée en pratique — on ne peut pas ajouter
 * une contrainte à une table déjà existante sans la recréer entièrement, voir la KDoc de tête de
 * `MIGRATION_6_7`). Chaque table « possédée » par l'utilisateur est donc purgée EXPLICITEMENT ici
 * via son `deleteAllForUser(userId)` — déjà présent sur chaque DAO synchronisé (construit à
 * l'origine pour `BackupRepositoryImpl.importBackup`), réutilisé tel quel plutôt que dupliqué.
 * `card_secrets` n'a pas besoin d'un appel dédié : sa seule clé étrangère (`accountId`, voir
 * [com.naniger.arzikina.data.local.entity.CardSecretEntity]) est une VRAIE contrainte SQL
 * `CASCADE`, posée dès sa création — elle se déclenche automatiquement au `DELETE` physique de
 * [accountDao] ci-dessous.
 *
 * Fichiers physiques (reçus, photo de profil) : capturés AVANT la transaction puis supprimés
 * APRÈS son succès complet, jamais avant ni pendant — même principe que
 * `BackupRepositoryImpl.importBackup` (`oldReceiptFilePaths`) : si la transaction échoue pour une
 * raison quelconque, aucun fichier encore référencé par une ligne Room n'est perdu.
 */
class AccountDataWiper @Inject constructor(
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
    private val transactionTemplateDao: TransactionTemplateDao,
    private val issuedReceiptDao: IssuedReceiptDao,
    private val issuerProfileDao: IssuerProfileDao,
    private val issuerLogoStorage: IssuerLogoStorage,
    private val issuedReceiptPdfStorage: IssuedReceiptPdfStorage,
    private val userPreferencesDao: UserPreferencesDao,
    private val userProfilePhotoDao: UserProfilePhotoDao,
    private val profilePhotoFileStorage: ProfilePhotoFileStorage,
    private val userServerLinkDao: UserServerLinkDao,
    private val userDao: UserDao
) {
    suspend fun wipeAllLocalData(userId: Long) {
        // Chemins physiques lus une dernière fois pendant que les lignes Room existent encore
        // (voir la doc de tête).
        val oldReceiptFilePaths = receiptDao.observeAllForUser(userId).first().map { it.localPath }
        val oldProfilePhotoPath = userProfilePhotoDao.findByUserId(userId)?.localPath
        val oldIssuerProfile = issuerProfileDao.getForUser(userId)
        val oldIssuerLogoFile = oldIssuerProfile?.logoFileName
        // Signatures : celle du profil + celles figées dans les reçus (anciens noms inclus).
        val oldSignatureFiles = (
            listOfNotNull(oldIssuerProfile?.signatureFileName) +
                issuedReceiptDao.getIssuerJsonsMentioningSignature(userId)
                    .mapNotNull { IssuedReceiptJson.decodeIssuer(it).signatureFileName }
            ).distinct()

        database.withTransaction {
            // Même ordre que `BackupRepositoryImpl.importBackup` (voir sa doc : enfants avant
            // parents, bien qu'aucun ordre ne soit strictement obligatoire ici — aucune `ForeignKey`
            // SQL ne relie ces tables entre elles, voir la doc de tête).
            loanPaymentDao.deleteAllForUser(userId)
            loanDao.deleteAllForUser(userId)
            recurringTransactionOccurrenceDao.deleteAllForUser(userId)
            recurringTransactionDao.deleteAllForUser(userId)
            transactionTemplateDao.deleteAllForUser(userId)
            transactionDao.deleteAllForUser(userId)
            budgetDao.deleteAllForUser(userId)
            financialPlanItemDao.deleteAllForUser(userId)
            financialPlanDao.deleteAllForUser(userId)
            personDao.deleteAllForUser(userId)
            categoryDao.deleteAllForUser(userId)
            // Vrai DELETE physique (voir la doc de tête) : efface `card_secrets` en cascade SQL.
            accountDao.deleteAllForUser(userId)
            savingsGoalDao.deleteAllForUser(userId)
            receiptDao.deleteAllForUser(userId)
            // Reçus émis et profil émetteur (utilitaire « Gestion de reçus »). Le fichier du logo est
            // supprimé après la transaction, ainsi que le dossier des PDF de l'utilisateur.
            issuedReceiptDao.deleteAllForUser(userId)
            issuerProfileDao.deleteAllForUser(userId)
            userPreferencesDao.deleteAllForUser(userId)
            userProfilePhotoDao.deleteAllForUser(userId)
            userServerLinkDao.deleteAllForUser(userId)
            userDao.deleteUser(userId)
        }

        // Fichiers physiques : voir la doc de tête, uniquement APRÈS le commit de la transaction.
        oldReceiptFilePaths.forEach { path -> runCatching { receiptFileStorage.deleteFile(path) } }
        oldProfilePhotoPath?.let { runCatching { profilePhotoFileStorage.deleteFile(it) } }
        oldIssuerLogoFile?.let { runCatching { issuerLogoStorage.delete(it) } }
        oldSignatureFiles.forEach { runCatching { issuerLogoStorage.delete(it) } }
        runCatching { issuedReceiptPdfStorage.deleteAllForUser(userId) }
    }
}
