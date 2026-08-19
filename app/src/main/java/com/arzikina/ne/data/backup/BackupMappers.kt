package com.arzikina.ne.data.backup

import com.arzikina.ne.data.local.entity.AccountEntity
import com.arzikina.ne.data.local.entity.BudgetEntity
import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.data.local.entity.FinancialPlanEntity
import com.arzikina.ne.data.local.entity.FinancialPlanItemEntity
import com.arzikina.ne.data.local.entity.LoanEntity
import com.arzikina.ne.data.local.entity.LoanPaymentEntity
import com.arzikina.ne.data.local.entity.PersonEntity
import com.arzikina.ne.data.local.entity.RecurringTransactionEntity
import com.arzikina.ne.data.local.entity.RecurringTransactionOccurrenceEntity
import com.arzikina.ne.data.local.entity.SavingsGoalEntity
import com.arzikina.ne.data.local.entity.TransactionEntity
import com.arzikina.ne.data.local.entity.UserEntity
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.model.AccountType
import com.arzikina.ne.domain.model.BudgetPeriod
import com.arzikina.ne.domain.model.CategoryIcon
import com.arzikina.ne.domain.model.FeeType
import com.arzikina.ne.domain.model.FinancialPlanIcon
import com.arzikina.ne.domain.model.LoanReason
import com.arzikina.ne.domain.model.LoanStatus
import com.arzikina.ne.domain.model.LoanType
import com.arzikina.ne.domain.model.OccurrenceStatus
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.PlanItemPriority
import com.arzikina.ne.domain.model.PlanItemStatus
import com.arzikina.ne.domain.model.PlanPeriodType
import com.arzikina.ne.domain.model.PlanStatus
import com.arzikina.ne.domain.model.RecurringFrequency
import com.arzikina.ne.domain.model.RepaymentMode
import com.arzikina.ne.domain.model.SecurityQuestion
import com.arzikina.ne.domain.model.ThemeMode
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.model.UserPreferences

/**
 * Conversions Entity Room <-> DTO de sauvegarde. Volontairement séparées des
 * mappers Entity <-> domaine existants (package `data.mapper`) : ce sont
 * deux besoins différents (persistance Room vs. format de fichier), même si
 * les champs se ressemblent aujourd'hui.
 *
 * `XDto.toEntity(userId)` : le fichier de sauvegarde ne contient
 * volontairement AUCUN `userId` (format pensé comme portable — voir
 * [AccountDto] et les autres DTO), c'est `BackupRepositoryImpl` qui assigne
 * l'utilisateur CONNECTÉ AU MOMENT DE L'IMPORT, indépendamment de qui avait
 * exporté le fichier à l'origine.
 *
 * Réattribution des ids à l'import (voir `BackupRepositoryImpl.importBackup`) : les `id` du
 * fichier ne sont JAMAIS réutilisés tels quels — plusieurs utilisateurs partagent les mêmes
 * tables sur un même appareil, un `id` du fichier pourrait déjà être pris par un autre. Chaque
 * entité est donc insérée avec `id = 0L` (nouvel id généré par SQLite, qui ne réutilise jamais un
 * id déjà attribué à quiconque dans la table), et les fonctions `XDto.remapIds(...)` ci-dessous
 * réécrivent les clés étrangères (`accountId`, `categoryId`, `personId`, `loanId`,
 * `transactionId`, `feeTransactionId`...) à partir de tables de correspondance ancien → nouvel id
 * (`Map<Long, Long>`), construites au fur et à mesure des insertions, dans l'ordre de dépendance.
 * Volontairement des fonctions PURES (aucune dépendance à Room) : testables directement sans
 * base de données (voir l'étape "Tests unitaires" du plan de la fonctionnalité Sauvegarde).
 */

fun AccountEntity.toDto() = AccountDto(
    id = id,
    name = name,
    icon = icon.name,
    colorArgb = colorArgb,
    currencyCode = currencyCode,
    initialBalanceMinor = initialBalanceMinor,
    createdAt = createdAt,
    type = type.name,
    cardLastFourDigits = cardLastFourDigits,
    cardExpiryMonth = cardExpiryMonth,
    cardExpiryYear = cardExpiryYear,
    isExcludedFromStatistics = isExcludedFromStatistics,
    mobileMoneyPackageName = mobileMoneyPackageName
)

