package com.naniger.arzikina.presentation.categories

import com.naniger.arzikina.domain.model.CategoryIcon

/**
 * Quelles icônes montrer dans le sélecteur : par défaut seulement les plus courantes (les premières
 * de la liste), le reste derrière « Voir plus ». Logique pure, sans Android, testée unitairement.
 */
object CategoryIconVisibility {

    /** Nombre de lignes montrées tant que la liste est repliée. */
    const val COLLAPSED_ROWS = 2

    /** Icônes montrées repliées pour [columns] colonnes. */
    fun collapsedCount(columns: Int): Int = columns.coerceAtLeast(1) * COLLAPSED_ROWS

    /** Un bouton « Voir plus » n'a de sens que s'il reste des icônes cachées. */
    fun needsToggle(total: Int, columns: Int): Boolean = total > collapsedCount(columns)

    /**
     * Liste à afficher. Repliée, l'icône [selected] est TOUJOURS visible : si elle n'est pas dans les
     * premières, elle prend la dernière place (jamais de sélection cachée).
     */
    fun visible(all: List<CategoryIcon>, selected: CategoryIcon, expanded: Boolean, columns: Int): List<CategoryIcon> {
        val limit = collapsedCount(columns)
        if (expanded || all.size <= limit) return all
        val head = all.take(limit)
        return if (selected in head) head else head.dropLast(1) + selected
    }

    /** Au premier affichage, une icône déjà choisie mais cachée (modification) déplie la liste. */
    fun startsExpanded(all: List<CategoryIcon>, selected: CategoryIcon, columns: Int): Boolean =
        all.indexOf(selected) >= collapsedCount(columns)
}
