package com.naniger.arzikina.presentation.auth.usage

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentUsageModeBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Choix du mode d'utilisation après une authentification Google (voir [UsageModeViewModel]). Pas de flèche retour
 * ni de retour système : on ne quitte cet écran qu'en terminant la configuration (sinon la session n'est jamais
 * démarrée et la prochaine connexion Google reproposera le choix).
 */
@AndroidEntryPoint
class UsageModeFragment : Fragment(R.layout.fragment_usage_mode) {

    private val viewModel: UsageModeViewModel by viewModels()
    private var binding: FragmentUsageModeBinding? = null
    private var controller: UsageModePageController? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentUsageModeBinding.bind(view)
        binding = viewBinding
        val pageController = UsageModePageController(
            binding = viewBinding.page,
            onModeSelected = viewModel::onModeSelected,
            onContinue = viewModel::confirm,
            onBack = null
        )
        controller = pageController
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { pageController.render(it.selected, it.isSubmitting) } }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        controller = null
        binding = null
    }

    private fun handleEvent(event: UsageModeEvent) {
        when (event) {
            UsageModeEvent.Done -> {
                val options = NavOptions.Builder()
                    .setEnterAnim(R.anim.fade_in)
                    .setExitAnim(R.anim.fade_out)
                    .setPopUpTo(R.id.nav_graph, true)
                    .build()
                findNavController().navigate(R.id.dashboardFragment, null, options)
            }
            is UsageModeEvent.ShowError ->
                binding?.let { Snackbar.make(it.root, event.messageRes, Snackbar.LENGTH_LONG).show() }
        }
    }
}
