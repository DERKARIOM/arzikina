package com.arzikina.ne.data.local.database

import androidx.room.TypeConverter
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.model.BudgetPeriod
import com.arzikina.ne.domain.model.CategoryIcon
import com.arzikina.ne.domain.model.PaymentMethod
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
}
