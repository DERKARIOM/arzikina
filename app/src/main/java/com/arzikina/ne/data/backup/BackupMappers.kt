package com.arzikina.ne.data.backup

import com.arzikina.ne.data.local.entity.AccountEntity
import com.arzikina.ne.data.local.entity.BudgetEntity
import com.arzikina.ne.data.local.entity.CategoryEntity
import com.arzikina.ne.data.local.entity.SavingsGoalEntity
import com.arzikina.ne.data.local.entity.TransactionEntity
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.model.BudgetPeriod
import com.arzikina.ne.domain.model.CategoryIcon
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
 * Limite connue : ces mappers conservent l'`id` d'origine du fichier. Avec
 * plusieurs utilisateurs partageant les mêmes tables, cet `id` peut déjà
 * être utilisé par un AUTRE utilisateur, ce qui provoquerait un conflit ou
 * un écrasement de sa ligne. Cette restauration reste donc, pour l'instant,
 * fiable uniquement quand un seul utilisateur existe sur l'appareil (ce qui
 * est le cas jusqu'à la mise en place complète des écrans Connexion/Inscription).
 * À corriger avant l'ouverture réelle du multi-compte : réattribuer de
 * nouveaux `id` à l'import et faire correspondre les clés étrangères
 * (`accountId`/`categoryId`) à leur nouvelle valeur.
 */

fun AccountEntity.toDto() = AccountDto(
    id = id,
    name = name,
    icon = icon.name,
    colorArgb = colorArgb,
    currencyCode = currencyCode,
    initialBalanceMinor = initialBalanceMinor,
    createdAt = createdAt
)

fun AccountDto.toEntity(userId: Long) = AccountEntity(
    id = id,
    userId = userId,
    name = name,
    icon = runCatching { AccountIcon.valueOf(icon) }.getOrDefault(AccountIcon.entries.first()),
    colorArgb = colorArgb,
    currencyCode = currencyCode,
    initialBalanceMinor = initialBalanceMinor,
    createdAt = createdAt
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
    categoryId = categoryId,
    date = date,
    description = description,
    receiptPhotoUri = receiptPhotoUri,
    latitude = latitude,
    longitude = longitude,
    createdAt = createdAt
)

fun TransactionDto.toEntity(userId: Long) = TransactionEntity(
    id = id,
    userId = userId,
    amount = amount,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
    accountId = accountId,
    categoryId = categoryId,
    date = date,
    description = description,
    receiptPhotoUri = receiptPhotoUri,
    latitude = latitude,
    longitude = longitude,
    createdAt = createdAt
)

fun BudgetEntity.toDto() = BudgetDto(
    id = id,
    categoryId = categoryId,
    period = period.name,
    limitAmount = limitAmount,
    currencyCode = currencyCode,
    createdAt = createdAt
)

fun BudgetDto.toEntity(userId: Long) = BudgetEntity(
    id = id,
    userId = userId,
    categoryId = categoryId,
    period = runCatching { BudgetPeriod.valueOf(period) }.getOrDefault(BudgetPeriod.MONTHLY),
    limitAmount = limitAmount,
    currencyCode = currencyCode,
    createdAt = createdAt
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

fun UserPreferences.toDto() = UserPreferencesDto(
    themeMode = themeMode.name,
    currencyCode = currencyCode
)

fun UserPreferencesDto.toDomain() = UserPreferences(
    themeMode = runCatching { ThemeMode.valueOf(themeMode) }.getOrDefault(ThemeMode.SYSTEM),
    currencyCode = currencyCode
)
