package com.arzikina.ne.presentation.auth

import android.os.Bundle
import android.text.InputType
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
import com.arzikina.ne.presentation.components.animateElevationOnFocus
import com.arzikina.ne.presentation.components.elevateOnFocusOf
import com.arzikina.ne.presentation.components.playEntranceAnimation
import com.arzikina.ne.presentation.components.playPressScaleFeedback
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
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
 *
 * Champs texte posés via `<include layout="@layout/item_postcard_text_input">` (voir
 * fragment_register.xml et sa doc, refonte "Login/Register") : `binding.xxxField` expose
 * `.postcardInputLayout` (le `TextInputLayout`) et `.postcardInput` (le `TextInputEditText`) —
 * [setUpInputs] centralise leur paramétrage (hint, placeholder, icône, type de clavier), le
 * layout inclus lui-même restant générique. `securityQuestionField` reste posé via
 * `item_dropdown_field.xml` (inchangé, voir sa doc).
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
        viewBinding.contentContainer.playEntranceAnimation()
        viewBinding.registerButton.playPressScaleFeedback()
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
        binding.fullNameField.postcardInputLayout.apply {
            hint = getString(R.string.register_full_name_label)
            setStartIconDrawable(R.drawable.ic_person_24)
        }
        binding.fullNameField.postcardInput.apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME
            imeOptions = EditorInfo.IME_ACTION_NEXT
            hint = getString(R.string.register_full_name_placeholder)
        }
        binding.fullNameField.animateElevationOnFocus()

        binding.usernameField.postcardInputLayout.apply {
            hint = getString(R.string.register_username_label)
            setStartIconDrawable(R.drawable.ic_account_circle_24)
        }
        binding.usernameField.postcardInput.apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_NEXT
            hint = getString(R.string.register_username_placeholder)
        }
        binding.usernameField.animateElevationOnFocus()

        binding.emailField.postcardInputLayout.apply {
            hint = getString(R.string.register_email_label)
            setStartIconDrawable(R.drawable.ic_mail_24)
        }
        binding.emailField.postcardInput.apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            imeOptions = EditorInfo.IME_ACTION_NEXT
            hint = getString(R.string.register_email_placeholder)
        }
        binding.emailField.animateElevationOnFocus()

        binding.phoneField.postcardInputLayout.apply {
            hint = getString(R.string.register_phone_label)
            setStartIconDrawable(R.drawable.ic_phone_24)
        }
        binding.phoneField.postcardInput.apply {
            inputType = InputType.TYPE_CLASS_PHONE
            imeOptions = EditorInfo.IME_ACTION_NEXT
            hint = getString(R.string.register_phone_placeholder)
        }
        binding.phoneField.animateElevationOnFocus()

        binding.passwordField.postcardInputLayout.apply {
            hint = getString(R.string.register_password_label)
            setStartIconDrawable(R.drawable.ic_lock_24)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        binding.passwordField.postcardInput.apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_NEXT
            hint = getString(R.string.register_password_placeholder)
        }
        binding.passwordField.animateElevationOnFocus()

        binding.confirmPasswordField.postcardInputLayout.apply {
            hint = getString(R.string.register_confirm_password_label)
            setStartIconDrawable(R.drawable.ic_lock_24)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        binding.confirmPasswordField.postcardInput.apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_NEXT
            hint = getString(R.string.register_confirm_password_placeholder)
        }
        binding.confirmPasswordField.animateElevationOnFocus()

        binding.securityAnswerField.postcardInputLayout.apply {
            hint = getString(R.string.register_security_answer_label)
            // Réutilise l'icône cadenas (voir passwordField/confirmPasswordField) : une réponse de
            // sécurité est, comme un mot de passe, une information secrète — même métaphore
            // visuelle plutôt qu'une troisième icône dédiée pour un seul champ.
            setStartIconDrawable(R.drawable.ic_lock_24)
        }
        binding.securityAnswerField.postcardInput.apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = getString(R.string.register_security_answer_placeholder)
        }
        binding.securityAnswerField.animateElevationOnFocus()

        binding.fullNameField.postcardInput.doAfterTextChanged { viewModel.onFullNameChange(it?.toString().orEmpty()) }
        binding.usernameField.postcardInput.doAfterTextChanged { viewModel.onUsernameChange(it?.toString().orEmpty()) }
        binding.emailField.postcardInput.doAfterTextChanged { viewModel.onEmailChange(it?.toString().orEmpty()) }
        binding.phoneField.postcardInput.doAfterTextChanged { viewModel.onPhoneNumberChange(it?.toString().orEmpty()) }
        binding.passwordField.postcardInput.doAfterTextChanged { viewModel.onPasswordChange(it?.toString().orEmpty()) }
        binding.confirmPasswordField.postcardInput.doAfterTextChanged {
            viewModel.onConfirmPasswordChange(it?.toString().orEmpty())
        }
        binding.securityAnswerField.postcardInput.doAfterTextChanged {
            viewModel.onSecurityAnswerChange(it?.toString().orEmpty())
        }
        // "Terminé" du clavier sur le dernier champ = même action que le
        // bouton (voir android:imeOptions posé juste au-dessus).
        binding.securityAnswerField.postcardInput.setOnEditorActionListener { _, actionId, _ ->
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
        binding.securityQuestionField.dropdownLayout.setStartIconDrawable(R.drawable.ic_help_24)
        binding.securityQuestionCard.elevateOnFocusOf(binding.securityQuestionField.dropdownInput)
        binding.securityQuestionField.dropdownInput.setSimpleItems(
            SECURITY_QUESTIONS.map { getString(it.displayTextRes()) }.toTypedArray()
        )
        binding.securityQuestionField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            viewModel.onSecurityQuestionChange(SECURITY_QUESTIONS[position])
        }
    }

    private fun render(state: RegisterFormState) {
        val binding = binding ?: return

        if (binding.fullNameField.postcardInput.text?.toString() != state.fullName) {
            binding.fullNameField.postcardInput.setText(state.fullName)
        }
        binding.fullNameField.postcardInputLayout.error = state.fullNameError?.let { getString(it) }

        if (binding.usernameField.postcardInput.text?.toString() != state.username) {
            binding.usernameField.postcardInput.setText(state.username)
        }
        binding.usernameField.postcardInputLayout.error = state.usernameError?.let { getString(it) }

        if (binding.emailField.postcardInput.text?.toString() != state.email) {
            binding.emailField.postcardInput.setText(state.email)
        }
        binding.emailField.postcardInputLayout.error = state.emailError?.let { getString(it) }

        if (binding.phoneField.postcardInput.text?.toString() != state.phoneNumber) {
            binding.phoneField.postcardInput.setText(state.phoneNumber)
        }

        if (binding.passwordField.postcardInput.text?.toString() != state.password) {
            binding.passwordField.postcardInput.setText(state.password)
        }
        binding.passwordField.postcardInputLayout.error = state.passwordError?.let { getString(it) }

        if (binding.confirmPasswordField.postcardInput.text?.toString() != state.confirmPassword) {
            binding.confirmPasswordField.postcardInput.setText(state.confirmPassword)
        }
        binding.confirmPasswordField.postcardInputLayout.error = state.confirmPasswordError?.let { getString(it) }

        val questionLabel = getString(state.securityQuestion.displayTextRes())
        if (binding.securityQuestionField.dropdownInput.text?.toString() != questionLabel) {
            binding.securityQuestionField.dropdownInput.setText(questionLabel, false)
        }
        if (binding.securityAnswerField.postcardInput.text?.toString() != state.securityAnswer) {
            binding.securityAnswerField.postcardInput.setText(state.securityAnswer)
        }
        binding.securityAnswerField.postcardInputLayout.error = state.securityAnswerError?.let { getString(it) }

        binding.registerButton.isEnabled = !state.isSubmitting
        binding.registerButton.text = if (state.isSubmitting) "" else getString(R.string.register_submit_action)
        binding.progressIndicator.visibility = if (state.isSubmitting) View.VISIBLE else View.GONE
        binding.loginActionText.isEnabled = !state.isSubmitting
    }

    private fun handleEvent(event: RegisterEvent) {
        when (event) {
            RegisterEvent.Registered -> {
                // Même raisonnement que LoginFragment.handleEvent(LoginEvent.LoggedIn) : fondu
                // (pas glissement), basculement de contexte pair vers l'application plutôt que
                // descente hiérarchique — voir sa doc pour le détail complet.
                val options = NavOptions.Builder()
                    .setEnterAnim(R.anim.fade_in)
                    .setExitAnim(R.anim.fade_out)
                    .setPopUpTo(R.id.nav_graph, true)
                    .build()
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
