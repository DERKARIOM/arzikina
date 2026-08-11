package com.arzikina.ne.presentation.auth

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
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
import com.arzikina.ne.databinding.FragmentRegisterBinding
import com.arzikina.ne.domain.model.SecurityQuestion
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Écran d'inscription (voir la feuille de route Authentification).
 *
 * Toujours ouvert DEPUIS Connexion (jamais atteignable autrement, voir
 * `nav_graph.xml`) : la flèche retour du Toolbar et le lien "Se connecter"
 * font tous deux `navigateUp()` vers cet unique appelant possible. À
 * l'inscription réussie, la pile de retour est entièrement vidée avant de
 * rejoindre le Dashboard : revenir en arrière ne doit jamais ramener sur le
 * formulaire d'un compte déjà créé.
 */
@AndroidEntryPoint
class RegisterFragment : Fragment(R.layout.fragment_register) {

    private val viewModel: RegisterViewModel by viewModels()
    private var binding: FragmentRegisterBinding? = null

    /**
     * `GetContent()` plutôt que le nouveau Photo Picker
     * (`PickVisualMedia`) : disponible nativement depuis la première version
     * de l'API Activity Result, sans dépendance supplémentaire ni contrainte
     * de version. Limite connue : l'URI retournée n'a pas systématiquement
     * une permission de lecture persistante au-delà du processus courant —
     * suffisant pour l'aperçu immédiat ci-dessous, mais à revisiter (copie
     * du fichier dans le stockage interne de l'app) si la photo doit
     * survivre à un redémarrage de l'appareil.
     */
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            binding?.avatarImage?.load(uri)
            viewModel.onProfilePhotoPicked(uri.toString())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentRegisterBinding.bind(view)
        binding = viewBinding

        // Garde explicite (plutôt qu'un simple isEnabled, que la flèche d'un
        // Toolbar n'expose pas directement) : ne pas abandonner une
        // inscription en cours sur un tap accidentel.
        viewBinding.toolbar.setNavigationOnClickListener {
            if (!viewModel.formState.value.isSubmitting) findNavController().navigateUp()
        }
        viewBinding.loginActionText.setOnClickListener { findNavController().navigateUp() }
        viewBinding.avatarContainer.setOnClickListener { pickImage.launch("image/*") }
        setUpInputs(viewBinding)
        viewBinding.registerButton.setOnClickListener { viewModel.submit() }

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

    private fun setUpInputs(binding: FragmentRegisterBinding) {
        binding.fullNameInput.doAfterTextChanged { viewModel.onFullNameChange(it?.toString().orEmpty()) }
        binding.usernameInput.doAfterTextChanged { viewModel.onUsernameChange(it?.toString().orEmpty()) }
        binding.emailInput.doAfterTextChanged { viewModel.onEmailChange(it?.toString().orEmpty()) }
        binding.phoneInput.doAfterTextChanged { viewModel.onPhoneNumberChange(it?.toString().orEmpty()) }
        binding.passwordInput.doAfterTextChanged { viewModel.onPasswordChange(it?.toString().orEmpty()) }
        binding.confirmPasswordInput.doAfterTextChanged { viewModel.onConfirmPasswordChange(it?.toString().orEmpty()) }
        binding.securityAnswerInput.doAfterTextChanged { viewModel.onSecurityAnswerChange(it?.toString().orEmpty()) }
        // "Terminé" du clavier sur le dernier champ = même action que le
        // bouton (voir android:imeOptions="actionDone" dans le layout).
        binding.securityAnswerInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.submit()
                true
            } else {
                false
            }
        }

        // Liste FERMÉE (voir SecurityQuestion) : champ non-éditable
        // (android:inputType="none"), un simple sélecteur parmi les libellés
        // ci-dessous, jamais de saisie libre.
        binding.securityQuestionField.dropdownLayout.hint = getString(R.string.register_security_question_label)
        binding.securityQuestionField.dropdownInput.setSimpleItems(
            SECURITY_QUESTIONS.map { getString(it.displayTextRes()) }.toTypedArray()
        )
        binding.securityQuestionField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            viewModel.onSecurityQuestionChange(SECURITY_QUESTIONS[position])
        }
    }

    private fun render(state: RegisterFormState) {
        val binding = binding ?: return

        if (binding.fullNameInput.text?.toString() != state.fullName) binding.fullNameInput.setText(state.fullName)
        binding.fullNameLayout.error = state.fullNameError?.let { getString(it) }

        if (binding.usernameInput.text?.toString() != state.username) binding.usernameInput.setText(state.username)
        binding.usernameLayout.error = state.usernameError?.let { getString(it) }

        if (binding.emailInput.text?.toString() != state.email) binding.emailInput.setText(state.email)
        binding.emailLayout.error = state.emailError?.let { getString(it) }

        if (binding.phoneInput.text?.toString() != state.phoneNumber) binding.phoneInput.setText(state.phoneNumber)

        if (binding.passwordInput.text?.toString() != state.password) binding.passwordInput.setText(state.password)
        binding.passwordLayout.error = state.passwordError?.let { getString(it) }

        if (binding.confirmPasswordInput.text?.toString() != state.confirmPassword) {
            binding.confirmPasswordInput.setText(state.confirmPassword)
        }
        binding.confirmPasswordLayout.error = state.confirmPasswordError?.let { getString(it) }

        val questionLabel = getString(state.securityQuestion.displayTextRes())
        if (binding.securityQuestionField.dropdownInput.text?.toString() != questionLabel) {
            binding.securityQuestionField.dropdownInput.setText(questionLabel, false)
        }
        if (binding.securityAnswerInput.text?.toString() != state.securityAnswer) {
            binding.securityAnswerInput.setText(state.securityAnswer)
        }
        binding.securityAnswerLayout.error = state.securityAnswerError?.let { getString(it) }

        binding.registerButton.isEnabled = !state.isSubmitting
        binding.registerButton.text = if (state.isSubmitting) "" else getString(R.string.register_submit_action)
        binding.progressIndicator.visibility = if (state.isSubmitting) View.VISIBLE else View.GONE
        binding.loginActionText.isEnabled = !state.isSubmitting
    }

    private fun handleEvent(event: RegisterEvent) {
        when (event) {
            RegisterEvent.Registered -> {
                val options = NavOptions.Builder().setPopUpTo(R.id.nav_graph, true).build()
                findNavController().navigate(R.id.dashboardFragment, null, options)
            }
            is RegisterEvent.ShowError -> {
                binding?.let { Snackbar.make(it.root, event.messageRes, Snackbar.LENGTH_LONG).show() }
            }
        }
    }

    private companion object {
        /** Ordre d'affichage dans la liste déroulante (voir setUpInputs). */
        val SECURITY_QUESTIONS = SecurityQuestion.entries
    }
}
