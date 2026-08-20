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
    val loanPayments: List<LoanPaymentDto> = emptyList(),
    /** Ajouté après coup (voir `UserDto`) : `null` par défaut pour rester compatible avec les
     * fichiers exportés avant son existence — un ancien fichier restauré laisse alors le profil de
     * l'utilisateur courant totalement inchangé (voir `BackupRepositoryImpl.importBackup`). */
    val user: UserDto? = null,
    /** Ajoutés après coup (voir `RecurringTransactionEntity`/`RecurringTransactionOccurrenceEntity`) :
     * listes vides par défaut pour rester compatible avec les fichiers exportés avant leur
     * existence — un ancien fichier restauré ne recrée simplement aucune règle récurrente, sans
     * erreur (comportement identique à celui déjà choisi pour `persons`/`loans` plus haut). */
    val recurringTransactions: List<RecurringTransactionDto> = emptyList(),
    val recurringTransactionOccurrences: List<RecurringTransactionOccurrenceDto> = emptyList(),
    /** Ajoutés après coup (voir `FinancialPlanEntity`/`FinancialPlanItemEntity`, Étape 10) : listes
     * vides par défaut pour rester compatible avec les fichiers exportés avant leur existence — un
     * ancien fichier restauré ne recrée simplement aucune planification, sans erreur (même
     * comportement que `persons`/`loans`/`recurringTransactions` plus haut). */
    val financialPlans: List<FinancialPlanDto> = emptyList(),
    val financialPlanItems: List<FinancialPlanItemDto> = emptyList(),
    /** Ajouté après coup (voir `ReceiptEntity`, cahier des charges "Gestion des reçus" Étape 9) :
     * liste vide par défaut pour rester compatible avec les fichiers exportés avant son existence —
     * un ancien fichier restauré ne recrée simplement aucun reçu, sans erreur (même comportement que
     * les autres listes ajoutées après coup ci-dessus). Contrairement à toutes les autres listes de
     * ce fichier, chaque [ReceiptDto] embarque le contenu BINAIRE complet d'un PDF (voir sa doc) —
     * accepté explicitement : un fichier de sauvegarde qui contient beaucoup de reçus, ou des PDF
     * volumineux, peut donc devenir nettement plus lourd qu'avant cette fonctionnalité. */
    val receipts: List<ReceiptDto> = emptyList()
)

/**
 * PAS de champ pour `UserPreferences.biometricLockEnabled` (voir sa doc) : c'est un réglage PAR
 * APPAREIL, jamais lié à un compte. L'inclure ici ferait qu'une restauration sur un NOUVEL
 * appareil imposerait un verrou biométrique choisi sur un autre téléphone — potentiellement sans
 * capteur biométrique enrôlé sur celui-ci, ce qui bloquerait l'ouverture de l'app au prochain
 * lancement (voir `MainActivity.resolveStartDestination`). `toDomain()` retombe donc sur la
 * valeur par défaut (`false`) à chaque restauration, quel que soit le réglage du fichier source.
 */
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
    val isExcludedFromStatistics: Boolean = false,
    /**
     * Ajouté après coup (voir `domain/model/Account.mobileMoneyPackageName`) : défaut `null`
     * pour rester compatible avec les fichiers exportés avant son existence. INCLUS
     * volontairement dans la sauvegarde (contrairement à `UserPreferences.biometricLockEnabled`,
     * exclu lui — voir sa doc) : un simple nom de package Android n'a aucune conséquence de
     * sécurité à restaurer sur un nouvel appareil. Si l'application associée n'y est pas
     * installée, l'écran de détail affiche simplement "Application non installée" (voir
     * `MobileMoneyAppUiState.NotInstalled`) — jamais considéré comme une erreur de restauration.
     */
    val mobileMoneyPackageName: String? = null
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
     * rester compatible avec les fichiers exportés avant son existence. L'id du fichier n'est PAS
     * conservé tel quel à la restauration (voir `BackupMappers.remapIds`/`BackupRepositoryImpl`,
     * qui réattribuent de nouveaux ids) : ce pointeur est réécrit vers le nouvel id de la
     * transaction de frais correspondante, en une deuxième passe une fois tous les ids connus. */
    val feeTransactionId: Long? = null,
    /** Voir `domain/model/Transaction.feeType`/`FeeType`. */
    val feeType: String? = null
)