fun AccountDto.toEntity(userId: Long) = AccountEntity(
    id = id,
    userId = userId,
    name = name,
    icon = runCatching { AccountIcon.valueOf(icon) }.getOrDefault(AccountIcon.entries.first()),
    colorArgb = colorArgb,
    currencyCode = currencyCode,
    initialBalanceMinor = initialBalanceMinor,
    createdAt = createdAt,
    type = runCatching { AccountType.valueOf(type) }.getOrDefault(AccountType.CASH),
    cardLastFourDigits = cardLastFourDigits,
    cardExpiryMonth = cardExpiryMonth,
    cardExpiryYear = cardExpiryYear,
    isExcludedFromStatistics = isExcludedFromStatistics,
    mobileMoneyPackageName = mobileMoneyPackageName
)

fun CategoryEntity.toDto() = CategoryDto(
    id = id,
    name = name,
    icon = icon.name,
    colorArgb = colorArgb,
    type = type.name,
    createdAt = createdAt
)

fun CategoryDto.toEntity(userId: Long) = CategoryEntity(
    id = id,
    userId = userId,
    name = name,
    icon = runCatching { CategoryIcon.valueOf(icon) }.getOrDefault(CategoryIcon.entries.first()),
    colorArgb = colorArgb,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
    createdAt = createdAt
)

fun TransactionEntity.toDto() = TransactionDto(
    id = id,
    amount = amount,
    type = type.name,
    accountId = accountId,
    transferAccountId = transferAccountId,
    categoryId = categoryId,
    date = date,
    description = description,
    receiptPhotoUri = receiptPhotoUri,
    latitude = latitude,
    longitude = longitude,
    paymentMethod = paymentMethod?.name,
    createdAt = createdAt,
    feeTransactionId = feeTransactionId,
    feeType = feeType?.name
)

fun TransactionDto.toEntity(userId: Long) = TransactionEntity(
    id = id,
    userId = userId,
    amount = amount,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
    accountId = accountId,
    transferAccountId = transferAccountId,
    categoryId = categoryId,
    date = date,
    description = description,
    receiptPhotoUri = receiptPhotoUri,
    latitude = latitude,
    longitude = longitude,
    // `null` reste `null` ici (contrairement aux enums non-nullables ci-dessus,
    // qui retombent sur une valeur par défaut) : un moyen de paiement non
    // précisé dans le fichier doit rester non précisé après import.
    paymentMethod = paymentMethod?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() },
    createdAt = createdAt,
    feeTransactionId = feeTransactionId,
    // Même raisonnement que paymentMethod ci-dessus : `null` reste `null`, pas de valeur de repli.
    feeType = feeType?.let { runCatching { FeeType.valueOf(it) }.getOrNull() }
)

/**
 * Voir la doc de tête de ce fichier. Appelée en DEUX passes par `BackupRepositoryImpl` :
 * - 1ère passe (avant insertion) : [feeTransactionIdMap] omis (vide par défaut) — `accountId`/
 *   `transferAccountId`/`categoryId` sont déjà connus (comptes/catégories insérés avant les
 *   transactions), mais [feeTransactionId] retombe forcément à `null` : la transaction de frais
 *   qu'il désigne n'a peut-être pas encore reçu son nouvel id (elle peut être plus loin dans la
 *   même liste). Résultat inséré tel quel.
 * - 2ème passe (après insertion de TOUTES les transactions) : rappelée avec [newId] = l'id déjà
 *   attribué à cette ligne et [feeTransactionIdMap] = la correspondance complète de la table —
 *   permet de renseigner enfin [feeTransactionId] par une mise à jour ciblée (voir
 *   `BackupRepositoryImpl`), sans jamais dupliquer la ligne.
 *
 * [accountId] est obligatoire sur une transaction (voir `TransactionEntity`) : `getValue` échoue
 * bruyamment si l'id référencé est absent de [accountIdMap] (fichier corrompu) plutôt que
 * d'insérer une transaction sans compte valide — l'échec annule tout l'import (transaction Room
 * atomique, voir `BackupRepositoryImpl.importBackup`). Les clés étrangères optionnelles
 * ([transferAccountId], [TransactionDto.categoryId], [TransactionDto.feeTransactionId]) retombent
 * simplement sur `null` si absentes de leur table de correspondance.
 */
fun TransactionDto.remapIds(
    newId: Long,
    accountIdMap: Map<Long, Long>,
    categoryIdMap: Map<Long, Long>,
    feeTransactionIdMap: Map<Long, Long> = emptyMap()
): TransactionDto = copy(
    id = newId,
    accountId = accountIdMap.getValue(accountId),
    transferAccountId = transferAccountId?.let { accountIdMap[it] },
    categoryId = categoryId?.let { categoryIdMap[it] },
    feeTransactionId = feeTransactionId?.let { feeTransactionIdMap[it] }
)

