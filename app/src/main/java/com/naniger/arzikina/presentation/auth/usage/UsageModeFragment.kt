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
import com.naniger.arzikina.presentation.auth.currency.CurrencyPageController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

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
    private var currencyController: CurrencyPageController? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentUsageModeBinding.bind(view)
        binding = viewBinding
        val pageController = UsageModePageController(
            binding = viewBinding.page,
            onModeSelected = viewModel::onModeSelected,
            onContinue = viewModel::continueToCurrency,
            onBack = null
        )
        controller = pageController
        val pageCurrencyController = CurrencyPageController(
            binding = viewBinding.currencyPage,
            onSelected = viewModel::onCurrencySelected,
            onQueryChange = viewModel::onCurrencyQueryChange,
            onContinue = viewModel::confirm,
            onBack = viewModel::back
        )
        currencyController = pageCurrencyController
        viewModel.onRegionDetected(Locale.getDefault().country)
        // Retour système : depuis la devise, retour au mode ; depuis le mode, on ne quitte pas (voir la doc de classe).
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = viewModel.back()
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect {
                        viewBinding.currencyPage.root.visibility =
                            if (it.step == UsageModeStep.CURRENCY) View.VISIBLE else View.GONE
                        pageController.render(it.selected, it.isSubmitting)
                        pageCurrencyController.render(it.currency, it.isSubmitting)
                    }
                }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        controller = null
        currencyController = null
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
