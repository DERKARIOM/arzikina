package com.naniger.arzikina.presentation.components

import android.content.Context
import androidx.annotation.StringRes
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.Category
import com.naniger.arzikina.domain.model.DefaultAccountKey
import com.naniger.arzikina.domain.model.SystemCategoryKey
import com.naniger.arzikina.domain.model.TransactionType

/**
 * Affichage traduit des noms créés par Arzikina (catégories et comptes par défaut, devises) —
 * chantier i18n, étape 4. SEUL endroit qui associe une clé du domaine à un texte de `strings.xml`.
 *
 * À appeler au moment du RENDU (Fragment, Adapter), avec le `Context` de la vue : la traduction
 * suit ainsi toujours la langue courante, y compris juste après un changement de langue. Ne jamais
 * stocker un nom traduit dans un `StateFlow` de ViewModel (il survit au changement de langue) —
 * voir [DefaultNameLocalizer] pour les rares besoins côté ViewModel.
 *
 * Les noms saisis par l'utilisateur (catégorie « Dépenses maman », compte « Orange Money perso »)
 * n'ont pas de clé : ils sont renvoyés tels quels, jamais traduits.
 */
fun Category.displayName(context: Context): String =
    systemKey?.let { context.getString(it.labelRes) } ?: name

fun Account.displayName(context: Context): String =
    defaultKey?.let { context.getString(it.labelRes) } ?: name

/** Pour les écrans qui n'ont que le nom et le type d'une catégorie (ex. modèles de transaction). */
fun Context.categoryDisplayName(name: String, type: TransactionType): String =
    SystemCategoryKey.of(name, type)?.let { getString(it.labelRes) } ?: name

@get:StringRes
val SystemCategoryKey.labelRes: Int
    get() = when (this) {
        SystemCategoryKey.SALARY -> R.string.category_default_salary
        SystemCategoryKey.OTHER_INCOME -> R.string.category_default_other_income
        SystemCategoryKey.FOOD -> R.string.category_default_food
        SystemCategoryKey.TRANSPORT -> R.string.category_default_transport
        SystemCategoryKey.HEALTH -> R.string.category_default_health
        SystemCategoryKey.SHOPPING -> R.string.category_default_shopping
        SystemCategoryKey.GIFTS -> R.string.category_default_gifts
        SystemCategoryKey.GIFTS_RECEIVED -> R.string.category_default_gifts_received
        SystemCategoryKey.INTERNET -> R.string.category_default_internet
        SystemCategoryKey.WATER -> R.string.category_default_water
        SystemCategoryKey.ELECTRICITY -> R.string.category_default_electricity
        SystemCategoryKey.EDUCATION -> R.string.category_default_education
        SystemCategoryKey.HOME -> R.string.category_default_home
        SystemCategoryKey.OTHER_EXPENSE -> R.string.category_default_other_expense
        SystemCategoryKey.LOAN_DISBURSEMENT_LENT -> R.string.category_system_loan_disbursement_lent
        SystemCategoryKey.LOAN_REPAYMENT_LENT -> R.string.category_system_loan_repayment_lent
        SystemCategoryKey.LOAN_DISBURSEMENT_BORROWED -> R.string.category_system_loan_disbursement_borrowed
        SystemCategoryKey.LOAN_REPAYMENT_BORROWED -> R.string.category_system_loan_repayment_borrowed
        SystemCategoryKey.FEES -> R.string.category_system_fees
        SystemCategoryKey.SHOP_SALES -> R.string.category_system_shop_sales
        SystemCategoryKey.SHOP_REFUNDS -> R.string.category_system_shop_refunds
    }

@get:StringRes
val DefaultAccountKey.labelRes: Int
    get() = when (this) {
        DefaultAccountKey.CASH -> R.string.account_default_cash
        DefaultAccountKey.BANK -> R.string.account_default_bank
        DefaultAccountKey.MOBILE_MONEY -> R.string.account_default_mobile_money
        DefaultAccountKey.SAVINGS -> R.string.account_default_savings
        DefaultAccountKey.WALLET -> R.string.account_default_wallet
        DefaultAccountKey.SHOP -> R.string.account_default_shop
    }