fun BudgetEntity.toDto() = BudgetDto(
    id = id,
    categoryId = categoryId,
    period = period.name,
    limitAmount = limitAmount,
    currencyCode = currencyCode,
    createdAt = createdAt,
    startDate = startDate,
    endDate = endDate
)

fun BudgetDto.toEntity(userId: Long) = BudgetEntity(
    id = id,
    userId = userId,
    categoryId = categoryId,
    period = runCatching { BudgetPeriod.valueOf(period) }.getOrDefault(BudgetPeriod.MONTHLY),
    limitAmount = limitAmount,
    currencyCode = currencyCode,
    createdAt = createdAt,
    startDate = startDate,
    endDate = endDate
)

/** Voir la doc de tête de ce fichier. `categoryId` obligatoire : `getValue` échoue bruyamment si
 * absent de [categoryIdMap] (fichier corrompu) — reste vrai même si l'index `categoryId` de
 * `BudgetEntity` n'est plus unique depuis la version 19 (période fixe), une catégorie appartient
 * toujours à un seul utilisateur (voir `BudgetEntity`). */
fun BudgetDto.remapIds(newId: Long, categoryIdMap: Map<Long, Long>): BudgetDto = copy(
    id = newId,
    categoryId = categoryIdMap.getValue(categoryId)
)

fun SavingsGoalEntity.toDto() = SavingsGoalDto(
    id = id,
    name = name,
    targetAmount = targetAmount,
    currentAmount = currentAmount,
    currencyCode = currencyCode,
    deadline = deadline,
    createdAt = createdAt
)

fun SavingsGoalDto.toEntity(userId: Long) = SavingsGoalEntity(
    id = id,
    userId = userId,
    name = name,
    targetAmount = targetAmount,
    currentAmount = currentAmount,
    currencyCode = currencyCode,
    deadline = deadline,
    createdAt = createdAt
)

fun PersonEntity.toDto() = PersonDto(
    id = id,
    name = name,
    phone = phone,
    createdAt = createdAt
)

fun PersonDto.toEntity(userId: Long) = PersonEntity(
    id = id,
    userId = userId,
    name = name,
    phone = phone,
    createdAt = createdAt
)

fun LoanEntity.toDto() = LoanDto(
    id = id,
    personId = personId,
    accountId = accountId,
    type = type.name,
    amount = amount,
    amountRepaid = amountRepaid,
    remainingAmount = remainingAmount,
    startDate = startDate,
    dueDate = dueDate,
    reason = reason.name,
    reasonCustomText = reasonCustomText,
    repaymentMode = repaymentMode.name,
    description = description,
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    transactionId = transactionId
)

fun LoanDto.toEntity(userId: Long) = LoanEntity(
    id = id,
    userId = userId,
    personId = personId,
    accountId = accountId,
    type = runCatching { LoanType.valueOf(type) }.getOrDefault(LoanType.LENT),
    amount = amount,
    amountRepaid = amountRepaid,
    remainingAmount = remainingAmount,
    startDate = startDate,
    dueDate = dueDate,
    reason = runCatching { LoanReason.valueOf(reason) }.getOrDefault(LoanReason.OTHER),
    reasonCustomText = reasonCustomText,
    repaymentMode = runCatching { RepaymentMode.valueOf(repaymentMode) }.getOrDefault(RepaymentMode.SINGLE),
    description = description,
    status = runCatching { LoanStatus.valueOf(status) }.getOrDefault(LoanStatus.ONGOING),
    createdAt = createdAt,
    updatedAt = updatedAt,
    transactionId = transactionId
)

/** Voir la doc de tête de ce fichier. `personId`/`accountId`/`transactionId` tous obligatoires
 * (voir `LoanEntity`) : `getValue` échoue bruyamment si l'un d'eux est absent de sa table de
 * correspondance (fichier corrompu, ou transaction de décaissement manquante du fichier). */
fun LoanDto.remapIds(
    newId: Long,
    personIdMap: Map<Long, Long>,
    accountIdMap: Map<Long, Long>,
    transactionIdMap: Map<Long, Long>
): LoanDto = copy(
    id = newId,
    personId = personIdMap.getValue(personId),
    accountId = accountIdMap.getValue(accountId),
    transactionId = transactionIdMap.getValue(transactionId)
)

fun LoanPaymentEntity.toDto() = LoanPaymentDto(
    id = id,
    loanId = loanId,
    accountId = accountId,
    amount = amount,
    date = date,
    note = note,
    transactionId = transactionId,
    createdAt = createdAt
)

