package com.arzikina.ne.presentation.profile

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import coil3.load
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentProfileBinding
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

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            binding?.avatarImage?.load(uri)
            viewModel.onProfilePhotoPicked(uri.toString())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentProfileBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        viewBinding.avatarContainer.setOnClickListener { pickImage.launch("image/*") }

        viewBinding.fullNameInput.doAfterTextChanged { viewModel.onFullNameChange(it?.toString().orEmpty()) }
        viewBinding.emailInput.doAfterTextChanged { viewModel.onEmailChange(it?.toString().orEmpty()) }
        viewBinding.phoneInput.doAfterTextChanged { viewModel.onPhoneNumberChange(it?.toString().orEmpty()) }
        viewBinding.saveButton.setOnClickListener { viewModel.save() }

        viewBinding.changePasswordRow.menuIcon.setImageResource(R.drawable.ic_lock_24)
        viewBinding.changePasswordRow.menuTitle.setText(R.string.profile_change_password_action)
        viewBinding.changePasswordRow.root.setOnClickListener {
            findNavController().navigate(R.id.changePasswordFragment)
        }

        viewBinding.securityQuestionRow.menuIcon.setImageResource(R.drawable.ic_help_24)
        viewBinding.securityQuestionRow.menuTitle.setText(R.string.profile_security_question_action)
        viewBinding.securityQuestionRow.root.setOnClickListener {
            findNavController().navigate(R.id.securityQuestionFragment)
        }

        viewBinding.logoutButton.setOnClickListener { confirmLogout() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.formState.collect { state -> render(state) } }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
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

        if (state.profilePhotoUri != null) {
            binding.avatarImage.load(state.profilePhotoUri)
        }

        binding.saveButton.isEnabled = !state.isSaving
        binding.saveButton.text = if (state.isSaving) "" else getString(R.string.profile_save_action)
        binding.progressIndicator.visibility = if (state.isSaving) View.VISIBLE else View.GONE
    }

    private fun handleEvent(event: ProfileEvent) {
        when (event) {
            ProfileEvent.Saved -> {
                binding?.let { Snackbar.make(it.root, R.string.profile_success_message, Snackbar.LENGTH_SHORT).show() }
            }
            ProfileEvent.LoggedOut -> {
                val options = NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build()
                findNavController().navigate(R.id.loginFragment, null, options)
            }
            is ProfileEvent.ShowError -> {
                binding?.let { Snackbar.make(it.root, event.messageRes, Snackbar.LENGTH_LONG).show() }
            }
        }
    }
}
