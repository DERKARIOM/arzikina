package com.arzikina.ne.presentation.utilities

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes

/**
 * Une entrée du bloc "Utilitaires" (voir [UtilityTileAdapter]) : un raccourci vers une
 * destination du graphe de navigation, affiché soit en tuile horizontale sur le Dashboard, soit
 * en grille sur l'écran "Tous les utilitaires" — même donnée, deux présentations selon le
 * contexte (voir [UtilityTileAdapter], réutilisé par les deux écrans).
 *
 * Même principe que [com.arzikina.ne.presentation.more.MoreMenuItem] (icône + titre +
 * destination), volontairement DUPLIQUÉ plutôt que partagé entre les deux packages : "Autre" et
 * "Utilitaires" sont deux fonctionnalités indépendantes qui n'ont pas vocation à évoluer
 * ensemble, malgré cette forme actuellement identique.
 *
 * Ajouter un futur utilitaire (calculateur d'intérêts, convertisseur de devises, rappels...) se
 * fait en ajoutant une entrée dans la liste de [com.arzikina.ne.presentation.dashboard.DashboardFragment]
 * et/ou [AllUtilitiesFragment], sans toucher à ce modèle ni à [UtilityTileAdapter] (voir
 * instructions projet, "évolutivité sans refonte majeure").
 *
 * @param descriptionRes non affiché pour l'instant par [UtilityTileAdapter] (les tuiles
 * n'affichent qu'icône + titre, voir la maquette) — réservé pour un futur écran qui en aurait
 * besoin (ex. une liste détaillée), sans avoir à changer ce modèle à ce moment-là.
 * @param badgeCount pastille de comptage optionnelle (voir cahier des charges "Gestion automatique
 * des transactions planifiées", section Dashboard) — `null`/`0` = masquée. [UtilityCatalog.all]
 * renvoie toujours `null` ici (catalogue statique) : c'est à l'écran appelant (voir
 * `DashboardFragment.render`) de reconstruire la liste avec la valeur à jour pour l'entrée
 * concernée, [UtilityTileAdapter] se contentant d'afficher ce qu'on lui donne.
 */
data class UtilityItem(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @IdRes val destinationId: Int,
    @StringRes val descriptionRes: Int? = null,
    val badgeCount: Int? = null
)
