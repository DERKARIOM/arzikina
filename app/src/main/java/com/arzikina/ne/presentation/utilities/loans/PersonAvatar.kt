package com.arzikina.ne.presentation.utilities.loans

import com.arzikina.ne.presentation.components.ColorPalette
import kotlin.math.absoluteValue

/**
 * Avatar d'une [com.arzikina.ne.domain.model.Person] : couleur + initiale calculées à
 * l'affichage à partir de son nom (voir la doc de `Person`, "évite les colonnes inutiles" — pas
 * de champ dédié en base). Fonctions pures et déterministes : la même personne a toujours le
 * même avatar, sans jamais rien stocker.
 */

/**
 * Couleur (ARGB, voir [com.arzikina.ne.domain.model.Account.colorArgb] pour la même convention)
 * choisie dans [ColorPalette.COLORS] — réutilise la palette déjà proposée pour les comptes/
 * catégories plutôt que d'en dupliquer une nouvelle, juste avec un choix déterministe (hachage du
 * nom) au lieu d'une sélection manuelle.
 */
fun personAvatarColorArgb(name: String): Long {
    val index = name.trim().lowercase().hashCode().absoluteValue % ColorPalette.COLORS.size
    return ColorPalette.COLORS[index]
}

/** Première lettre du nom, majuscule ; "?" si [name] est vide (ne devrait pas arriver, [Person.name]
 * étant obligatoire, mais évite un crash sur une donnée malformée). */
fun personAvatarInitial(name: String): String =
    name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
