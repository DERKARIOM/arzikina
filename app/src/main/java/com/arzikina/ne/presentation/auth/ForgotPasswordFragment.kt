package com.arzikina.ne.presentation.auth

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
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentForgotPasswordBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Écran "Mot de passe oublié" : ouvert DEPUIS Connexion (voir
 * `nav_graph.xml`), jamais accessible autrement — la flèche retour ramène
 * donc toujours à Connexion via `navigateUp()`.
 *
 * Un seul Fragment pour les 2 étapes (voir [ForgotPasswordViewModel] pour la
 * justification) : [render] bascule simplement la visibilité de
 * `identifierGroup` / `resetGroup` selon `state.step`.
 */
@AndroidEntryPoint
class ForgotPasswordFragment : Fragment(R.layout.fragment_forgot_password) {

    private val viewModel: ForgotPasswordViewModel by viewModels()
    private var binding: FragmentForgotPasswordBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentForgotPasswordBinding.bind(view)
        binding = viewBinding

        // Garde explicite : ne pas abandonner une vérification/réinitialisation
        // en cours sur un tap accidentel (même raisonnement que RegisterFragment).
        viewBinding.toolbar.setNavigationOnClickListener {
            if (!viewModel.formState.value.isSubmitting) findNavController().navigateUp()
        }

        viewBinding.identifierInput.doAfterTextChanged { viewModel.onIdentifierChange(it?.toString().orEmpty()) }
        viewBinding.identifierInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.submitIdentifier()
                true
            } else {
                false
            }
        }
        viewBinding.continueButton.setOnClickListener { viewModel.submitIdentifier() }

        viewBinding.securityAnswerInput.doAfterTextChanged {
            viewModel.onSecurityAnswerChange(it?.toString().orEmpty())
        }
        viewBinding.newPasswordInput.doAfterTextChanged { viewModel.onNewPasswordChange(it?.toString().orEmpty()) }
        viewBinding.confirmPasswordInput.doAfterTextChanged {
            viewModel.onConfirmPasswordChange(it?.toString().orEmpty())
        }
        // Seul le dernier champ (confirmation) déclenche la soumission via
        // "Terminé" : les étapes intermédiaires n'ont qu'un simple "Suivant"
        // (voir android:imeOptions dans le layout), pas d'action associée.
        viewBinding.confirmPasswordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.submitReset()
                true
            } else {
                false
            }
        }
        viewBinding.resetButton.setOnClickListener { viewModel.submitReset() }

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

    private fun render(state: ForgotPasswordFormState) {
        val binding = binding ?: return

        val isIdentifierStep = state.step == ForgotPasswordStep.IDENTIFIER
        binding.identifierGroup.visibility = if (isIdentifierStep) View.VISIBLE else View.GONE
        binding.resetGroup.visibility = if (isIdentifierStep) View.GONE else View.VISIBLE

        if (binding.identifierInput.text?.toString() != state.identifier) {
            binding.identifierInput.setText(state.identifier)
        }
        binding.identifierLayout.error = state.identifierError?.let { getString(it) }
        binding.continueButton.isEnabled = !state.isSubmitting
        binding.continueButton.text = if (state.isSubmitting) "" else getString(R.string.forgot_password_continue_action)
        binding.identifierProgressIndicator.visibility = if (isIdentifierStep && state.isSubmitting) View.VISIBLE else View.GONE

        state.securityQuestion?.let { binding.securityQuestionText.text = getString(it.displayTextRes()) }

        if (binding.securityAnswerInput.text?.toString() != state.securityAnswer) {
            binding.securityAnswerInput.setText(state.securityAnswer)
        }
        binding.securityAnswerLayout.error = state.securityAnswerError?.let { getString(it) }

        if (binding.newPasswordInput.text?.toString() != state.newPassword) {
            binding.newPasswordInput.setText(state.newPassword)
        }
        binding.newPasswordLayout.error = state.newPasswordError?.let { getString(it) }

        if (binding.confirmPasswordInput.text?.toString() != state.confirmPassword) {
            binding.confirmPasswordInput.setText(state.confirmPassword)
        }
        binding.confirmPasswordLayout.error = state.confirmPasswordError?.let { getString(it) }

        binding.resetButton.isEnabled = !state.isSubmitting
        binding.resetButton.text = if (state.isSubmitting) "" else getString(R.string.forgot_password_submit_action)
        binding.resetProgressIndicator.visibility = if (!isIdentifierStep && state.isSubmitting) View.VISIBLE else View.GONE
    }

    private fun handleEvent(event: ForgotPasswordEvent) {
        when (event) {
            ForgotPasswordEvent.PasswordReset -> {
                // Toast plutôt que Snackbar : ce message doit survivre à la
                // navigation qui suit immédiatement (retour à Connexion, voir
                // navigateUp() ci-dessous) — un Snackbar disparaîtrait avec la
                // vue de ce Fragment au moment où elle est détruite.
                Toast.makeText(requireContext(), R.string.forgot_password_success_message, Toast.LENGTH_LONG).show()
                // Toujours ouvert DEPUIS Connexion (voir la KDoc de la classe) :
                // navigateUp() y revient forcément, sans avoir besoin de cibler
                // explicitement R.id.loginFragment.
                findNavController().navigateUp()
            }
            is ForgotPasswordEvent.ShowError -> {
                binding?.let { Snackbar.make(it.root, event.messageRes, Snackbar.LENGTH_LONG).show() }
            }
        }
    }
}
