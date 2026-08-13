package com.arzikina.ne.data.local.database

import androidx.room.TypeConverter
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.model.AccountType
import com.arzikina.ne.domain.model.BudgetPeriod
import com.arzikina.ne.domain.model.CategoryIcon
import com.arzikina.ne.domain.model.FeeType
import com.arzikina.ne.domain.model.LoanReason
import com.arzikina.ne.domain.model.LoanStatus
import com.arzikina.ne.domain.model.LoanType
import com.arzikina.ne.domain.model.OccurrenceStatus
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.RecurringFrequency
import com.arzikina.ne.domain.model.RepaymentMode
import com.arzikina.ne.domain.model.SecurityQuestion
import com.arzikina.ne.domain.model.TransactionType

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
}
