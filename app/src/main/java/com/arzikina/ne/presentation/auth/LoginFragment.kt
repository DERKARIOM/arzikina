package com.arzikina.ne.presentation.auth

import android.os.Bundle
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
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentLoginBinding
import com.arzikina.ne.presentation.components.NavAnimations
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Écran de Connexion : point d'entrée de l'app quand aucune session locale
 * n'existe (voir `MainActivity.resolveStartDestination`). Étant le
 * startDestination dans ce cas, il n'a pas de flèche retour ni de pile à
 * dépiler ; à l'inverse, Inscription est ouverte DEPUIS cet écran et y
 * revient via `navigateUp()`.
 */
@AndroidEntryPoint
class LoginFragment : Fragment(R.layout.fragment_login) {

    private val viewModel: LoginViewModel by viewModels()
    private var binding: FragmentLoginBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentLoginBinding.bind(view)
        binding = viewBinding

        viewBinding.identifierInput.doAfterTextChanged {
            viewModel.onIdentifierChange(it?.toString().orEmpty())
        }
        viewBinding.passwordInput.doAfterTextChanged {
            viewModel.onPasswordChange(it?.toString().orEmpty())
        }
        // "Terminé" du clavier sur le dernier champ = même action que le
        // bouton (voir android:imeOptions="actionDone" dans le layout).
        viewBinding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
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

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun render(state: LoginFormState) {
        val binding = binding ?: return

        if (binding.identifierInput.text?.toString() != state.identifier) {
            binding.identifierInput.setText(state.identifier)
        }
        binding.identifierLayout.error = state.identifierError?.let { getString(it) }

        if (binding.passwordInput.text?.toString() != state.password) {
            binding.passwordInput.setText(state.password)
        }
        binding.passwordLayout.error = state.passwordError?.let { getString(it) }

        if (state.formError != null) {
            binding.formErrorText.visibility = View.VISIBLE
            binding.formErrorText.text = getString(state.formError)
        } else {
            binding.formErrorText.visibility = View.GONE
        }

        binding.loginButton.isEnabled = !state.isSubmitting
        binding.loginButton.text = if (state.isSubmitting) "" else getString(R.string.login_submit_action)
        binding.progressIndicator.visibility = if (state.isSubmitting) View.VISIBLE else View.GONE

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
