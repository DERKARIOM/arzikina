package com.naniger.arzikina.presentation.profile.crop

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.canhub.cropper.CropImageView
import com.google.android.material.snackbar.Snackbar
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentProfilePhotoCropBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Écran « Recadrer la photo » d'Arzikina, basé directement sur [CropImageView] (recommandation de
 * la bibliothèque image-cropper, dont `CropImageActivity`/`CropImageContract` sont dépréciés
 * depuis la version 4.6.0).
 *
 * Réglages du recadrage (voir `fragment_profile_photo_crop.xml`) : cadre carré fixe, grille,
 * déplacement à un doigt et zoom à deux doigts, sans rotation ni miroir. L'orientation EXIF des
 * photos prises à l'appareil photo est corrigée par [CropImageView] au chargement.
 *
 * Résultat renvoyé à l'écran précédent (`ProfileFragment`) via le `SavedStateHandle` de son entrée
 * de pile de navigation (API officielle de Navigation) :
 * - [RESULT_CROPPED_URI] : photo recadrée (512 px max, JPEG), à confirmer dans l'aperçu ;
 * - [RESULT_LOAD_FAILED] : la photo source n'a pas pu être ouverte (fichier illisible, format non
 *   supporté…) — `ProfileFragment` affiche le message d'erreur.
 * Annuler (flèche retour) ne renvoie rien.
 */
@AndroidEntryPoint
class ProfilePhotoCropFragment : Fragment(R.layout.fragment_profile_photo_crop) {

    private val viewModel: ProfilePhotoCropViewModel by viewModels()
    private var binding: FragmentProfilePhotoCropBinding? = null

    private val onImageLoaded = object : CropImageView.OnSetImageUriCompleteListener {
        override fun onSetImageUriComplete(view: CropImageView, uri: Uri, error: Exception?) {
            if (error == null) viewModel.onImageLoaded() else finishWithResult(RESULT_LOAD_FAILED, true)
        }
    }

    private val onCropped = object : CropImageView.OnCropImageCompleteListener {
        override fun onCropImageComplete(view: CropImageView, result: CropImageView.CropResult) {
            val croppedUri = result.uriContent
            if (result.isSuccessful && croppedUri != null) {
                finishWithResult(RESULT_CROPPED_URI, croppedUri)
            } else {
                viewModel.onCropFailed()
                binding?.let { Snackbar.make(it.root, R.string.profile_photo_error_message, Snackbar.LENGTH_LONG).show() }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentProfilePhotoCropBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        viewBinding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_confirm_crop) {
                confirmCrop()
                true
            } else {
                false
            }
        }

        viewBinding.cropImageView.setOnSetImageUriCompleteListener(onImageLoaded)
        viewBinding.cropImageView.setOnCropImageCompleteListener(onCropped)
        // Après une rotation de l'écran, CropImageView restaure elle-même l'image et le cadrage
        // (état sauvegardé de la vue) : la recharger ici effacerait le cadrage de l'utilisateur.
        if (savedInstanceState == null) {
            viewBinding.cropImageView.setImageUriAsync(viewModel.sourceUri)
        } else {
            viewModel.onImageLoaded()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    override fun onDestroyView() {
        binding?.cropImageView?.setOnSetImageUriCompleteListener(null)
        binding?.cropImageView?.setOnCropImageCompleteListener(null)
        super.onDestroyView()
        binding = null
    }

    private fun render(state: ProfilePhotoCropUiState) {
        val binding = binding ?: return
        binding.progressIndicator.visibility = if (state.isBusy) View.VISIBLE else View.GONE
        binding.toolbar.menu.findItem(R.id.action_confirm_crop)?.isEnabled = !state.isBusy
    }

    private fun confirmCrop() {
        if (!viewModel.onCropRequested()) return
        binding?.cropImageView?.croppedImageAsync(
            saveCompressFormat = Bitmap.CompressFormat.JPEG,
            saveCompressQuality = OUTPUT_JPEG_QUALITY,
            reqWidth = OUTPUT_SIZE_PX,
            reqHeight = OUTPUT_SIZE_PX,
            options = CropImageView.RequestSizeOptions.RESIZE_INSIDE
        )
    }

    private fun <T> finishWithResult(key: String, value: T) {
        val navController = findNavController()
        navController.previousBackStackEntry?.savedStateHandle?.set(key, value)
        navController.popBackStack()
    }

    companion object {
        const val RESULT_CROPPED_URI = "profile_photo_crop_result_uri"
        const val RESULT_LOAD_FAILED = "profile_photo_crop_load_failed"

        /** 512 px de côté : largement suffisant pour un avatar (jamais affiché à plus de ~200 dp)
         *  tout en restant léger pour la synchronisation. */
        private const val OUTPUT_SIZE_PX = 512
        private const val OUTPUT_JPEG_QUALITY = 85
    }
}
