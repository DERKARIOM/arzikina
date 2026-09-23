package com.naniger.arzikina.data.local.database

import androidx.room.TypeConverter
import com.naniger.arzikina.domain.model.AccountIcon
import com.naniger.arzikina.domain.model.AccountType
import com.naniger.arzikina.domain.model.BudgetPeriod
import com.naniger.arzikina.domain.model.CategoryIcon
import com.naniger.arzikina.domain.model.FeeType
import com.naniger.arzikina.domain.model.FinancialPlanIcon
import com.naniger.arzikina.domain.model.LoanReason
import com.naniger.arzikina.domain.model.LoanStatus
import com.naniger.arzikina.domain.model.LoanType
import com.naniger.arzikina.domain.model.OccurrenceStatus
import com.naniger.arzikina.domain.model.PaymentMethod
import com.naniger.arzikina.domain.model.PlanItemPriority
import com.naniger.arzikina.domain.model.PlanItemStatus
import com.naniger.arzikina.domain.model.PlanPeriodType
import com.naniger.arzikina.domain.model.PlanStatus
import com.naniger.arzikina.domain.model.RecurringFrequency
import com.naniger.arzikina.domain.model.RepaymentMode
import com.naniger.arzikina.domain.model.SecurityQuestion
import com.naniger.arzikina.domain.model.SyncOperation
import com.naniger.arzikina.domain.model.SyncStatus
import com.naniger.arzikina.domain.model.ThemeMode
import com.naniger.arzikina.domain.model.TransactionType

/**
 * Conversions Room pour les types qui n'ont pas de correspondance directe
 * avec une colonne SQLite. Un seul point centralisé pour toute la base :
 * chaque nouvelle entité qui a besoin d'un enum ajoute ses convertisseurs
 * ici plutôt que de dupliquer cette logique.
 */
class Converters {
    @TypeConverter
    fun fromAccountIcon(icon: AccountIcon): String = icon.name

    @TypeConverter
    fun toAccountIcon(value: String): AccountIcon = AccountIcon.valueOf(value)

    @TypeConverter
    fun fromAccountType(type: AccountType): String = type.name

    @TypeConverter
    fun toAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter
    fun fromCategoryIcon(icon: CategoryIcon): String = icon.name

    @TypeConverter
    fun toCategoryIcon(value: String): CategoryIcon = CategoryIcon.valueOf(value)

    @TypeConverter
    fun fromTransactionType(type: TransactionType): String = type.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromBudgetPeriod(period: BudgetPeriod): String = period.name

    @TypeConverter
    fun toBudgetPeriod(value: String): BudgetPeriod = BudgetPeriod.valueOf(value)

    @TypeConverter
    fun fromSecurityQuestion(question: SecurityQuestion): String = question.name

    @TypeConverter
    fun toSecurityQuestion(value: String): SecurityQuestion = SecurityQuestion.valueOf(value)

    /** Nullable (contrairement aux enums ci-dessus) : voir [PaymentMethod], champ "si applicable". */
    @TypeConverter
    fun fromPaymentMethod(method: PaymentMethod?): String? = method?.name

    @TypeConverter
    fun toPaymentMethod(value: String?): PaymentMethod? = value?.let { PaymentMethod.valueOf(it) }

    @TypeConverter
    fun fromLoanType(type: LoanType): String = type.name

    @TypeConverter
    fun toLoanType(value: String): LoanType = LoanType.valueOf(value)

    @TypeConverter
    fun fromLoanReason(reason: LoanReason): String = reason.name

    @TypeConverter
    fun toLoanReason(value: String): LoanReason = LoanReason.valueOf(value)

    @TypeConverter
    fun fromRepaymentMode(mode: RepaymentMode): String = mode.name

    @TypeConverter
    fun toRepaymentMode(value: String): RepaymentMode = RepaymentMode.valueOf(value)

    @TypeConverter
    fun fromLoanStatus(status: LoanStatus): String = status.name

    @TypeConverter
    fun toLoanStatus(value: String): LoanStatus = LoanStatus.valueOf(value)

    @TypeConverter
    fun fromRecurringFrequency(frequency: RecurringFrequency): String = frequency.name

    @TypeConverter
    fun toRecurringFrequency(value: String): RecurringFrequency = RecurringFrequency.valueOf(value)

    @TypeConverter
    fun fromOccurrenceStatus(status: OccurrenceStatus): String = status.name

    @TypeConverter
    fun toOccurrenceStatus(value: String): OccurrenceStatus = OccurrenceStatus.valueOf(value)

    /** Nullable (même raisonnement que [PaymentMethod] ci-dessus) : voir [FeeType]. */
    @TypeConverter
    fun fromFeeType(type: FeeType?): String? = type?.name

    @TypeConverter
    fun toFeeType(value: String?): FeeType? = value?.let { FeeType.valueOf(it) }

    @TypeConverter
    fun fromPlanPeriodType(type: PlanPeriodType): String = type.name

    @TypeConverter
    fun toPlanPeriodType(value: String): PlanPeriodType = PlanPeriodType.valueOf(value)

    @TypeConverter
    fun fromPlanStatus(status: PlanStatus): String = status.name

    @TypeConverter
    fun toPlanStatus(value: String): PlanStatus = PlanStatus.valueOf(value)

    @TypeConverter
    fun fromFinancialPlanIcon(icon: FinancialPlanIcon): String = icon.name

    @TypeConverter
    fun toFinancialPlanIcon(value: String): FinancialPlanIcon = FinancialPlanIcon.valueOf(value)

    @TypeConverter
    fun fromPlanItemPriority(priority: PlanItemPriority): String = priority.name

    @TypeConverter
    fun toPlanItemPriority(value: String): PlanItemPriority = PlanItemPriority.valueOf(value)

    @TypeConverter
    fun fromPlanItemStatus(status: PlanItemStatus): String = status.name

    @TypeConverter
    fun toPlanItemStatus(value: String): PlanItemStatus = PlanItemStatus.valueOf(value)

    /** Voir `data/local/entity/SyncQueueEntity`. */
    @TypeConverter
    fun fromSyncOperation(operation: SyncOperation): String = operation.name

    @TypeConverter
    fun toSyncOperation(value: String): SyncOperation = SyncOperation.valueOf(value)

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    /** Voir `data/local/entity/UserPreferencesEntity`. */
    @TypeConverter
    fun fromThemeMode(mode: ThemeMode): String = mode.name

    @TypeConverter
    fun toThemeMode(value: String): ThemeMode = ThemeMode.valueOf(value)
}
