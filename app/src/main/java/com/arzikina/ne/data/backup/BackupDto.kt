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
    val savingsGoals: List<SavingsGoalDto>
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
    val createdAt: Long
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
    val createdAt: Long
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
