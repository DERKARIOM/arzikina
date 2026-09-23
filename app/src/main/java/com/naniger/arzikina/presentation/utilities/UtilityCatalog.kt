package com.naniger.arzikina.presentation.utilities

import com.naniger.arzikina.R

/**
 * Source UNIQUE de la liste des utilitaires — évite que le bloc du Dashboard
 * ([com.naniger.arzikina.presentation.dashboard.DashboardFragment]) et l'écran complet
 * ([AllUtilitiesFragment]) ne divergent en dupliquant chacun leur propre liste.
 *
 * Aujourd'hui les deux écrans affichent la totalité de [all] (7 entrées). Quand
 * d'autres utilitaires seront ajoutés (calculateur d'intérêts, convertisseur de devises,
 * rappels...), il faudra probablement que le Dashboard n'en affiche plus qu'une sélection
 * restreinte (les plus utilisés) plutôt que la totalité — à ce moment-là, ajouter ici un
 * deuxième accesseur (ex. `featured`) sans changer [AllUtilitiesFragment], qui continuera de
 * afficher [all] en intégralité.
 */
object UtilityCatalog {
    fun all(): List<UtilityItem> = listOf(
        UtilityItem(
            iconRes = R.drawable.categorie_24,
            titleRes = R.string.more_menu_categories,
            destinationId = R.id.categoriesFragment
        ),
        UtilityItem(
            iconRes = R.drawable.investissement_24,
            titleRes = R.string.utility_loans_title,
            destinationId = R.id.loansFragment
        ),
        UtilityItem(
            iconRes = R.drawable.ic_planifications,
            titleRes = R.string.utility_recurring_transactions_title,
            destinationId = R.id.recurringTransactionsFragment
        ),
        UtilityItem(
            iconRes = R.drawable.ic_financial_plan_utility_24,
            titleRes = R.string.utility_financial_plans_title,
            destinationId = R.id.financialPlansFragment
        ),
        UtilityItem(
            iconRes = R.drawable.sauvegarde,
            titleRes = R.string.utility_backup_title,
            destinationId = R.id.backupFragment
        ),
        UtilityItem(
            iconRes = R.drawable.ic_receipt_long_24,
            titleRes = R.string.utility_receipts_title,
            destinationId = R.id.receiptsFragment
        ),
        // Cahier des charges "Marketplace personnelle" — voir
        // presentation/utilities/marketplace/MarketplaceFragment. ic_storefront_24 déjà utilisée
        // par l'état vide de cet écran (voir fragment_marketplace.xml) : même icône, cohérente
        // partout où cette fonctionnalité apparaît.
        UtilityItem(
            iconRes = R.drawable.ic_storefront_24,
            titleRes = R.string.utility_marketplace_title,
            destinationId = R.id.marketplaceFragment
        )
    )
}
