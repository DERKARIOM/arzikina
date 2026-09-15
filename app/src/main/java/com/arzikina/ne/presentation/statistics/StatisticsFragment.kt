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
 * Graphiques (évolution mensuelle, répartition par catégorie). [StatisticsViewModel] porte toute
 * la logique métier (période, type de répartition, totaux) ; ce Fragment ne fait que du rendu.
 *
 * Le graphique d'évolution utilise le module `views` de Vico (`CartesianChartView`) — stable pour
 * cet usage. La répartition par catégorie, elle, N'utilise PAS Vico : dessinée directement via
 * [CategoryBarChartView] (une couleur DIFFÉRENTE par barre, pas un cas d'usage nativement supporté
 * par une série Vico unique — voir sa doc de tête), un diagramme en bâtons qui a lui-même remplacé
 * un premier jet en anneau ([CategoryPieView], toujours utilisée telle quelle par
 * [com.arzikina.ne.presentation.utilities.loans.LoanStatisticsFragment] pour un besoin différent).
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
        setUpBreakdownTypeSelector(viewBinding)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    /**
     * Préréglages affichés dans le Spinner [FragmentStatisticsBinding.periodPresetField], DANS CET
     * ORDRE (celui du menu déroulant) — sert à la fois à peupler la liste
     * ([MaterialAutoCompleteTextView.setSimpleItems]) et à retrouver le préréglage correspondant à
     * la position cliquée (voir [setUpPeriodSelector]), même principe que `latestCategories` de
     * `BudgetFormFragment.setUpCategoryDropdown`.
     */
    private val periodPresets: List<Pair<StatsPeriodPreset, Int>> = listOf(
        StatsPeriodPreset.MONTH to R.string.statistics_period_month,
        StatsPeriodPreset.PREV_MONTH to R.string.statistics_period_prev_month,
        StatsPeriodPreset.LAST_7_DAYS to R.string.statistics_period_last_7_days,
        StatsPeriodPreset.LAST_30_DAYS to R.string.statistics_period_last_30_days,
        StatsPeriodPreset.YEAR to R.string.statistics_period_year,
        StatsPeriodPreset.CUSTOM to R.string.statistics_period_custom
    )

    /**
     * Sélecteur de période (voir [StatisticsViewModel.PeriodSelection]) : Spinner Material3
     * ("postcard" `ExposedDropdownMenu`, `item_dropdown_field.xml`) — même pattern que
     * `BudgetFormFragment.setUpCategoryDropdown`/`setUpCurrencyDropdown`, réutilisé tel quel plutôt
     * qu'un nouveau composant. Les dates personnalisées gardent le sélecteur
     * `MaterialDatePicker`/`showDatePicker` déjà établi par `BudgetFormFragment`.
     */
    private fun setUpPeriodSelector(binding: FragmentStatisticsBinding) {
        binding.periodPresetField.dropdownLayout.hint = getString(R.string.statistics_period_label)
        binding.periodPresetField.dropdownInput.setSimpleItems(
            periodPresets.map { (_, labelRes) -> getString(labelRes) }.toTypedArray()
        )
        binding.periodPresetField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            periodPresets.getOrNull(position)?.let { (preset, _) -> viewModel.onPresetSelected(preset) }
        }

        binding.customStartField.dateFieldLabel.text = getString(R.string.statistics_period_custom_start_label)
        binding.customStartRow.setOnClickListener {
            showDatePicker(R.string.statistics_period_custom_start_label) { viewModel.onCustomStartSelected(it) }
        }
        binding.customEndField.dateFieldLabel.text = getString(R.string.statistics_period_custom_end_label)
        binding.customEndRow.setOnClickListener {
            showDatePicker(R.string.statistics_period_custom_end_label) { viewModel.onCustomEndSelected(it) }
        }

        binding.resetPeriodButton.setOnClickListener { viewModel.onResetPeriod() }
    }

    /** Miroir de [periodPresets] pour le Spinner "Type" (Dépenses/Revenus) de la section
     *  "Répartition" — voir [StatisticsViewModel.BreakdownType]. */
    private val breakdownTypes: List<Pair<BreakdownType, Int>> = listOf(
        BreakdownType.EXPENSE to R.string.statistics_breakdown_type_expense,
        BreakdownType.INCOME to R.string.statistics_breakdown_type_income
    )

    /** Spinner "Type" (Dépenses/Revenus) de la section "Répartition" — même composant et même
     *  câblage que [setUpPeriodSelector] (`item_dropdown_field.xml`). */
    private fun setUpBreakdownTypeSelector(binding: FragmentStatisticsBinding) {
        binding.breakdownTypeField.dropdownLayout.hint = getString(R.string.statistics_breakdown_type_label)
        binding.breakdownTypeField.dropdownInput.setSimpleItems(
            breakdownTypes.map { (_, labelRes) -> getString(labelRes) }.toTypedArray()
        )
        binding.breakdownTypeField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            breakdownTypes.getOrNull(position)?.let { (type, _) -> viewModel.onBreakdownTypeSelected(type) }
        }
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
            renderBreakdownTypeSelector(binding, state.data)
            renderBreakdown(binding, state.data)
        }
    }

    /** Texte affiché par le Spinner "Type" — voir [setUpBreakdownTypeSelector]. */
    private fun renderBreakdownTypeSelector(binding: FragmentStatisticsBinding, uiState: StatisticsUiState) {
        val expectedLabel = getString(breakdownTypes.first { (type, _) -> type == uiState.breakdownType }.second)
        if (binding.breakdownTypeField.dropdownInput.text?.toString() != expectedLabel) {
            binding.breakdownTypeField.dropdownInput.setText(expectedLabel, false)
        }
    }

    /** Spinner de préréglage, champs de dates personnalisées et message d'erreur — voir
     *  [StatisticsViewModel.PeriodSelection]/[StatsPeriodError]. */
    private fun renderPeriodSelector(binding: FragmentStatisticsBinding, uiState: StatisticsUiState) {
        val selection = uiState.periodSelection

        // Ne réécrit le texte que s'il a changé (même garde que
        // `BudgetFormFragment.render`/`categoryField`) : évite de perturber le menu ouvert si le
        // ViewModel réémet un état identique pendant que l'utilisateur interagit.
        val expectedLabel = getString(periodPresets.first { (preset, _) -> preset == selection.preset }.second)
        if (binding.periodPresetField.dropdownInput.text?.toString() != expectedLabel) {
            binding.periodPresetField.dropdownInput.setText(expectedLabel, false)
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
        binding.breakdownEmptyState.text = getString(
            when (uiState.breakdownType) {
                BreakdownType.EXPENSE -> R.string.statistics_breakdown_empty_expense
                BreakdownType.INCOME -> R.string.statistics_breakdown_empty_income
            }
        )

        breakdownAdapter.currencyCode = uiState.currencyCode
        breakdownAdapter.submitList(breakdown)

        binding.breakdownChart.bars = breakdown.map { item ->
            val color = item.category?.colorArgb?.toInt()
                ?: ContextCompat.getColor(requireContext(), R.color.arzikina_outline)
            CategoryBarChartView.Bar(
                label = item.category?.name ?: getString(R.string.transaction_uncategorized),
                amountMinor = item.amountMinor,
                color = color
            )
        }
    }

    private companion object {
        /** Même motif que `BudgetFormFragment.DATE_FORMATTER` (dd/MM/yyyy) — dupliqué par fichier
         *  dans ce projet plutôt que centralisé (voir les 9 usages existants de ce même motif),
         *  suivi ici pour rester cohérent avec la convention déjà en place. */
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
