package com.naniger.arzikina.presentation.versioning

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentForcedUpdateBinding
import com.naniger.arzikina.domain.versioning.VersionGateResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Écran plein cadre « Mise à jour obligatoire » — et, avec la même mise en page, « Maintenance ».
 * Sans Toolbar ni flèche retour, comme le verrou biométrique : la seule issue est la mise à jour
 * (le bouton Précédent est intercepté et ignoré).
 *
 * Il ne contient aucune règle : il affiche l'état de [AppVersionViewModel] et, quand cet état
 * redevient « autorisé » (maintenance levée, politique assouplie), se retire pour révéler l'écran
 * d'origine.
 */
@AndroidEntryPoint
class ForcedUpdateFragment : Fragment(R.layout.fragment_forced_update) {

    private val viewModel: AppVersionViewModel by activityViewModels()
    private var binding: FragmentForcedUpdateBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentForcedUpdateBinding.bind(view)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Volontairement vide : aucune échappatoire par navigation arrière.
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: VersionGateResult) {
        val b = binding ?: return
        when (state) {
            is VersionGateResult.UpdateRequired -> {
                b.titleText.setText(R.string.forced_update_title)
                b.messageText.setText(R.string.forced_update_message)
                val required = state.minVersionName?.let { "$it (${state.minVersionCode})" }
                    ?: state.minVersionCode.toString()
                b.versionsText.text = getString(
                    R.string.forced_update_versions,
                    "${state.installed.versionName} (${state.installed.versionCode})",
                    required
                )
                b.versionsText.isVisible = true
                b.notesText.text = state.releaseNotes
                b.notesText.isVisible = !state.releaseNotes.isNullOrBlank()
                b.primaryButton.setText(R.string.forced_update_action)
                b.primaryButton.setOnClickListener { openUpdateLink(state.updateUrl) }
            }
            is VersionGateResult.Maintenance -> {
                b.titleText.setText(R.string.maintenance_title)
                b.messageText.text = state.message ?: getString(R.string.maintenance_message_default)
                b.versionsText.isVisible = false
                b.notesText.isVisible = false
                b.primaryButton.setText(R.string.maintenance_retry_action)
                b.primaryButton.setOnClickListener { viewModel.check(force = true) }
            }
            VersionGateResult.Allowed -> {
                val navController = findNavController()
                if (navController.currentDestination?.id == R.id.forcedUpdateFragment) {
                    navController.popBackStack()
                }
            }
        }
    }

    private fun openUpdateLink(url: String) {
        if (!UpdateLinkOpener.open(requireContext(), url)) {
            Snackbar.make(requireView(), R.string.forced_update_open_failed, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
