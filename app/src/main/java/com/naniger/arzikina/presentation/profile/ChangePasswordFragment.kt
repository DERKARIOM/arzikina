package com.naniger.arzikina.presentation.profile

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentChangePasswordBinding
import com.naniger.arzikina.presentation.auth.GoogleSignInLauncher
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Ouvert DEPUIS Profil (voir `nav_graph.xml`) : la flèche retour y ramène toujours. */
@AndroidEntryPoint
class ChangePasswordFragment : Fragment(R.layout.fragment_change_password) {

    private val viewModel: ChangePasswordViewModel by viewModels()
    private var binding: FragmentChangePasswordBinding? = null

    /** Même sélecteur Google que Connexion, Inscription et Paramètres (voir [GoogleSignInLauncher]). */
    private val googleSignInLauncher by lazy { GoogleSignInLauncher(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentChangePasswordBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener {
            if (!viewModel.formState.value.isSubmitting) findNavController().navigateUp()
        }

        viewBinding.currentPasswordInput.doAfterTextChanged {
            viewModel.onCurrentPasswordChange(it?.toString().orEmpty())
        }
        viewBinding.newPasswordInput.doAfterTextChanged { viewModel.onNewPasswordChange(it?.toString().orEmpty()) }
        viewBinding.confirmPasswordInput.doAfterTextChanged {
            viewModel.onConfirmPasswordChange(it?.toString().orEmpty())
        }
        viewBinding.confirmPasswordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.submit()
                true
            } else {
                false
            }
        }
        viewBinding.modeToggleButton.setOnClickListener {
            val next = when (viewModel.formState.value.mode) {
                ChangePasswordMode.CURRENT_PASSWORD -> ChangePasswordMode.GOOGLE
                ChangePasswordMode.GOOGLE -> ChangePasswordMode.CURRENT_PASSWORD
            }
            viewModel.onModeChange(next)
        }
        viewBinding.submitButton.setOnClickListener { viewModel.submit() }

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

    private fun render(state: ChangePasswordFormState) {
        val binding = binding ?: return
        val googleMode = state.mode == ChangePasswordMode.GOOGLE

        // Mode Google : pas de mot de passe actuel à saisir, la preuve est la confirmation par Google.
        binding.currentPasswordCard.visibility = if (googleMode) View.GONE else View.VISIBLE
        binding.googleHintText.visibility = if (googleMode) View.VISIBLE else View.GONE
        binding.modeToggleButton.setText(
            if (googleMode) R.string.change_password_mode_current_action else R.string.change_password_mode_google_action
        )
        binding.modeToggleButton.isEnabled = !state.isSubmitting

        if (binding.currentPasswordInput.text?.toString() != state.currentPassword) {
            binding.currentPasswordInput.setText(state.currentPassword)
        }
        binding.currentPasswordLayout.error = state.currentPasswordError?.let { getString(it) }

        if (binding.newPasswordInput.text?.toString() != state.newPassword) {
            binding.newPasswordInput.setText(state.newPassword)
        }
        binding.newPasswordLayout.error = state.newPasswordError?.let { getString(it) }

        if (binding.confirmPasswordInput.text?.toString() != state.confirmPassword) {
            binding.confirmPasswordInput.setText(state.confirmPassword)
        }
        binding.confirmPasswordLayout.error = state.confirmPasswordError?.let { getString(it) }

        binding.submitButton.isEnabled = !state.isSubmitting
        binding.submitButton.text = when {
            state.isSubmitting -> ""
            googleMode -> getString(R.string.change_password_submit_google_action)
            else -> getString(R.string.change_password_submit_action)
        }
        binding.progressIndicator.visibility = if (state.isSubmitting) View.VISIBLE else View.GONE
    }

    private fun handleEvent(event: ChangePasswordEvent) {
        when (event) {
            ChangePasswordEvent.Changed -> {
                Toast.makeText(requireContext(), R.string.change_password_success_message, Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            is ChangePasswordEvent.ShowError -> {
                binding?.let { Snackbar.make(it.root, event.messageRes, Snackbar.LENGTH_LONG).show() }
            }
            ChangePasswordEvent.RequestGoogleConfirmation -> confirmWithGoogle()
        }
    }

    /** Fermer la fenêtre Google sans choisir de compte n'est pas une erreur : on reste sur l'écran, rien n'est envoyé. */
    private fun confirmWithGoogle() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = googleSignInLauncher.chooseGoogleAccount()) {
                is GoogleSignInLauncher.Result.Success -> viewModel.onGoogleIdToken(result.idToken)
                is GoogleSignInLauncher.Result.Cancelled -> Unit
                is GoogleSignInLauncher.Result.Error -> viewModel.onGoogleSignInFailed()
            }
        }
    }
}
