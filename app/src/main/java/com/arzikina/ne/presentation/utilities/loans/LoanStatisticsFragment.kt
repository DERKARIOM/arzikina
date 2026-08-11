package com.arzikina.ne.presentation.utilities.loans

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentLoanStatisticsBinding
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.LoanStatus
import com.arzikina.ne.presentation.statistics.CategoryPieView
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.Money
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Statistiques Prêts/Emprunts (voir la doc de [LoanStatisticsViewModel]), atteint depuis l'action
 * dédiée de la Toolbar de [LoansFragment]. Réutilise directement
 * [com.arzikina.ne.presentation.statistics.CategoryPieView] (générique, voir sa doc) pour l'anneau
 * de répartition par statut — pas de composant dupliqué.
 */
@AndroidEntryPoint
class LoanStatisticsFragment : Fragment(R.layout.fragment_loan_statistics) {

    private val viewModel: LoanStatisticsViewModel by viewModels()
    private var binding: FragmentLoanStatisticsBinding? = null

    private val statusAdapter = LoanStatusBreakdownAdapter()
    private val personAdapter = LoanPersonBalanceAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentLoanStatisticsBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        viewBinding.statusLegendList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.statusLegendList.adapter = statusAdapter
        viewBinding.personBalanceList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.personBalanceList.adapter = personAdapter

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

    private fun render(state: AppResult<LoanStatisticsUiState>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return
        val uiState = state.data

        binding.totalLentValue.text = formatCurrencyAmounts(uiState.totalLent)
        binding.totalBorrowedValue.text = formatCurrencyAmounts(uiState.totalBorrowed)
        binding.totalRepaidReceivedValue.text = formatCurrencyAmounts(uiState.totalRepaidReceived)
        binding.totalRepaidPaidValue.text = formatCurrencyAmounts(uiState.totalRepaidPaid)

        renderStatusBreakdown(binding, uiState)
        renderPersonBalances(binding, uiState)
    }

    private fun renderStatusBreakdown(binding: FragmentLoanStatisticsBinding, uiState: LoanStatisticsUiState) {
        val hasBreakdown = uiState.statusBreakdown.isNotEmpty()
        binding.statusChart.visibility = if (hasBreakdown) View.VISIBLE else View.GONE
        binding.statusLegendList.visibility = if (hasBreakdown) View.VISIBLE else View.GONE
        binding.statusEmptyState.visibility = if (hasBreakdown) View.GONE else View.VISIBLE

        statusAdapter.submitList(uiState.statusBreakdown)
        binding.statusChart.slices = uiState.statusBreakdown.map { item ->
            CategoryPieView.Slice(fraction = item.percentage, color = statusColor(item.status))
        }
    }

    private fun renderPersonBalances(binding: FragmentLoanStatisticsBinding, uiState: LoanStatisticsUiState) {
        val hasBalances = uiState.personBalances.isNotEmpty()
        binding.personBalanceList.visibility = if (hasBalances) View.VISIBLE else View.GONE
        binding.personsEmptyState.visibility = if (hasBalances) View.GONE else View.VISIBLE
        personAdapter.submitList(uiState.personBalances)
    }

    /** Même logique de couleur que [LoanStatusBreakdownAdapter.ViewHolder] (voir sa doc) —
     * dupliquée volontairement ici plutôt que de rendre cette fonction publique pour un seul
     * appel externe, cohérent avec le reste de l'app (petite duplication assumée entre binder et
     * Fragment, voir `LoansAdapter`/`LoansFragment`). */
    private fun statusColor(status: LoanStatus): Int {
        val colorRes = when (status) {
            LoanStatus.ONGOING -> R.color.arzikina_primary
            LoanStatus.REPAID -> R.color.loan_lent_color
            LoanStatus.OVERDUE -> R.color.expense_red
            LoanStatus.UPCOMING -> R.color.arzikina_outline
        }
        return ContextCompat.getColor(requireContext(), colorRes)
    }

    /** Une ligne par devise (voir la doc de `LoanStatisticsUiState`) ; "—" si la liste est vide —
     * même convention que `LoansAdapter.formatCurrencyAmounts`/`DashboardFragment.formatAmounts`. */
    private fun formatCurrencyAmounts(amounts: List<CurrencyAmount>): String =
        if (amounts.isEmpty()) "—" else amounts.joinToString("\n") { Money.format(it) }
}
