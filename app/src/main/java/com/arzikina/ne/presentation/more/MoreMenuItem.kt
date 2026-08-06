package com.arzikina.ne.presentation.more

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes

/**
 * Une ligne de l'écran "Autre" (voir [MoreFragment]) : un raccourci vers une
 * destination du graphe de navigation qui n'est plus un onglet direct de la
 * Bottom Navigation (Budget, Catégories, Paramètres aujourd'hui).
 *
 * Ajouter une future entrée (Objectifs d'épargne, Sauvegarde, Export...) se
 * fait en complétant la liste dans [MoreFragment], sans toucher à l'adapter
 * ni au layout — voir instructions projet ("évolutivité sans refonte majeure").
 */
data class MoreMenuItem(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @IdRes val destinationId: Int
)
