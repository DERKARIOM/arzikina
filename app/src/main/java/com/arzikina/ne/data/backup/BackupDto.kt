package com.arzikina.ne.data.backup

import kotlinx.serialization.Serializable

/**
 * Version du format de fichier de sauvegarde — indépendante de [com.arzikina.ne.data.local.database.ArzikinaDatabase.version]
 * (schéma Room). Ces DTO existent précisément pour que le fichier JSON
 * exporté puisse évoluer à son propre rythme (ex. ajout d'un champ optionnel)
 * sans être couplé aux migrations Room ni aux détails internes des entités.
 *
 * Les enums sont sérialisés par leur nom (`String`) plutôt que leur ordinal :
 * un fichier de sauvegarde doit rester lisible et stable même si l'ordre de
 * déclaration d'un enum change plus tard.
 */
const val BACKUP_SCHEMA_VERSION = 1

@Serializable
data class BackupPayload(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val exportedAtEpochMillis: Long,
    val preferences: UserPreferencesDto,
    val accounts: List<AccountDto>,
    val categories: List<CategoryDto>,
    val transactions: List<TransactionDto>,
    val budgets: List<BudgetDto>,
    val savingsGoals: List<SavingsGoalDto>,
    /** Ajoutés après coup (voir `PersonEntity`/`LoanEntity`/`LoanPaymentEntity`) : listes vides
     * par défaut pour rester compatible avec les fichiers exportés avant leur existence — un
     * ancien fichier restauré ne recrée simplement aucun prêt/emprunt, sans erreur. */
    val persons: List<PersonDto> = emptyList(),
    val loans: List<LoanDto> = emptyList(),
    val loanPayments: List<LoanPaymentDto> = emptyList()
)

@Serializable
data class UserPreferencesDto(
    val themeMode: String,
    val currencyCode: String
)

@Serializable
data class AccountDto(
    val id: Long,
    val name: String,
    val icon: String,
    val colorArgb: Long,
    val currencyCode: String,
    val initialBalanceMinor: Long,
    val createdAt: Long,
    /** Ajouté après coup (voir `domain/model/AccountType`) : défaut `"CASH"`
     * pour rester compatible avec les fichiers exportés avant son existence. */
    val type: String = "CASH",
    /**
     * Uniquement pour un compte [com.arzikina.ne.domain.model.AccountType.CREDIT_CARD] :
     * les 4 derniers chiffres seulement (voir `Account.cardLastFourDigits`).
     * Le numéro complet et le CVV ne sont JAMAIS conservés par l'application,
     * même en mémoire au-delà de la saisie — ils n'existent donc nulle part
     * qui pourrait finir dans une sauvegarde.
     */
    val cardLastFourDigits: String? = null,
    val cardExpiryMonth: Int? = null,
    val cardExpiryYear: Int? = null,
    /** Ajouté après coup (voir `domain/model/Account.isExcludedFromStatistics`) : défaut
     * `false` pour rester compatible avec les fichiers exportés avant son existence — un
     * ancien fichier restauré recrée des comptes tous inclus dans les statistiques,
     * comme c'était implicitement le cas avant cette fonctionnalité. */
    val isExcludedFromStatistics: Boolean = false
)

@Serializable
data class CategoryDto(
    val id: Long,
    val name: String,
    val icon: String,
    val colorArgb: Long,
    val type: String,
    val createdAt: Long
)

@Serializable
data class TransactionDto(
    val id: Long,
    val amount: Long,
    val type: String,
    val accountId: Long,
    /** Ajouté après coup (voir `domain/model/TransactionType.TRANSFER`) : défaut
     * `null` pour rester compatible avec les fichiers exportés avant son existence
     * (aucune transaction d'un ancien fichier n'est un transfert). */
    val transferAccountId: Long? = null,
    /** Devenu optionnel en même temps que [transferAccountId] : `null` pour un
     * transfert, toujours renseigné pour un revenu ou une dépense. */
    val categoryId: Long? = null,
    val date: Long,
    val description: String,
    val receiptPhotoUri: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Ajouté après coup (voir `domain/model/PaymentMethod`) : défaut `null`
     * pour rester compatible avec les fichiers exportés avant son existence. */
    val paymentMethod: String? = null,
    val createdAt: Long,
    /** Ajouté après coup (voir `domain/model/Transaction.feeTransactionId`) : défaut `null` pour
     * rester compatible avec les fichiers exportés avant son existence. L'id d'origine étant
     * conservé tel quel à la restauration (voir la doc de tête de `BackupMappers`), ce pointeur
     * reste valide après import : les deux lignes liées sont restaurées avec les mêmes ids. */
    val feeTransactionId: Long? = null,
    /** Voir `domain/model/Transaction.feeType`/`FeeType`. */
    val feeType: String? = null
)

@Serializable
data class BudgetDto(
    val id: Long,
    val categoryId: Long,
    val period: String,
    val limitAmount: Long,
    val currencyCode: String,
    val createdAt: Long
)

@Serializable
data class SavingsGoalDto(
    val id: Long,
    val name: String,
    val targetAmount: Long,
    val currentAmount: Long,
    val currencyCode: String,
    val deadline: Long? = null,
    val createdAt: Long
)

@Serializable
data class PersonDto(
    val id: Long,
    val name: String,
    val phone: String? = null,
    val createdAt: Long
)

@Serializable
data class LoanDto(
    val id: Long,
    val personId: Long,
    val accountId: Long,
    val type: String,
    val amount: Long,
    val amountRepaid: Long,
    val remainingAmount: Long,
    val startDate: Long,
    val dueDate: Long,
    val reason: String,
    val reasonCustomText: String? = null,
    val repaymentMode: String,
    val description: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Voir `LoanEntity.transactionId` : id de la transaction de décaissement, réexportée telle
     * quelle — `TransactionDto` conserve aussi l'`id` d'origine (voir plus haut), donc cette
     * référence reste valide après import (voir l'ordre d'insertion dans `BackupRepositoryImpl`). */
    val transactionId: Long
)

@Serializable
data class LoanPaymentDto(
    val id: Long,
    val loanId: Long,
    val accountId: Long,
    val amount: Long,
    val date: Long,
    val note: String,
    val transactionId: Long,
    val createdAt: Long
)