fun LoanPaymentDto.toEntity(userId: Long) = LoanPaymentEntity(
    id = id,
    userId = userId,
    loanId = loanId,
    accountId = accountId,
    amount = amount,
    date = date,
    note = note,
    transactionId = transactionId,
    createdAt = createdAt
)

/** Voir la doc de tête de ce fichier. `loanId`/`accountId`/`transactionId` tous obligatoires (voir
 * `LoanPaymentEntity`) : `getValue` échoue bruyamment si l'un d'eux est absent de sa table de
 * correspondance. */
fun LoanPaymentDto.remapIds(
    newId: Long,
    loanIdMap: Map<Long, Long>,
    accountIdMap: Map<Long, Long>,
    transactionIdMap: Map<Long, Long>
): LoanPaymentDto = copy(
    id = newId,
    loanId = loanIdMap.getValue(loanId),
    accountId = accountIdMap.getValue(accountId),
    transactionId = transactionIdMap.getValue(transactionId)
)

fun UserPreferences.toDto() = UserPreferencesDto(
    themeMode = themeMode.name,
    currencyCode = currencyCode
)

fun UserPreferencesDto.toDomain() = UserPreferences(
    themeMode = runCatching { ThemeMode.valueOf(themeMode) }.getOrDefault(ThemeMode.SYSTEM),
    currencyCode = currencyCode
)

/**
 * Voir la doc de tête de [UserDto] : PAS de `toEntity`, contrairement à toutes les autres DTO —
 * cette entité n'est jamais recréée en base, seulement mise à jour en place par
 * `BackupRepositoryImpl.importBackup` via `UserDao.restoreProfileFromBackup`. `profilePhotoUri`
 * volontairement absent (voir [UserDto]).
 */
fun UserEntity.toDto() = UserDto(
    fullName = fullName,
    username = username,
    email = email,
    phoneNumber = phoneNumber,
    passwordHash = passwordHash,
    securityQuestion = securityQuestion.name,
    securityAnswerHash = securityAnswerHash,
    createdAt = createdAt
)

/** Repli sur la première question de la liste si le fichier contient une valeur inconnue/corrompue
 * (même convention que les autres enums de ce fichier) — la réponse hachée resterait alors
 * associée à une question affichée différente de l'originale, limite acceptée pour un cas de
 * fichier corrompu qui ne devrait normalement jamais se produire. */
fun UserDto.securityQuestionOrDefault(): SecurityQuestion =
    runCatching { SecurityQuestion.valueOf(securityQuestion) }.getOrDefault(SecurityQuestion.entries.first())

