package com.arzikina.ne.presentation.statistics

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentStatisticsBinding
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.Money
import com.patrykandpatrick.vico.views.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.views.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.views.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.views.cartesian.data.columnModel
import com.patrykandpatrick.vico.views.common.data.ExtraStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Clé pour transporter les libellés de mois (axe bas du graphique d'évolution) via les "extras" Vico. */
private val MonthLabelsKey = ExtraStore.Key<List<String>>()

/**
 * Graphiques (évolution mensuelle, répartition des dépenses). Reconstruite
 * en XML/Views (voir instructions projet) ; [StatisticsViewModel] est
 * inchangé.
 *
 * Le graphique d'évolution utilise le module `views` de Vico
 * (`CartesianChartView`) — stable pour cet usage. Le camembert de
 * répartition, lui, N'utilise PAS Vico : `PieChartView` levait de façon
 * reproductible `IllegalArgumentException: The outer size must be greater
 * than the inner size.` dès qu'un modèle non vide lui était fourni, un
 * problème interne au module (désormais en maintenance — corrections
 * critiques uniquement) que deux correctifs successifs n'ont pas résolu.
 * Remplacé par [CategoryPieView], un anneau dessiné directement sur un
 * `Canvas` : aucune dépendance externe, aucun invariant caché, et les
 * couleurs par catégorie restent dynamiques.
 */
@AndroidEntryPoint
class StatisticsFragment : Fragment(R.layout.fragment_statistics) {

    private val viewModel: StatisticsViewModel by viewModels()
    private var binding: FragmentStatisticsBinding? = null

    private val evolutionModelProducer = CartesianChartModelProducer()
    private val breakdownAdapter = CategoryBreakdownAdapter()

    private val monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.FRENCH)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentStatisticsBinding.bind(view)
        binding = viewBinding

        setUpEvolutionChart(viewBinding)
        setUpBreakdownList(viewBinding)

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

    private fun setUpEvolutionChart(binding: FragmentStatisticsBinding) {
        binding.evolutionChart.modelProducer = evolutionModelProducer
        val monthLabelFormatter = CartesianValueFormatter { context, x, _ ->
            context.model.extraStore.getOrNull(MonthLabelsKey)?.getOrNull(x.toInt()).orEmpty()
        }
        binding.evolutionChart.chart?.let { chart ->
            binding.evolutionChart.chart = chart.copy(
                bottomAxis = (chart.bottomAxis as HorizontalAxis).copy(valueFormatter = monthLabelFormatter)
            )
        }
    }

    private fun setUpBreakdownList(binding: FragmentStatisticsBinding) {
        binding.breakdownLegendList.layoutManager = LinearLayoutManager(requireContext())
        binding.breakdownLegendList.adapter = breakdownAdapter
    }

    private suspend fun render(state: AppResult<StatisticsUiState>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return

        renderEvolution(state.data)
        renderBreakdown(binding, state.data)
    }

    private suspend fun renderEvolution(uiState: StatisticsUiState) {
        val monthLabels = uiState.monthlyEvolution.map {
            it.yearMonth.format(monthFormatter).replaceFirstChar(Char::uppercase)
        }
        val incomeValues = uiState.monthlyEvolution.map { Money.toMajorDouble(it.incomeMinor) }
        val expenseValues = uiState.monthlyEvolution.map { Money.toMajorDouble(it.expenseMinor) }

        evolutionModelProducer.runTransaction {
            columnModel {
                series(y = incomeValues)
                series(y = expenseValues)
            }
            extras { it[MonthLabelsKey] = monthLabels }
        }
    }

    private fun renderBreakdown(binding: FragmentStatisticsBinding, uiState: StatisticsUiState) {
        val breakdown = uiState.categoryBreakdown
        val hasBreakdown = breakdown.isNotEmpty()

        binding.breakdownChart.visibility = if (hasBreakdown) View.VISIBLE else View.GONE
        binding.breakdownLegendList.visibility = if (hasBreakdown) View.VISIBLE else View.GONE
        binding.breakdownEmptyState.visibility = if (hasBreakdown) View.GONE else View.VISIBLE

        breakdownAdapter.currencyCode = uiState.currencyCode
        breakdownAdapter.submitList(breakdown)

        binding.breakdownChart.slices = breakdown.map { item ->
            val color = item.category?.colorArgb?.toInt()
                ?: ContextCompat.getColor(requireContext(), R.color.arzikina_outline)
            CategoryPieView.Slice(fraction = item.percentage, color = color)
        }
    }
}
