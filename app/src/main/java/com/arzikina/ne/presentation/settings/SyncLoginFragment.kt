package com.arzikina.ne.presentation.settings

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentSyncLoginBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Connexion au serveur de synchronisation (voir [SyncLoginViewModel],
 * `domain/repository/SyncAuthRepository.kt`) — atteint depuis Paramètres > Synchronisation
 * (voir [SettingsFragment.setUpSyncSection]), jamais le point d'entrée de l'app : contrairement à
 * [com.arzikina.ne.presentation.auth.LoginFragment] (authentification LOCALE), une flèche retour
 * est donc toujours présente.
 *
 * Portée volontairement limitée à la connexion : pas encore de gestion "déjà connecté / se
 * déconnecter" (état affiché dans Paramètres, écran séparé) — prévu à une étape ultérieure une
 * fois ce premier flux validé de bout en bout avec le serveur.
 */
@AndroidEntryPoint
class SyncLoginFragment : Fragment(R.layout.fragment_sync_login) {

    private val viewModel: SyncLoginViewModel by viewModels()
    private var binding: FragmentSyncLoginBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentSyncLoginBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        viewBinding.identifierInput.doAfterTextChanged {
            viewModel.onIdentifierChange(it?.toString().orEmpty())
        }
        viewBinding.passwordInput.doAfterTextChanged {
            viewModel.onPasswordChange(it?.toString().orEmpty())
        }
        // "Terminé" du clavier sur le dernier champ = même action que le bouton (voir
        // android:imeOptions="actionDone" dans le layout).
        viewBinding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.submit()
                true
            } else {
                false
            }
        }
        viewBinding.loginButton.setOnClickListener { viewModel.submit() }

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

    private fun render(state: SyncLoginFormState) {
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
        binding.loginButton.text = if (state.isSubmitting) "" else getString(R.string.sync_login_submit_action)
        binding.progressIndicator.visibility = if (state.isSubmitting) View.VISIBLE else View.GONE
    }

    private fun handleEvent(event: SyncLoginEvent) {
        val binding = binding ?: return
        when (event) {
            // Simple retour à Paramètres avec confirmation (pas de remplacement de graphe comme
            // LoginEvent.LoggedIn côté authentification locale) : cet écran est un sous-écran de
            // Paramètres, pas un basculement de contexte pair de toute l'application.
            SyncLoginEvent.LoggedIn -> {
                Snackbar.make(binding.root, R.string.sync_login_success_message, Snackbar.LENGTH_LONG).show()
                findNavController().navigateUp()
            }
        }
    }
}
