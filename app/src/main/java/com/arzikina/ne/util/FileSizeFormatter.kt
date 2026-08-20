package com.arzikina.ne.util

import java.util.Locale

/**
 * Formatage lisible d'une taille de fichier en octets — cahier des charges "Gestion des reçus",
 * section 5 : affichage de [com.arzikina.ne.domain.model.Receipt.fileSize] sur chaque carte.
 *
 * Unités codées en dur ("o"/"Ko"/"Mo", jamais via `strings.xml`) : même convention que
 * [Money] pour le symbole monétaire (voir `SupportedCurrency`) — une unité de mesure n'est pas un
 * texte d'interface à traduire au sens propre, cohérent avec le reste du projet.
 */
object FileSizeFormatter {
    private const val KILO = 1024.0
    private const val MEGA = KILO * 1024.0

    fun format(bytes: Long): String = when {
        bytes < KILO -> "$bytes o"
        bytes < MEGA -> String.format(Locale.FRENCH, "%.1f Ko", bytes / KILO)
        else -> String.format(Locale.FRENCH, "%.1f Mo", bytes / MEGA)
    }
}
