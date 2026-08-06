package com.arzikina.ne.presentation.dashboard

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentDashboardBinding
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.Money
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Écran d'accueil : solde total, revenus/dépenses du mois en cours et
 * dernières transactions. Reconstruit en XML/Views (voir instructions
 * projet) ; [DashboardViewModel] est inchangé depuis la version Compose.
 */
@AndroidEntryPoint
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: DashboardViewModel by viewModels()
    private var binding: FragmentDashboardBinding? = null
    private val recentTransactionsAdapter = RecentTransactionsAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentDashboardBinding.bind(view)
        binding = viewBinding

        viewBinding.recentTransactionsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentTransactionsAdapter
        }
        viewBinding.accountsShortcut.setOnClickListener {
            findNavController().navigate(R.id.accountsFragment)
        }
        viewBinding.categoriesShortcut.setOnClickListener {
            findNavController().navigate(R.id.categoriesFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun render(state: AppResult<DashboardUiState>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return
        val uiState = state.data

        binding.balanceValue.text = formatAmounts(uiState.balances)
        binding.incomeValue.text = formatAmounts(uiState.monthlyIncome)
        binding.expenseValue.text = formatAmounts(uiState.monthlyExpense)

        val hasTransactions = uiState.recentTransactions.isNotEmpty()
        binding.recentTransactionsList.setVisible(hasTransactions)
        binding.recentTransactionsEmpty.setVisible(!hasTransactions)
        recentTransactionsAdapter.submitList(uiState.recentTransactions)
    }

    /** Une ligne par devise détenue (voir [DashboardUiState]) ; "—" si aucun compte encore. */
    private fun formatAmounts(amounts: List<CurrencyAmount>): String =
        if (amounts.isEmpty()) "—" else amounts.joinToString("\n") { Money.format(it) }

    private fun View.setVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }
}
