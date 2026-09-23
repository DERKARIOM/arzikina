package com.naniger.arzikina.presentation.auth

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentLoginBinding
import com.naniger.arzikina.presentation.components.NavAnimations
import com.naniger.arzikina.presentation.components.animateElevationOnFocus
import com.naniger.arzikina.presentation.components.playEntranceAnimation
import com.naniger.arzikina.presentation.components.playPressScaleFeedback
import com.naniger.arzikina.presentation.components.setVisibleAnimated
import com.google.android.material.textfield.TextInputLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Écran de Connexion : point d'entrée de l'app quand aucune session locale
 * n'existe (voir `MainActivity.resolveStartDestination`). Étant le
 * startDestination dans ce cas, il n'a pas de flèche retour ni de pile à
 * dépiler ; à l'inverse, Inscription est ouverte DEPUIS cet écran et y
 * revient via `navigateUp()`.
 *
 * Champs "identifiant"/"mot de passe" posés via `<include layout="@layout/item_postcard_text_input">`
 * (voir fragment_login.xml et sa doc, refonte "Login/Register") : `binding.identifierField` /
 * `binding.passwordField` exposent chacun `.postcardInputLayout` (le `TextInputLayout`, pour hint/
 * icône/erreur) et `.postcardInput` (le `TextInputEditText` lui-même) — même pattern déjà utilisé
 * pour `securityQuestionField` côté [RegisterFragment]. [setUpInputs] centralise le paramétrage
 * (hint, placeholder, icône, type de clavier) propre à CET écran, puisque le layout inclus reste
 * générique.
 */
@AndroidEntryPoint
class LoginFragment : Fragment(R.layout.fragment_login) {

    private val viewModel: LoginViewModel by viewModels()
    private var binding: FragmentLoginBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentLoginBinding.bind(view)
        binding = viewBinding

        setUpInputs(viewBinding)
        viewBinding.contentContainer.playEntranceAnimation()
        viewBinding.loginButton.playPressScaleFeedback()

        viewBinding.identifierField.postcardInput.doAfterTextChanged {
            viewModel.onIdentifierChange(it?.toString().orEmpty())
        }
        viewBinding.passwordField.postcardInput.doAfterTextChanged {
            viewModel.onPasswordChange(it?.toString().orEmpty())
        }
        // "Terminé" du clavier sur le dernier champ = même action que le
        // bouton (voir android:imeOptions="actionDone" posé par setUpInputs).
        viewBinding.passwordField.postcardInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.submit()
                true
            } else {
                false
            }
        }
        viewBinding.loginButton.setOnClickListener { viewModel.submit() }
        viewBinding.registerActionText.setOnClickListener {
            findNavController().navigate(R.id.registerFragment, null, NavAnimations.push)
        }
        viewBinding.forgotPasswordActionText.setOnClickListener {
            findNavController().navigate(R.id.forgotPasswordFragment, null, NavAnimations.push)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.formState.collect { state -> render(state) } }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    /**
     * Paramétrage propre à cet écran des deux champs génériques `item_postcard_text_input.xml`
     * (hint, placeholder, icône de tête, type de clavier, bascule mot de passe) — le layout inclus
     * lui-même ne connaît rien de "identifiant" ou "mot de passe", voir sa doc.
     */
    private fun setUpInputs(binding: FragmentLoginBinding) {
        binding.identifierField.postcardInputLayout.apply {
            hint = getString(R.string.login_identifier_label)
            setStartIconDrawable(R.drawable.ic_mail_24)
        }
        binding.identifierField.postcardInput.apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            imeOptions = EditorInfo.IME_ACTION_NEXT
            hint = getString(R.string.login_identifier_placeholder)
        }
        binding.identifierField.animateElevationOnFocus()

        binding.passwordField.postcardInputLayout.apply {
            hint = getString(R.string.login_password_label)
            setStartIconDrawable(R.drawable.ic_lock_24)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        binding.passwordField.postcardInput.apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = getString(R.string.login_password_placeholder)
        }
        binding.passwordField.animateElevationOnFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun render(state: LoginFormState) {
        val binding = binding ?: return

        if (binding.identifierField.postcardInput.text?.toString() != state.identifier) {
            binding.identifierField.postcardInput.setText(state.identifier)
        }
        binding.identifierField.postcardInputLayout.error = state.identifierError?.let { getString(it) }

        if (binding.passwordField.postcardInput.text?.toString() != state.password) {
            binding.passwordField.postcardInput.setText(state.password)
        }
        binding.passwordField.postcardInputLayout.error = state.passwordError?.let { getString(it) }

        if (state.formError != null) {
            binding.formErrorText.text = getString(state.formError)
            binding.formErrorText.setVisibleAnimated(true)
        } else {
            binding.formErrorText.setVisibleAnimated(false)
        }

        binding.loginButton.isEnabled = !state.isSubmitting
        binding.loginButton.text = if (state.isSubmitting) "" else getString(R.string.login_submit_action)
        binding.progressIndicator.visibility = if (state.isSubmitting) View.VISIBLE else View.GONE

        val stageMessageRes = state.stage.messageRes()
        if (stageMessageRes != null) {
            binding.stageMessageText.text = getString(stageMessageRes)
            binding.stageMessageText.setVisibleAnimated(true)
        } else {
            binding.stageMessageText.setVisibleAnimated(false)
        }

        // Empêche de quitter l'écran (donc d'abandonner silencieusement une
        // connexion en cours) : un tap accidentel sur ces liens pendant la
        // vérification du mot de passe (voir PasswordHasher) ne doit pas
        // interrompre la tentative en cours.
        binding.registerActionText.isEnabled = !state.isSubmitting
        binding.forgotPasswordActionText.isEnabled = !state.isSubmitting
    }

    private fun handleEvent(event: LoginEvent) {
        when (event) {
            LoginEvent.LoggedIn -> {
                // Fondu, pas glissement (même raisonnement que NavAnimations.tabSwitch, voir sa
                // doc) : cette navigation ne descend pas dans une hiérarchie, elle REMPLACE tout
                // le graphe (`popUpTo(nav_graph, true)`) — un basculement de contexte pair (écran
                // de connexion → application), pas parent/enfant. `popEnterAnim`/`popExitAnim`
                // inutiles ici : `popUpTo` vide toute la pile, il n'y a plus rien vers quoi
                // revenir en arrière depuis le Dashboard.
                val options = NavOptions.Builder()
                    .setEnterAnim(R.anim.fade_in)
                    .setExitAnim(R.anim.fade_out)
                    .setPopUpTo(R.id.nav_graph, true)
                    .build()
                findNavController().navigate(R.id.dashboardFragment, null, options)
            }
        }
    }
}
