package com.arzikina.ne.presentation.statistics

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentStatisticsBinding
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.DatePeriods
import com.arzikina.ne.util.Money
import com.arzikina.ne.util.StatsPeriodPreset
import com.google.android.material.datepicker.MaterialDatePicker
import com.patrykandpatrick.vico.views.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.views.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.views.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.views.cartesian.data.columnModel
import com.patrykandpatrick.vico.views.common.data.ExtraStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
        setUpPeriodSelector(viewBinding)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    /**
     * Sélecteur de période (voir [StatisticsViewModel.PeriodSelection]) : même pattern que
     * `BudgetFormFragment.setUpQuickRangeGroup`/`showDatePicker` (`MaterialButtonToggleGroup` +
     * `MaterialDatePicker`, conversion UTC→LocalDate identique), réutilisé tel quel plutôt qu'un
     * nouveau composant de sélection de date.
     */
    private fun setUpPeriodSelector(binding: FragmentStatisticsBinding) {
        binding.customStartField.dateFieldLabel.text = getString(R.string.statistics_period_custom_start_label)
        binding.customStartRow.setOnClickListener {
            showDatePicker(R.string.statistics_period_custom_start_label) { viewModel.onCustomStartSelected(it) }
        }
        binding.customEndField.dateFieldLabel.text = getString(R.string.statistics_period_custom_end_label)
        binding.customEndRow.setOnClickListener {
            showDatePicker(R.string.statistics_period_custom_end_label) { viewModel.onCustomEndSelected(it) }
        }

        binding.periodPresetGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            presetForButtonId(checkedId)?.let { viewModel.onPresetSelected(it) }
        }

        binding.resetPeriodButton.setOnClickListener { viewModel.onResetPeriod() }
    }

    private fun presetForButtonId(buttonId: Int): StatsPeriodPreset? = when (buttonId) {
        R.id.periodPresetMonth -> StatsPeriodPreset.MONTH
        R.id.periodPresetPrevMonth -> StatsPeriodPreset.PREV_MONTH
        R.id.periodPresetLast7Days -> StatsPeriodPreset.LAST_7_DAYS
        R.id.periodPresetLast30Days -> StatsPeriodPreset.LAST_30_DAYS
        R.id.periodPresetYear -> StatsPeriodPreset.YEAR
        R.id.periodPresetCustom -> StatsPeriodPreset.CUSTOM
        else -> null
    }

    private fun buttonIdForPreset(preset: StatsPeriodPreset): Int = when (preset) {
        StatsPeriodPreset.MONTH -> R.id.periodPresetMonth
        StatsPeriodPreset.PREV_MONTH -> R.id.periodPresetPrevMonth
        StatsPeriodPreset.LAST_7_DAYS -> R.id.periodPresetLast7Days
        StatsPeriodPreset.LAST_30_DAYS -> R.id.periodPresetLast30Days
        StatsPeriodPreset.YEAR -> R.id.periodPresetYear
        StatsPeriodPreset.CUSTOM -> R.id.periodPresetCustom
    }

    /**
     * Même conversion que `BudgetFormFragment.showDatePicker`, en s'arrêtant une étape plus tôt
     * (un [LocalDate], pas des millis) : les millis UTC minuit renvoyés par le picker représentent
     * déjà le jour calendaire exact tapé par l'utilisateur — `atZone(ZoneOffset.UTC).toLocalDate()`
     * le récupère directement, sans avoir besoin de le reconvertir ensuite en millis locaux ici
     * (c'est [DatePeriods.toEpochMillis]/[DatePeriods.toEpochMillisEndOfDay], appelés côté
     * ViewModel, qui s'en chargent au moment de résoudre la période).
     */
    private fun showDatePicker(@StringRes titleRes: Int, onSelected: (LocalDate) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(titleRes)
            .build()
        picker.addOnPositiveButtonClickListener { selectionUtcMillis ->
            onSelected(Instant.ofEpochMilli(selectionUtcMillis).atZone(ZoneOffset.UTC).toLocalDate())
        }
        picker.show(parentFragmentManager, "statistics_period_date_picker")
    }

    private fun formatDate(date: LocalDate?): String =
        date?.format(DATE_FORMATTER) ?: getString(R.string.budget_form_date_placeholder)

    private fun periodErrorMessage(error: StatsPeriodError): String = when (error) {
        StatsPeriodError.MISSING_DATES -> getString(R.string.statistics_period_error_missing_dates)
        StatsPeriodError.START_AFTER_END -> getString(R.string.statistics_period_error_start_after_end)
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

        renderPeriodSelector(binding, state.data)
        // Le graphique "Évolution" reste toujours rendu, y compris en cas d'erreur de période (voir
        // StatisticsViewModel : monthlyEvolution est indépendant de la sélection de période).
        renderEvolution(state.data)

        val hasError = state.data.periodError != null
        binding.totalsCard.visibility = if (hasError) View.GONE else View.VISIBLE
        binding.breakdownSection.visibility = if (hasError) View.GONE else View.VISIBLE
        if (!hasError) {
            renderTotals(binding, state.data)
            renderBreakdown(binding, state.data)
        }
    }

    /** Boutons de préréglage, champs de dates personnalisées et message d'erreur — voir
     *  [StatisticsViewModel.PeriodSelection]/[StatsPeriodError]. */
    private fun renderPeriodSelector(binding: FragmentStatisticsBinding, uiState: StatisticsUiState) {
        val selection = uiState.periodSelection

        val expectedButtonId = buttonIdForPreset(selection.preset)
        if (binding.periodPresetGroup.checkedButtonId != expectedButtonId) {
            binding.periodPresetGroup.check(expectedButtonId)
        }

        val isCustom = selection.preset == StatsPeriodPreset.CUSTOM
        binding.customStartRow.visibility = if (isCustom) View.VISIBLE else View.GONE
        binding.customEndRow.visibility = if (isCustom) View.VISIBLE else View.GONE
        binding.customStartField.dateFieldValue.text = formatDate(selection.customStart)
        binding.customEndField.dateFieldValue.text = formatDate(selection.customEnd)

        binding.periodErrorText.text = uiState.periodError?.let { periodErrorMessage(it) }
        binding.periodErrorText.visibility = if (uiState.periodError != null) View.VISIBLE else View.GONE
    }

    /** Revenus/Dépenses/Solde de la période sélectionnée — voir `totalsCard` de
     *  `fragment_statistics.xml`. Solde coloré dynamiquement (rouge si négatif), même principe que
     *  `differenceValue` du Dashboard ([com.arzikina.ne.presentation.dashboard.DashboardFragment]). */
    private fun renderTotals(binding: FragmentStatisticsBinding, uiState: StatisticsUiState) {
        binding.totalIncomeValue.text = Money.format(CurrencyAmount(uiState.currencyCode, uiState.totalIncomeMinor))
        binding.totalExpenseValue.text = Money.format(CurrencyAmount(uiState.currencyCode, uiState.totalExpenseMinor))
        binding.totalNetValue.text = Money.format(CurrencyAmount(uiState.currencyCode, uiState.totalNetMinor))
        binding.totalNetValue.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (uiState.totalNetMinor < 0L) R.color.expense_red else R.color.arzikina_on_balance_card
            )
        )
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

    private companion object {
        /** Même motif que `BudgetFormFragment.DATE_FORMATTER` (dd/MM/yyyy) — dupliqué par fichier
         *  dans ce projet plutôt que centralisé (voir les 9 usages existants de ce même motif),
         *  suivi ici pour rester cohérent avec la convention déjà en place. */
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
