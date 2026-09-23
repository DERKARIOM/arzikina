package com.naniger.arzikina.presentation.profile

import android.os.Bundle
import android.view.View
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.BottomSheetChangeProfilePhotoBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * "Modifier la photo" (cahier des charges "Gestion de la photo de profil") — ouvert par
 * [ProfileFragment] au tap sur l'avatar. Communique son résultat via la Fragment Result API
 * ([REQUEST_KEY]/[RESULT_ACTION]) plutôt qu'un callback lambda direct : survit à une recréation de
 * ce Fragment (ex. rotation de l'écran) sans référence pendante vers un Fragment détruit — même
 * principe déjà utilisé ailleurs dans le projet pour la communication entre Fragments.
 *
 * Aucun ViewModel : ce bottom sheet n'a aucun état propre à conserver (juste 2 ou 3 lignes
 * cliquables), toute la logique (permissions, recadrage, sauvegarde) reste dans
 * [ProfileFragment]/[ProfileViewModel].
 */
class ChangeProfilePhotoBottomSheet : BottomSheetDialogFragment(R.layout.bottom_sheet_change_profile_photo) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = BottomSheetChangeProfilePhotoBinding.bind(view)

        val hasExistingPhoto = arguments?.getBoolean(ARG_HAS_EXISTING_PHOTO) ?: false
        binding.deletePhotoRow.visibility = if (hasExistingPhoto) View.VISIBLE else View.GONE

        binding.takePhotoRow.setOnClickListener { sendResultAndDismiss(ACTION_TAKE_PHOTO) }
        binding.chooseFromGalleryRow.setOnClickListener { sendResultAndDismiss(ACTION_CHOOSE_FROM_GALLERY) }
        binding.deletePhotoRow.setOnClickListener { sendResultAndDismiss(ACTION_DELETE_PHOTO) }
    }

    private fun sendResultAndDismiss(action: String) {
        setFragmentResult(REQUEST_KEY, Bundle(1).apply { putString(RESULT_ACTION, action) })
        dismiss()
    }

    companion object {
        const val REQUEST_KEY = "change_profile_photo_action"
        const val RESULT_ACTION = "result_action"
        const val ACTION_TAKE_PHOTO = "take_photo"
        const val ACTION_CHOOSE_FROM_GALLERY = "choose_from_gallery"
        const val ACTION_DELETE_PHOTO = "delete_photo"

        private const val ARG_HAS_EXISTING_PHOTO = "has_existing_photo"
        private const val TAG = "change_profile_photo_bottom_sheet"

        /** [hasExistingPhoto] : masque la ligne "Supprimer la photo" quand il n'y a rien à
         *  supprimer (voir la doc du layout). */
        fun show(fragmentManager: FragmentManager, hasExistingPhoto: Boolean) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            ChangeProfilePhotoBottomSheet()
                .apply { arguments = Bundle(1).apply { putBoolean(ARG_HAS_EXISTING_PHOTO, hasExistingPhoto) } }
                .show(fragmentManager, TAG)
        }
    }
}