fun RecurringTransactionEntity.toDto() = RecurringTransactionDto(
    id = id,
    type = type.name,
    amount = amount,
    accountId = accountId,
    categoryId = categoryId,
    description = description,
    paymentMethod = paymentMethod?.name,
    startDate = startDate,
    endDate = endDate,
    frequency = frequency.name,
    nextExecutionDate = nextExecutionDate,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun RecurringTransactionDto.toEntity(userId: Long) = RecurringTransactionEntity(
    id = id,
    userId = userId,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
    amount = amount,
    accountId = accountId,
    categoryId = categoryId,
    description = description,
    // `null` reste `null` (moyen de paiement non précisé) — même raisonnement que TransactionDto.toEntity.
    paymentMethod = paymentMethod?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() },
    startDate = startDate,
    endDate = endDate,
    frequency = runCatching { RecurringFrequency.valueOf(frequency) }.getOrDefault(RecurringFrequency.MONTHLY),
    nextExecutionDate = nextExecutionDate,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/** Voir la doc de tête de ce fichier. `accountId` obligatoire (voir `RecurringTransactionEntity`) :
 * `getValue` échoue bruyamment si absent de [accountIdMap] (fichier corrompu). `categoryId`
 * nullable (réservé à un futur type transfert, jamais `null` aujourd'hui en pratique) : retombe
 * simplement sur `null` si absent de [categoryIdMap]. */
fun RecurringTransactionDto.remapIds(
    newId: Long,
    accountIdMap: Map<Long, Long>,
    categoryIdMap: Map<Long, Long>
): RecurringTransactionDto = copy(
    id = newId,
    accountId = accountIdMap.getValue(accountId),
    categoryId = categoryId?.let { categoryIdMap[it] }
)

fun RecurringTransactionOccurrenceEntity.toDto() = RecurringTransactionOccurrenceDto(
    id = id,
    recurringTransactionId = recurringTransactionId,
    scheduledDate = scheduledDate,
    status = status.name,
    transactionId = transactionId,
    processedAt = processedAt,
    createdAt = createdAt
)

fun RecurringTransactionOccurrenceDto.toEntity(userId: Long) = RecurringTransactionOccurrenceEntity(
    id = id,
    userId = userId,
    recurringTransactionId = recurringTransactionId,
    scheduledDate = scheduledDate,
    status = runCatching { OccurrenceStatus.valueOf(status) }.getOrDefault(OccurrenceStatus.PENDING),
    transactionId = transactionId,
    processedAt = processedAt,
    createdAt = createdAt
)

/** Voir la doc de tête de ce fichier. `recurringTransactionId` obligatoire (voir
 * `RecurringTransactionOccurrenceEntity`) : `getValue` échoue bruyamment si absent de
 * [recurringTransactionIdMap] (fichier corrompu). `transactionId` nullable — `null` tant que
 * l'occurrence reste `PENDING`/`REJECTED` (voir [RecurringTransactionOccurrenceDto]) — retombe sur
 * `null` si absent de [transactionIdMap], au lieu d'échouer, puisqu'une occurrence `PENDING` n'a de
 * toute façon jamais de `transactionId` à remapper. */
fun RecurringTransactionOccurrenceDto.remapIds(
    newId: Long,
    recurringTransactionIdMap: Map<Long, Long>,
    transactionIdMap: Map<Long, Long>
): RecurringTransactionOccurrenceDto = copy(
    id = newId,
    recurringTransactionId = recurringTransactionIdMap.getValue(recurringTransactionId),
    transactionId = transactionId?.let { transactionIdMap[it] }
)

fun FinancialPlanEntity.toDto() = FinancialPlanDto(
    id = id,
    name = name,
    description = description,
    availableAmount = availableAmount,
    targetAmount = targetAmount,
    periodType = periodType.name,
    startDate = startDate,
    endDate = endDate,
    icon = icon.name,
    colorArgb = colorArgb,
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun FinancialPlanDto.toEntity(userId: Long) = FinancialPlanEntity(
    id = id,
    userId = userId,
    name = name,
    description = description,
    availableAmount = availableAmount,
    targetAmount = targetAmount,
    periodType = runCatching { PlanPeriodType.valueOf(periodType) }.getOrDefault(PlanPeriodType.NONE),
    startDate = startDate,
    endDate = endDate,
    icon = runCatching { FinancialPlanIcon.valueOf(icon) }.getOrDefault(FinancialPlanIcon.WALLET),
    colorArgb = colorArgb,
    status = runCatching { PlanStatus.valueOf(status) }.getOrDefault(PlanStatus.ACTIVE),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun FinancialPlanItemEntity.toDto() = FinancialPlanItemDto(
    id = id,
    planId = planId,
    name = name,
    amount = amount,
    actualAmount = actualAmount,
    categoryId = categoryId,
    description = description,
    plannedDate = plannedDate,
    priority = priority.name,
    status = status.name,
    transactionId = transactionId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun FinancialPlanItemDto.toEntity(userId: Long) = FinancialPlanItemEntity(
    id = id,
    userId = userId,
    planId = planId,
    name = name,
    amount = amount,
    actualAmount = actualAmount,
    categoryId = categoryId,
    description = description,
    plannedDate = plannedDate,
    priority = runCatching { PlanItemPriority.valueOf(priority) }.getOrDefault(PlanItemPriority.IMPORTANT),
    status = runCatching { PlanItemStatus.valueOf(status) }.getOrDefault(PlanItemStatus.TO_PLAN),
    transactionId = transactionId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/** Voir la doc de tête de ce fichier. `planId` obligatoire (voir `FinancialPlanItemEntity`) :
 * `getValue` échoue bruyamment si absent de [planIdMap] (fichier corrompu). `categoryId`/
 * `transactionId` nullables — retombent simplement sur `null` si absents de leur table de
 * correspondance, même raisonnement que [TransactionDto.remapIds]/[RecurringTransactionOccurrenceDto.remapIds]. */
fun FinancialPlanItemDto.remapIds(
    newId: Long,
    planIdMap: Map<Long, Long>,
    categoryIdMap: Map<Long, Long>,
    transactionIdMap: Map<Long, Long>
): FinancialPlanItemDto = copy(
    id = newId,
    planId = planIdMap.getValue(planId),
    categoryId = categoryId?.let { categoryIdMap[it] },
    transactionId = transactionId?.let { transactionIdMap[it] }
)
