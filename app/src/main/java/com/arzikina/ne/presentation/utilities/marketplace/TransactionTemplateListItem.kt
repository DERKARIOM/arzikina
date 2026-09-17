package com.arzikina.ne.presentation.utilities.marketplace

import com.arzikina.ne.domain.model.CategoryIcon
import com.arzikina.ne.domain.model.TransactionType

/**
 * Modèle d'AFFICHAGE d'un [com.arzikina.ne.domain.model.TransactionTemplate] pour la liste
 * "Marketplace personnelle" — combine le modèle avec le nom/l'icône/la couleur de sa catégorie
 * (voir [MarketplaceViewModel.uiState]) : le domaine ne connaît que `categoryId`, jamais ces
 * détails de présentation (voir `Category`/`CategoryIconMapper`).
 *
 * [currencyCode] : résolu depuis le compte associé ([com.arzikina.ne.domain.model.Account]), pour
 * afficher le montant avec le bon symbole (voir `Money.format`) — même raisonnement que
 * `LoanListItem`/`RecurringTransactionFormFragment`.
 *
 * [categoryId] conservé À CÔTÉ de [categoryName]/[categoryIcon]/[categoryColorArgb] (pas seulement
 * ces derniers) : nécessaire pour préremplir `TransactionFormFragment` (argument `presetCategoryId`,
 * voir cahier des charges "Marketplace personnelle" section 4, bouton "Acheter") — un id, pas un nom.
 *
 * [defaultHour]/[defaultMinute] : voir `TransactionTemplate.defaultHour`/`defaultMinute` — repris
 * tels quels (`null` = pas d'heure par défaut), utilisés par `MarketplaceFragment.onBuyClicked` pour
 * calculer `presetDateTimeMillis`.
 */
data class TransactionTemplateListItem(
    val id: Long,
    val name: String,
    val type: TransactionType,
    val amount: Long,
    val currencyCode: String,
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: CategoryIcon,
    val categoryColorArgb: Long,
    val accountId: Long,
    val description: String,
    val isFavorite: Boolean,
    val defaultHour: Int?,
    val defaultMinute: Int?
)
