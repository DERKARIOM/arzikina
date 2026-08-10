package com.arzikina.ne.presentation.utilities

import com.arzikina.ne.R

/**
 * Source UNIQUE de la liste des utilitaires — évite que le bloc du Dashboard
 * ([com.arzikina.ne.presentation.dashboard.DashboardFragment]) et l'écran complet
 * ([AllUtilitiesFragment]) ne divergent en dupliquant chacun leur propre liste.
 *
 * Aujourd'hui les deux écrans affichent la totalité de [all] (seulement 3 entrées). Quand
 * d'autres utilitaires seront ajoutés (calculateur d'intérêts, convertisseur de devises,
 * rappels...), il faudra probablement que le Dashboard n'en affiche plus qu'une sélection
 * restreinte (les plus utilisés) plutôt que la totalité — à ce moment-là, ajouter ici un
 * deuxième accesseur (ex. `featured`) sans changer [AllUtilitiesFragment], qui continuera de
 * afficher [all] en intégralité.
 */
object UtilityCatalog {
    fun all(): List<UtilityItem> = listOf(
        UtilityItem(
            iconRes = R.drawable.ic_label_24,
            titleRes = R.string.more_menu_categories,
            destinationId = R.id.categoriesFragment
        ),
        UtilityItem(
            iconRes = R.drawable.ic_loan_24,
            titleRes = R.string.utility_loans_title,
            destinationId = R.id.loansFragment
        ),
        UtilityItem(
            iconRes = R.drawable.ic_cloud_backup_24,
            titleRes = R.string.utility_backup_title,
            destinationId = R.id.backupFragment
        )
    )
}
