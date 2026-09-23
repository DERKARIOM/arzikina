package com.naniger.arzikina.presentation.profile

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil3.load
import com.naniger.arzikina.databinding.DialogProfilePhotoPreviewBinding
import kotlinx.coroutines.launch

/**
 * "Aperçu de votre photo de profil" (cahier des charges "Gestion de la photo de profil", section
 * "Aperçu et validation") — ouvert par [ProfileFragment] juste après un recadrage réussi
 * ([com.canhub.cropper.CropImageContract], voir sa doc de tête). Affiche le résultat DÉJÀ recadré
 * ET optimisé (carré, résolution/compression déjà appliquées par la bibliothèque de recadrage,
 * voir [ProfileFragment.launchCrop]) : ce dialogue ne fait plus aucun traitement d'image,
 * uniquement l'aperçu et la confirmation.
 *
 * Partage le [ProfileViewModel] du Fragment parent ([requireParentFragment], voir
 * [androidx.fragment.app.viewModels]) plutôt qu'un ViewModel dédié : aucun état propre à ce
 * dialogue au-delà de l'URI reçue en argument, la sauvegarde elle-même
 * ([ProfileViewModel.confirmNewPhoto]) doit rester possible même si ce dialogue est fermé entre
 * temps (ex. rotation), même principe que [ChangeProfilePhotoBottomSheet].
 *
 * Fond du Dialog transparent (voir [onCreateDialog]) — même principe que
 * `RecurringOccurrenceEditDialogFragment` : seule la `MaterialCardView` du layout dessine un
 * arrière-plan.
 */
class ProfilePhotoPreviewDialogFragment : DialogFragment() {

    private val viewModel: ProfileViewModel by viewModels({ requireParentFragment() })
    private var binding: DialogProfilePhotoPreviewBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val viewBinding = DialogProfilePhotoPreviewBinding.inflate(inflater, container, false)
        binding = viewBinding
        return viewBinding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        isCancelable = true
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = binding ?: return

        val croppedUri = requireNotNull(arguments?.let { BundleCompat.getParcelable(it, ARG_CROPPED_URI, Uri::class.java) }) {
            "ProfilePhotoPreviewDialogFragment nécessite $ARG_CROPPED_URI."
        }
        viewBinding.previewImage.load(croppedUri)

        viewBinding.cancelButton.setOnClickListener { dismissAllowingStateLoss() }
        viewBinding.confirmButton.setOnClickListener {
            val bytes = requireContext().contentResolver.openInputStream(croppedUri)?.use { it.readBytes() }
            if (bytes != null) {
                viewModel.confirmNewPhoto(bytes)
            } else {
                dismissAllowingStateLoss()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.photoUiState.collect { state -> render(state) } }
                launch {
                    viewModel.photoEvents.collect { event ->
                        when (event) {
                            ProfilePhotoEvent.PhotoSaved -> dismissAllowingStateLoss()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun render(state: ProfilePhotoUiState) {
        val binding = binding ?: return
        binding.previewLoadingIndicator.visibility = if (state.isProcessing) View.VISIBLE else View.GONE
        binding.cancelButton.isEnabled = !state.isProcessing
        binding.confirmButton.isEnabled = !state.isProcessing
    }

    companion object {
        private const val ARG_CROPPED_URI = "cropped_uri"
        private const val TAG = "profile_photo_preview"

        /** Point d'entrée UNIQUE (voir [ProfileFragment.cropImage]) — [croppedUri] est TOUJOURS le
         *  résultat direct du recadrage, jamais une URI choisie par l'utilisateur AVANT recadrage. */
        fun show(fragmentManager: FragmentManager, croppedUri: Uri) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            ProfilePhotoPreviewDialogFragment()
                .apply { arguments = Bundle(1).apply { putParcelable(ARG_CROPPED_URI, croppedUri) } }
                .show(fragmentManager, TAG)
        }
    }
}
