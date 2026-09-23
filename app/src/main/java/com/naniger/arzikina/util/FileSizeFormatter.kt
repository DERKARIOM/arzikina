package com.naniger.arzikina.util

import android.content.Context
import com.naniger.arzikina.R
import java.text.NumberFormat

/**
 * Formatage lisible d'une taille de fichier en octets — cahier des charges "Gestion des reçus",
 * section 5 : affichage de [com.naniger.arzikina.domain.model.Receipt.fileSize] sur chaque carte.
 *
 * Unités et séparateur décimal dans la langue de l'interface (chantier i18n, étape 3) :
 * « 1,5 Mo » en français, « 1.5 MB » en anglais. Les unités viennent de `strings.xml`
 * (`file_size_*`) ; les calculs (base 1024) sont inchangés.
 */
object FileSizeFormatter {
    private const val KILO = 1024.0
    private const val MEGA = KILO * 1024.0

    fun format(context: Context, bytes: Long): String {
        if (bytes < KILO) return context.getString(R.string.file_size_bytes, bytes)
        val oneDecimal = NumberFormat.getNumberInstance(context.appLocale()).apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }
        return if (bytes < MEGA) {
            context.getString(R.string.file_size_kilobytes, oneDecimal.format(bytes / KILO))
        } else {
            context.getString(R.string.file_size_megabytes, oneDecimal.format(bytes / MEGA))
        }
    }
}
