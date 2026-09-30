package com.naniger.arzikina.presentation.update

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.naniger.arzikina.R

/**
 * Proposition « Nouvelle version disponible » (mise à jour Flexible, non urgente), affichée AVANT le
 * flux Google Play. DialogFragment plutôt qu'un simple AlertDialog : il survit à la rotation sans
 * être dupliqué, et son choix revient par la Fragment Result API à `PlayStoreUpdateManager`, même
 * si l'Activity a été recréée entre-temps.
 *
 * Retour arrière ou toucher hors du dialogue = « Plus tard » : l'utilisateur n'est jamais bloqué.
 */
class InAppUpdateOfferDialogFragment : DialogFragment() {

    private val versionCode: Int get() = requireArguments().getInt(ARG_VERSION_CODE)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.in_app_update_available_title)
            .setMessage(getString(R.string.in_app_update_available_message, getString(R.string.app_name)))
            .setPositiveButton(R.string.in_app_update_action_update) { _, _ -> deliver(Choice.UPDATE) }
            .setNegativeButton(R.string.in_app_update_action_later) { _, _ -> deliver(Choice.LATER) }
            .create()

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        deliver(Choice.LATER)
    }

    private fun deliver(choice: Choice) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            bundleOf(RESULT_CHOICE to choice.name, RESULT_VERSION_CODE to versionCode)
        )
    }

    enum class Choice { UPDATE, LATER }

    companion object {
        const val TAG = "in_app_update_offer"
        const val REQUEST_KEY = "in_app_update_offer_result"
        const val RESULT_CHOICE = "choice"
        const val RESULT_VERSION_CODE = "version_code"
        private const val ARG_VERSION_CODE = "version_code"

        /** N'affiche rien si le dialogue est déjà à l'écran ou si l'état est déjà sauvegardé
         * (Activity en cours d'arrêt) : jamais deux dialogues identiques. */
        fun showIfAbsent(fragmentManager: FragmentManager, versionCode: Int) {
            if (fragmentManager.isStateSaved || fragmentManager.findFragmentByTag(TAG) != null) return
            InAppUpdateOfferDialogFragment()
                .apply { arguments = bundleOf(ARG_VERSION_CODE to versionCode) }
                .show(fragmentManager, TAG)
        }

        fun isShowing(fragmentManager: FragmentManager): Boolean =
            fragmentManager.findFragmentByTag(TAG) != null
    }
}
