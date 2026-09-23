package com.naniger.arzikina.presentation.profile

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
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
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentProfileBinding
import com.naniger.arzikina.presentation.components.ConfirmDialogs
import com.naniger.arzikina.presentation.components.NavAnimations
import com.naniger.arzikina.presentation.profile.crop.ProfilePhotoCropFragment
import com.naniger.arzikina.presentation.profile.crop.ProfilePhotoCropFragmentArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Écran Profil : consultation/édition des informations (nom, e-mail,
 * téléphone, photo), accès aux écrans "Changer le mot de passe" et
 * "Modifier la question de sécurité", et déconnexion.
 *
 * Reçoit un clic depuis l'onglet "Paramètres" (voir SettingsFragment.setUpProfileRow) — un
 * Toolbar avec flèche retour classique, contrairement aux écrans d'authentification
 * (Connexion/Inscription/Mot de passe oublié) qui, eux, gèrent leur propre
 * navigation de façon spécifique.
 */
@AndroidEntryPoint
class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val viewModel: ProfileViewModel by viewModels()
    private var binding: FragmentProfileBinding? = null

    /**
     * URI du fichier dans lequel l'appareil photo écrit (voir [CameraCaptureFile]). Conservée dans
     * [onSaveInstanceState] : Android peut détruire ce processus pendant que l'appareil photo est au
     * premier plan, et le résultat de [takePhoto] ne contient pas l'URI.
     */
    private var pendingCaptureUri: Uri? = null

    /** "Choisir dans la galerie" : sélecteur de photos du système (Photo Picker). Aucune
     *  permission nécessaire, sur toutes les versions d'Android supportées. `null` = annulé. */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(::launchCrop)
    }

    /** "Prendre une photo" : application appareil photo du téléphone, qui écrit dans
     *  [pendingCaptureUri]. `false` = prise de vue annulée. */
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val captureUri = pendingCaptureUri
        pendingCaptureUri = null
        if (saved && captureUri != null) launchCrop(captureUri)
    }

    /**
     * Demandée UNIQUEMENT quand l'utilisateur choisit "Prendre une photo" (jamais au démarrage de
     * l'app ni à l'ouverture de cet écran) — voir cahier des charges "Gérer correctement les
     * permissions caméra". "Choisir dans la galerie" ne nécessite AUCUNE permission (voir
     * [pickPhoto], sélecteur système "Photo Picker").
     */
    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCamera()
        } else {
            binding?.let { Snackbar.make(it.root, R.string.profile_photo_camera_permission_denied, Snackbar.LENGTH_LONG).show() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingCaptureUri = savedInstanceState?.let { BundleCompat.getParcelable(it, STATE_PENDING_CAPTURE_URI, Uri::class.java) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingCaptureUri?.let { outState.putParcelable(STATE_PENDING_CAPTURE_URI, it) }
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
                launch { observeCropResult() }
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
            ChangeProfilePhotoBottomSheet.ACTION_CHOOSE_FROM_GALLERY ->
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            ChangeProfilePhotoBottomSheet.ACTION_DELETE_PHOTO -> confirmDeletePhoto()
        }
    }

    private fun onTakePhotoSelected() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    /** Aucune application appareil photo (rare : profil professionnel restreint, émulateur…) :
     *  message d'erreur plutôt qu'un plantage. */
    private fun launchCamera() {
        val captureUri = CameraCaptureFile.createUri(requireContext())
        pendingCaptureUri = captureUri
        try {
            takePhoto.launch(captureUri)
        } catch (e: ActivityNotFoundException) {
            pendingCaptureUri = null
            binding?.let { Snackbar.make(it.root, R.string.profile_photo_error_message, Snackbar.LENGTH_LONG).show() }
        }
    }

    /**
     * [source] : photo déjà choisie ([pickPhoto]) ou déjà prise ([takePhoto]). L'écran de recadrage
     * Arzikina (voir [ProfilePhotoCropFragment]) renvoie la photo recadrée via le `SavedStateHandle`
     * de cet écran, lu dans [observeCropResult].
     */
    private fun launchCrop(source: Uri) {
        findNavController().navigate(
            R.id.profilePhotoCropFragment,
            ProfilePhotoCropFragmentArgs(sourceUri = source).toBundle(),
            NavAnimations.push
        )
    }

    /**
     * Résultat de [ProfilePhotoCropFragment] : aperçu de confirmation (voir
     * [ProfilePhotoPreviewDialogFragment]) si la photo est recadrée, message si la photo source
     * était illisible. Chaque résultat est effacé dès sa lecture pour n'être traité qu'une fois
     * (sinon l'aperçu se rouvrirait à chaque retour sur cet écran).
     */
    private suspend fun observeCropResult() {
        val handle = findNavController().currentBackStackEntry?.savedStateHandle ?: return
        coroutineScope {
            launch {
                handle.getStateFlow<Uri?>(ProfilePhotoCropFragment.RESULT_CROPPED_URI, null).collect { croppedUri ->
                    if (croppedUri != null) {
                        handle.remove<Uri>(ProfilePhotoCropFragment.RESULT_CROPPED_URI)
                        ProfilePhotoPreviewDialogFragment.show(childFragmentManager, croppedUri)
                    }
                }
            }
            launch {
                handle.getStateFlow(ProfilePhotoCropFragment.RESULT_LOAD_FAILED, false).collect { failed ->
                    if (failed) {
                        handle.remove<Boolean>(ProfilePhotoCropFragment.RESULT_LOAD_FAILED)
                        binding?.let { Snackbar.make(it.root, R.string.profile_photo_error_message, Snackbar.LENGTH_LONG).show() }
                    }
                }
            }
        }
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
     * `avatarImage` (la photo) et `avatarPlaceholder` (l'icône de repli) sont deux vues séparées de
     * taille FIXE (voir fragment_profile.xml, `avatarContainer`) — seule leur visibilité bascule
     * ici, jamais leurs dimensions/padding. Corrige un bug où la photo s'affichait trop petite au
     * premier rendu puis correctement après une interaction : l'ancienne version réutilisait la
     * même ShapeableImageView pour la photo ET le placeholder en modifiant son padding/tint par
     * code selon l'état, rendant sa taille effective dépendante du moment où ce code s'exécutait
     * par rapport au rendu (même correctif que
     * [com.naniger.arzikina.presentation.settings.SettingsFragment.render] pour `profileAvatar`, et
     * appliqué en parallèle à
     * [com.naniger.arzikina.presentation.dashboard.DashboardFragment.renderUserHeader]).
     */
    private fun renderPhoto(state: ProfilePhotoUiState) {
        val binding = binding ?: return
        if (state.photoUri != null) {
            binding.avatarPlaceholder.visibility = View.GONE
            binding.avatarImage.load(state.photoUri) { crossfade(true) }
        } else {
            binding.avatarPlaceholder.visibility = View.VISIBLE
            binding.avatarImage.setImageDrawable(null)
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
        const val STATE_PENDING_CAPTURE_URI = "pending_capture_uri"
    }
}
