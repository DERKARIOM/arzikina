package com.naniger.arzikina.presentation.profile

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
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentDeleteAccountBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Ouvert DEPUIS Profil (voir `nav_graph.xml` et `ProfileFragment.setUpDeleteAccountButton`) : la
 * flèche retour y ramène toujours — même raisonnement que [ChangePasswordFragment]. Le mot de
 * passe re-saisi ici TIENT LIEU de confirmation (voir [DeleteAccountViewModel]) : aucun dialogue
 * "Êtes-vous sûr ?" supplémentaire, contrairement à `ProfileFragment.confirmLogout`/
 * `confirmDeletePhoto`, qui eux n'ont aucune re-saisie d'identifiant à offrir comme garde-fou.
 */
@AndroidEntryPoint
class DeleteAccountFragment : Fragment(R.layout.fragment_delete_account) {

    private val viewModel: DeleteAccountViewModel by viewModels()
    private var binding: FragmentDeleteAccountBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentDeleteAccountBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener {
            if (!viewModel.formState.value.isSubmitting) findNavController().navigateUp()
        }

        viewBinding.passwordInput.doAfterTextChanged { viewModel.onPasswordChange(it?.toString().orEmpty()) }
        viewBinding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                viewModel.submit()
                true
            } else {
                false
            }
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

    private fun render(state: DeleteAccountFormState) {
        val binding = binding ?: return

        if (binding.passwordInput.text?.toString() != state.password) {
            binding.passwordInput.setText(state.password)
        }
        binding.passwordLayout.error = state.passwordError?.let { getString(it) }

        binding.submitButton.isEnabled = !state.isSubmitting
        binding.submitButton.text = if (state.isSubmitting) "" else getString(R.string.delete_account_submit_action)
        binding.progressIndicator.visibility = if (state.isSubmitting) View.VISIBLE else View.GONE
    }

    private fun handleEvent(event: DeleteAccountEvent) {
        when (event) {
            DeleteAccountEvent.Deleted -> {
                // Même raisonnement que ProfileFragment.handleEvent(ProfileEvent.LoggedOut) :
                // fondu vers l'écran de connexion, back stack entièrement vidé — le compte n'existe
                // plus, revenir en arrière n'aurait aucun sens.
                val options = NavOptions.Builder()
                    .setEnterAnim(R.anim.fade_in)
                    .setExitAnim(R.anim.fade_out)
                    .setPopUpTo(R.id.nav_graph, true)
                    .build()
                findNavController().navigate(R.id.loginFragment, null, options)
            }
            is DeleteAccountEvent.ShowError -> {
                binding?.let { Snackbar.make(it.root, event.messageRes, Snackbar.LENGTH_LONG).show() }
            }
        }
    }
}