/** [startDate]/[endDate] : période fixe, absents (`null` par défaut) dans toute sauvegarde créée
 * avant la fonctionnalité "période fixe" — voir [BudgetEntity][com.arzikina.ne.data.local.entity.BudgetEntity]/
 * [Budget][com.arzikina.ne.domain.model.Budget]. Valeurs par défaut indispensables ici : sans elles,
 * la désérialisation d'une ancienne sauvegarde (JSON sans ces deux clés) échouerait. */
@Serializable
data class BudgetDto(
    val id: Long,
    val categoryId: Long,
    val period: String,
    val limitAmount: Long,
    val currencyCode: String,
    val createdAt: Long,
    val startDate: Long? = null,
    val endDate: Long? = null
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
    /** Voir `LoanEntity.transactionId` : id de la transaction de décaissement. Réécrit vers le
     * nouvel id de cette transaction à l'import (voir `LoanDto.remapIds`/`BackupRepositoryImpl`,
     * qui insèrent les transactions avant les prêts précisément pour connaître cette correspondance). */
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

/**
 * Profil de l'utilisateur qui a exporté ce fichier. Contrairement à tous les autres DTO, la
 * restauration ne RECRÉE jamais de ligne `users` à partir de celui-ci : elle met à jour EN PLACE
 * le profil de l'utilisateur actuellement connecté au moment de l'import (voir
 * `BackupRepositoryImpl.importBackup`) — un fichier de sauvegarde n'a de sens qu'une fois qu'un
 * compte existe déjà sur l'appareil pour le recevoir (inscription ou connexion préalable).
 *
 * PAS d'`id` : inutile, la ligne cible est celle de l'utilisateur courant, jamais celle du fichier.
 * PAS de `profilePhotoUri` : chemin de fichier local à l'ancien appareil, sans aucun sens après
 * restauration sur un autre (voir cahier des charges, "ne pas sauvegarder les informations
 * spécifiques au téléphone") — la photo de profil doit être re-choisie manuellement si besoin.
 *
 * [passwordHash]/[securityAnswerHash] : jamais le mot de passe ni la réponse en clair — ce sont
 * déjà des empreintes salées (voir `util/PasswordHasher`, PBKDF2), le format même de ces chaînes
 * ne permet pas de retrouver la valeur d'origine. Inclus pour que l'utilisateur retrouve, après
 * restauration, les MÊMES identifiants de connexion qu'avant (décision produit assumée : voir
 * l'échange qui a précédé cette étape) plutôt que d'être forcé de redéfinir un mot de passe.
 */
@Serializable
data class UserDto(
    val fullName: String,
    val username: String,
    val email: String,
    val phoneNumber: String? = null,
    val passwordHash: String,
    val securityQuestion: String,
    val securityAnswerHash: String,
    val createdAt: Long
)

/**
 * La RÈGLE récurrente elle-même (voir `RecurringTransactionEntity`) — pas ses occurrences
 * générées, voir [RecurringTransactionOccurrenceDto] séparément (même séparation modèle/occurrence
 * que `LoanDto`/`LoanPaymentDto`).
 */
@Serializable
data class RecurringTransactionDto(
    val id: Long,
    val type: String,
    val amount: Long,
    val accountId: Long,
    /** `NULL` uniquement réservé à un futur type transfert (voir `RecurringTransactionEntity.categoryId`). */
    val categoryId: Long? = null,
    val description: String,
    val paymentMethod: String? = null,
    val startDate: Long,
    val endDate: Long? = null,
    val frequency: String,
    val nextExecutionDate: Long,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** Défaut 08:00 (voir `RecurringTransactionEntity`/[com.arzikina.ne.domain.model.RecurringTransaction])
     * : absent de toute sauvegarde créée avant la fonctionnalité "heure de déclenchement", donc
     * indispensable pour que la désérialisation d'une ancienne sauvegarde continue de fonctionner. */
    val triggerHour: Int = 8,
    val triggerMinute: Int = 0
)

/**
 * Une exécution réelle (ou en attente) d'une [RecurringTransactionDto] — voir
 * `RecurringTransactionOccurrenceEntity`. [transactionId] pointe vers la transaction créée quand
 * [status] vaut `ACCEPTED`/`MODIFIED` ; `null` tant que l'occurrence reste `PENDING`, ou pour
 * toujours si `REJECTED`.
 */
@Serializable
data class RecurringTransactionOccurrenceDto(
    val id: Long,
    val recurringTransactionId: Long,
    val scheduledDate: Long,
    val status: String,
    val transactionId: Long? = null,
    val processedAt: Long? = null,
    val createdAt: Long
)

/**
 * Une planification financière par projet (voir `FinancialPlanEntity`) — pas ses dépenses prévues,
 * voir [FinancialPlanItemDto] séparément (même séparation modèle/enfant que [RecurringTransactionDto]/
 * [RecurringTransactionOccurrenceDto]). Aucune clé étrangère à remapper sur ce DTO lui-même (comme
 * [SavingsGoalDto]) : seul son [id] change à la restauration, via la table de correspondance
 * `financialPlanIdMap` construite par `BackupRepositoryImpl` pour réécrire [FinancialPlanItemDto.planId].
 */
@Serializable
data class FinancialPlanDto(
    val id: Long,
    val name: String,
    val description: String? = null,
    val availableAmount: Long,
    val targetAmount: Long? = null,
    val periodType: String,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val icon: String,
    val colorArgb: Long,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Une dépense prévue d'une [FinancialPlanDto] — voir `FinancialPlanItemEntity`. [transactionId]
 * pointe vers la transaction réelle créée lors d'une conversion explicite (voir la doc de
 * `FinancialPlanItem.transactionId`) ; `null` tant que la dépense reste purement prévisionnelle.
 */
@Serializable
data class FinancialPlanItemDto(
    val id: Long,
    val planId: Long,
    val name: String,
    val amount: Long,
    val actualAmount: Long? = null,
    val categoryId: Long? = null,
    val description: String? = null,
    val plannedDate: Long? = null,
    val priority: String,
    val status: String,
    val transactionId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Un reçu PDF (voir `ReceiptEntity`/`domain/model/Receipt`) — le SEUL DTO de ce fichier à embarquer
 * du contenu binaire ([pdfBase64]) plutôt que de simples métadonnées : voir la doc de tête de
 * `BackupPayload.receipts` pour le compromis assumé (taille de fichier), et la doc de tête de
 * `BackupMappers` pour le raisonnement complet sur la lecture/écriture du fichier physique, qui
 * reste hors de ce DTO et de ses fonctions de correspondance (fonctions PURES, voir la convention
 * de ce fichier).
 *
 * PAS de champ `localPath` : le chemin physique est un détail d'implémentation propre à un appareil
 * donné (voir `Receipt.localPath`) — la restauration écrit toujours [pdfBase64] vers un tout NOUVEAU
 * fichier (nom UUID neuf, voir `ReceiptFileStorage.writeBytes`), jamais vers l'ancien chemin, qui
 * n'existe de toute façon pas sur l'appareil de destination.
 */
@Serializable
data class ReceiptDto(
    val id: Long,
    val fileName: String,
    val receivedAt: Long,
    val fileSize: Long,
    val mimeType: String,
    val sourceApp: String? = null,
    val sourceName: String? = null,
    val amountMinor: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** Contenu binaire complet du fichier PDF, encodé en Base64 (voir `java.util.Base64`, même
     * choix que `util/PasswordHasher`). Lu depuis/écrit vers le disque uniquement par
     * `BackupRepositoryImpl` (via `ReceiptFileStorage`), jamais par ce DTO ni par les fonctions de
     * `BackupMappers` qui le manipulent. */
    val pdfBase64: String
)
