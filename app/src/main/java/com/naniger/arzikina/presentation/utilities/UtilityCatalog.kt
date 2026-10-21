package com.naniger.arzikina.presentation.utilities

import com.naniger.arzikina.R

/**
 * Source UNIQUE de la liste des utilitaires — évite que le bloc du Dashboard
 * ([com.naniger.arzikina.presentation.dashboard.DashboardFragment]) et l'écran complet
 * ([AllUtilitiesFragment]) ne divergent en dupliquant chacun leur propre liste.
 *
 * Aujourd'hui les deux écrans affichent la totalité de [all] (9 entrées). Quand
 * d'autres utilitaires seront ajoutés (calculateur d'intérêts, convertisseur de devises,
 * rappels...), il faudra probablement que le Dashboard n'en affiche plus qu'une sélection
 * restreinte (les plus utilisés) plutôt que la totalité — à ce moment-là, ajouter ici un
 * deuxième accesseur (ex. `featured`) sans changer [AllUtilitiesFragment], qui continuera de
 * afficher [all] en intégralité.
 */
object UtilityCatalog {
    fun all(): List<UtilityItem> = listOf(
        // « Gestion de reçus » : reçus ÉMIS par l'utilisateur (formulaire, aperçu, PDF). Distinct de
        // l'entrée « Justificatifs » (import de PDF reçus d'ailleurs). Placée en PREMIER et marquée « NEW ».
        UtilityItem(
            iconRes = R.drawable.ic_receipt_issue_24,
            titleRes = R.string.utility_issued_receipts_title,
            destinationId = R.id.issuedReceiptHistoryFragment,
            descriptionRes = R.string.utility_issued_receipts_description,
            isNew = true
        ),
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
        // Plus d'entrée « Épargne » : un objectif d'épargne est désormais un TYPE de compte
        // (AccountType.SAVINGS_GOAL), créé et géré depuis l'écran Comptes comme tout compte.
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
        ),
        // Console web : QR code + lien de connexion (voir presentation/utilities/webapp).
        UtilityItem(
            iconRes = R.drawable.ic_web_24,
            titleRes = R.string.utility_web_app_title,
            destinationId = R.id.webAppFragment
        )
    )
}
