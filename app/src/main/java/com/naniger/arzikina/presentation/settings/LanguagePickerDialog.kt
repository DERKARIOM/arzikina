package com.naniger.arzikina.presentation.settings

import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.AppLanguage

/**
 * Sélecteur de langue (Paramètres → Langue). Extrait de [SettingsFragment], déjà volumineux, pour
 * rester réutilisable (un futur écran d'accueil pourra proposer le même choix).
 *
 * Même ergonomie que les sélecteurs de thème et de devise : liste à choix unique, qui se ferme dès
 * qu'une option est touchée. Aucun rappel n'est déclenché si l'utilisateur retouche la langue déjà
 * active : on évite de reconstruire l'écran pour rien.
 */
fun Fragment.showLanguagePicker(current: AppLanguage, onSelected: (AppLanguage) -> Unit) {
    val options = AppLanguage.entries
    val labels = options.map { getString(it.pickerOptionRes()) }.toTypedArray()

    MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.settings_dialog_language_title)
        .setSingleChoiceItems(labels, options.indexOf(current)) { dialog, position ->
            dialog.dismiss()
            val chosen = options[position]
            if (chosen != current) onSelected(chosen)
        }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}
