package com.arzikina.ne.presentation.components

import android.content.Context
import com.arzikina.ne.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Dialogue de confirmation générique avant une action irréversible
 * (suppression, restauration d'une sauvegarde...) — équivalent Views de
 * l'ancien composant Compose `ConfirmDeleteDialog` (retiré, voir instructions
 * projet), remplacé ici par un simple appel à [MaterialAlertDialogBuilder]
 * plutôt qu'un composant dédié : il n'y a pas de state Compose à gérer côté
 * Views, un `AlertDialog` classique suffit et n'a pas besoin d'être réifié
 * en composant réutilisable au-delà de cette fonction utilitaire.
 */
object ConfirmDialogs {

    fun confirm(
        context: Context,
        title: String,
        message: String,
        confirmLabel: String = context.getString(R.string.action_delete),
        onConfirm: () -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(confirmLabel) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
