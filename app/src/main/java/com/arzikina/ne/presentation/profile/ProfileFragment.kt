package com.arzikina.ne.presentation.profile

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import coil3.load
import coil3.request.crossfade
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentProfileBinding
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.presentation.components.NavAnimations
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Écran Profil : consultation/édition des informations (nom, e-mail,
 * téléphone, photo), accès aux écrans "Changer le mot de passe" et
 * "Modifier la question de sécurité", et déconnexion.
 *
 * Reçoit un clic depuis l'onglet "Autre" (voir MoreFragment) — un Toolbar
 * avec flèche retour classique, contrairement aux écrans d'authentification
 * (Connexion/Inscription/Mot de passe oublié) qui, eux, gèrent leur propre
 * navigation de façon spécifique.
 */
@AndroidEntryPoint
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val viewModel: ProfileViewModel by viewModels()
    private var binding: FragmentProfileBinding? = null

    /**
     * Lance l'écran de recadrage (voir [launchCrop]) — le résultat est TOUJOURS le fichier déjà
     * recadré/optimisé par la bibliothèque (jamais l'URI source choisie), ouvert directement dans
     * [ProfilePhotoPreviewDialogFragment] pour confirmation (cahier des charges "Aperçu et
     * validation"). `result.isSuccessful == false` couvre aussi bien une annulation qu'une erreur
     * réelle (voir [com.canhub.cropper.CropImageView.CropResult.error]) — un simple retour silencieux
     * suffit pour une annulation, un message n'est affiché que si [com.canhub.cropper.CropImageView.CropResult.error]
     * est non nul.
     */
    private val cropImage = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { croppedUri ->
                ProfilePhotoPreviewDialogFragment.show(childFragmentManager, croppedUri)
            }
        } else if (result.error != null) {
            binding?.let { Snackbar.make(it.root, R.string.profile_photo_error_message, Snackbar.LENGTH_LONG).show() }
        }
    }

    /**
     * Demandée UNIQUEMENT quand l'utilisateur choisit "Prendre une photo" (jamais au démarrage de
     * l'app ni à l'ouverture de cet écran) — voir cahier des charges "Gérer correctement les
     * permissions caméra". "Choisir dans la galerie" ne nécessite AUCUNE permission (voir
     * [launchCrop], sélecteur système "Photo Picker").
     */
    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCrop(includeCamera = true, includeGallery = false)
        } else {
            binding?.let { Snackbar.make(it.root, R.string.profile_photo_camera_permission_denied, Snackbar.LENGTH_LONG).show() }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentProfileBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        viewBinding.avatarContainer.setOnClickListener {
            ChangeProfilePhotoBottomSheet.show(
                childFragmentManager,
                hasExistingPhoto = viewModel.photoUiState.value.photoUri != null
            )
        }
        childFragmentManager.setFragmentResultListener(
            ChangeProfilePhotoBottomSheet.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle -> onChangePhotoActionSelected(bundle.getString(ChangeProfilePhotoBottomSheet.RESULT_ACTION)) }

        viewBinding.fullNameInput.doAfterTextChanged { viewModel.onFullNameChange(it?.toString().orEmpty()) }
        viewBinding.emailInput.doAfterTextChanged { viewModel.onEmailChange(it?.toString().orEmpty()) }
        viewBinding.phoneInput.doAfterTextChanged { viewModel.onPhoneNumberChange(it?.toString().orEmpty()) }
        viewBinding.saveButton.setOnClickListener { viewModel.save() }

        viewBinding.changePasswordRow.menuIcon.setImageResource(R.drawable.ic_lock_24)
        viewBinding.changePasswordRow.menuTitle.setText(R.string.profile_change_password_action)
        viewBinding.changePasswordRow.root.setOnClickListener {
            findNavController().navigate(R.id.changePasswordFragment, null, NavAnimations.push)
        }

        viewBinding.securityQuestionRow.menuIcon.setImageResource(R.drawable.ic_help_24)
        viewBinding.securityQuestionRow.menuTitle.setText(R.string.profile_security_question_action)
        viewBinding.securityQuestionRow.root.setOnClickListener {
            findNavController().navigate(R.id.securityQuestionFragment, null, NavAnimations.push)
        }

        viewBinding.biometricLockSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onBiometricLockToggle(isChecked)
        }

        viewBinding.logoutButton.setOnClickListener { confirmLogout() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.formState.collect { state -> render(state) } }
                launch { viewModel.photoUiState.collect { state -> renderPhoto(state) } }
                launch { viewModel.biometricLockState.collect { state -> renderBiometricLock(state) } }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    /**
     * Résultat du bottom sheet "Modifier la photo" (voir [ChangeProfilePhotoBottomSheet]) — reçu via
     * Fragment Result API plutôt qu'un callback direct (voir sa doc de tête).
     */
    private fun onChangePhotoActionSelected(action: String?) {
        when (action) {
            ChangeProfilePhotoBottomSheet.ACTION_TAKE_PHOTO -> onTakePhotoSelected()
            ChangeProfilePhotoBottomSheet.ACTION_CHOOSE_FROM_GALLERY -> launchCrop(includeCamera = false, includeGallery = true)
            ChangeProfilePhotoBottomSheet.ACTION_DELETE_PHOTO -> confirmDeletePhoto()
        }
    }

    private fun onTakePhotoSelected() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCrop(includeCamera = true, includeGallery = false)
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    /**
     * [includeCamera]/[includeGallery] sont TOUJOURS mutuellement exclusifs ici (jamais les deux à
     * `true` en même temps) : l'utilisateur a déjà choisi sa source dans
     * [ChangeProfilePhotoBottomSheet], la bibliothèque de recadrage n'a donc jamais besoin d'afficher
     * son propre sélecteur "Caméra/Galerie" interne.
     *
     * Options choisies pour une photo de profil (cahier des charges "Recadrage de la photo",
     * "Optimisation de l'image") :
     * - `fixAspectRatio`/`aspectRatioX`/`aspectRatioY` = carré ; `canChangeCropWindow = false` : la
     *   zone de recadrage reste un carré FIXE au centre, seule l'image en dessous bouge/zoome
     *   (déplacement + zoom, comme la quasi-totalité des sélecteurs de photo de profil).
     * - `outputRequestWidth`/`outputRequestHeight` + `RESIZE_INSIDE` : redimensionnement à une
     *   résolution raisonnable (512px) directement par la bibliothèque — inutile d'écrire un second
     *   passage de redimensionnement manuel ensuite.
     * - `outputCompressFormat`/`outputCompressQuality` : compression JPEG déjà appliquée à la
     *   sortie — le fichier lu par [ProfilePhotoPreviewDialogFragment] est donc déjà optimisé.
     * - L'orientation EXIF (photo prise à la verticale/horizontale) est corrigée par la
     *   bibliothèque elle-même à la lecture, AVANT le recadrage — jamais géré manuellement ici
     *   (cahier des charges "éviter les problèmes de rotation des photos prises avec la caméra").
     * - `allowRotation`/`allowFlipping = false` : cet écran ne propose QUE ce que demande le cahier
     *   des charges (déplacer, zoomer, aperçu en temps réel, Annuler/Valider) — pas de fonctions
     *   supplémentaires non demandées.
     */
    private fun launchCrop(includeCamera: Boolean, includeGallery: Boolean) {
        cropImage.launch(
            CropImageContractOptions(
                uri = null,
                cropImageOptions = CropImageOptions(
                    imageSourceIncludeCamera = includeCamera,
                    imageSourceIncludeGallery = includeGallery,
                    cropShape = CropImageView.CropShape.RECTANGLE,
                    fixAspectRatio = true,
                    aspectRatioX = 1,
                    aspectRatioY = 1,
                    // IMPORTANT : canChangeCropWindow = true est OBLIGATOIRE. La bibliothèque
                    // mappe directement ce flag sur `View.isEnabled` du cadre de recadrage
                    // (voir CropOverlayView.onTouchEvent, "if (isEnabled) ... else return false") :
                    // à false, PLUS AUCUN geste n'est traité (ni déplacement, ni redimensionnement,
                    // ni même le pinch-zoom), l'écran de recadrage devient totalement figé — c'est
                    // le bug "bloqué, aucune interaction possible" remonté par l'utilisateur.
                    // fixAspectRatio=true (ci-dessus) garantit à lui seul que le cadre reste carré
                    // pendant le déplacement/redimensionnement, canChangeCropWindow=true n'a donc
                    // aucun effet indésirable sur le format.
                    canChangeCropWindow = true,
                    multiTouchEnabled = true,
                    autoZoomEnabled = true,
                    guidelines = CropImageView.Guidelines.ON,
                    allowRotation = false,
                    allowFlipping = false,
                    outputCompressFormat = Bitmap.CompressFormat.JPEG,
                    outputCompressQuality = PROFILE_PHOTO_JPEG_QUALITY,
                    outputRequestWidth = PROFILE_PHOTO_TARGET_SIZE_PX,
                    outputRequestHeight = PROFILE_PHOTO_TARGET_SIZE_PX,
                    outputRequestSizeOptions = CropImageView.RequestSizeOptions.RESIZE_INSIDE,
                    activityTitle = getString(R.string.profile_photo_crop_title),
                    cropMenuCropButtonTitle = getString(R.string.profile_photo_crop_confirm_action),
                    // Filet de sécurité en plus de Theme.Arzikina.Cropper (voir AndroidManifest.xml) :
                    // garantit un contraste correct du bouton de validation/de la flèche retour
                    // quel que soit l'appareil/la résolution exacte du thème par la bibliothèque.
                    toolbarColor = ContextCompat.getColor(requireContext(), R.color.arzikina_primary),
                    toolbarTitleColor = ContextCompat.getColor(requireContext(), R.color.arzikina_on_primary),
                    toolbarBackButtonColor = ContextCompat.getColor(requireContext(), R.color.arzikina_on_primary),
                    toolbarTintColor = ContextCompat.getColor(requireContext(), R.color.arzikina_on_primary)
                )
            )
        )
    }

    /** Suppression = action irréversible pour l'utilisateur (retour à l'avatar par défaut, à
     *  refaire depuis zéro) : confirmation avant, même principe que [confirmLogout]. */
    private fun confirmDeletePhoto() {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.profile_photo_delete_confirm_title),
            message = getString(R.string.profile_photo_delete_confirm_message),
            confirmLabel = getString(R.string.profile_photo_delete_action),
            onConfirm = { viewModel.deletePhoto() }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    /**
     * La déconnexion n'est pas instantanément réversible du point de vue de
     * l'utilisateur (il faudra retaper son mot de passe) : une confirmation
     * évite un tap accidentel, sans pour autant sur-dramatiser une action qui
     * ne supprime aucune donnée (voir le message du dialogue).
     */
    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.profile_logout_confirm_title)
            .setMessage(R.string.profile_logout_confirm_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.profile_logout_action) { _, _ -> viewModel.logout() }
            .show()
    }

    private fun render(state: ProfileFormState) {
        val binding = binding ?: return

        binding.usernameText.text = getString(R.string.profile_username_format, state.username)

        if (binding.fullNameInput.text?.toString() != state.fullName) binding.fullNameInput.setText(state.fullName)
        binding.fullNameLayout.error = state.fullNameError?.let { getString(it) }

        if (binding.emailInput.text?.toString() != state.email) binding.emailInput.setText(state.email)
        binding.emailLayout.error = state.emailError?.let { getString(it) }

        if (binding.phoneInput.text?.toString() != state.phoneNumber) binding.phoneInput.setText(state.phoneNumber)

        binding.saveButton.isEnabled = !state.isSaving
        binding.saveButton.text = if (state.isSaving) "" else getString(R.string.profile_save_action)
        binding.progressIndicator.visibility = if (state.isSaving) View.VISIBLE else View.GONE
    }

    /**
     * Avatar par défaut (`ic_person_24`) dès que [ProfilePhotoUiState.photoUri] est `null` — jamais
     * laissé tel quel avec une ancienne image chargée (ex. juste après une suppression).
     *
     * `imageTintList` retiré/réappliqué explicitement ici : `app:tint="?attr/colorOnSurfaceVariant"`
     * dans fragment_profile.xml est prévu UNIQUEMENT pour la silhouette [R.drawable.ic_person_24]
     * (icône monochrome) — laissé tel quel, Android l'applique aussi à une vraie photo chargée par
     * Coil (filtre de couleur SRC_IN sur toute l'image, opaque), l'écrasant en un simple carré de
     * couleur unie (bug "photo affichée en carré blanc"). Même correctif que
     * [com.arzikina.ne.presentation.dashboard.DashboardFragment.renderUserHeader].
     *
     * `padding` retiré/réappliqué de la même façon : `android:padding="@dimen/spacing_m"` (XML)
     * donne un joli espacement pour la petite icône silhouette au centre du cercle, mais appliqué à
     * une vraie photo il la fait apparaître inscrite dans le cercle plutôt que de remplir tout le
     * cercle jusqu'au bord (voir aussi `app:shapeAppearanceOverlay`, qui découpe désormais le
     * contenu en cercle — cahier des charges "avatar circulaire").
     */
    private fun renderPhoto(state: ProfilePhotoUiState) {
        val binding = binding ?: return
        if (state.photoUri != null) {
            binding.avatarImage.imageTintList = null
            binding.avatarImage.setPadding(0, 0, 0, 0)
            binding.avatarImage.load(state.photoUri) { crossfade(true) }
        } else {
            binding.avatarImage.imageTintList = ColorStateList.valueOf(
                MaterialColors.getColor(binding.avatarImage, com.google.android.material.R.attr.colorOnSurfaceVariant)
            )
            val iconPadding = resources.getDimensionPixelSize(R.dimen.spacing_m)
            binding.avatarImage.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
            binding.avatarImage.setImageResource(R.drawable.ic_person_24)
        }
        binding.avatarProgress.visibility = if (state.isProcessing) View.VISIBLE else View.GONE
    }

    /** Le switch reste visible mais désactivé (jamais masqué) si la biométrie est indisponible sur
     * l'appareil : le réglage ne disparaît pas silencieusement, un texte explique pourquoi il est
     * grisé (voir [R.string.profile_biometric_lock_unavailable_description]). */
    private fun renderBiometricLock(state: BiometricLockUiState) {
        val binding = binding ?: return

        binding.biometricLockSwitch.isEnabled = state.isAvailable
        if (binding.biometricLockSwitch.isChecked != state.isEnabled) {
            binding.biometricLockSwitch.isChecked = state.isEnabled
        }
        binding.biometricLockDescription.text = getString(
            if (state.isAvailable) {
                R.string.profile_biometric_lock_description
            } else {
                R.string.profile_biometric_lock_unavailable_description
            }
        )
    }

    private fun handleEvent(event: ProfileEvent) {
        when (event) {
            ProfileEvent.Saved -> {
                binding?.let { Snackbar.make(it.root, R.string.profile_success_message, Snackbar.LENGTH_SHORT).show() }
            }
            ProfileEvent.LoggedOut -> {
                // Même raisonnement que LoginFragment.handleEvent(LoginEvent.LoggedIn) : fondu
                // (pas glissement), basculement de contexte pair vers l'écran de connexion plutôt
                // que descente hiérarchique — voir sa doc pour le détail complet.
                val options = NavOptions.Builder()
                    .setEnterAnim(R.anim.fade_in)
                    .setExitAnim(R.anim.fade_out)
                    .setPopUpTo(R.id.nav_graph, true)
                    .build()
                findNavController().navigate(R.id.loginFragment, null, options)
            }
            is ProfileEvent.ShowError -> {
                binding?.let { Snackbar.make(it.root, event.messageRes, Snackbar.LENGTH_LONG).show() }
            }
        }
    }

    private companion object {
        /** 512px de côté : largement suffisant pour un avatar (jamais affiché en plus grand que
         *  ~200dp dans l'app, voir dialog_profile_photo_preview.xml) tout en restant léger pour la
         *  synchronisation (cahier des charges "Optimisation de l'image"). */
        const val PROFILE_PHOTO_TARGET_SIZE_PX = 512
        const val PROFILE_PHOTO_JPEG_QUALITY = 85
    }
}
