package com.arzikina.ne.presentation.budget

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentBudgetFormBinding
import com.arzikina.ne.domain.model.BudgetPeriod
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.SupportedCurrency
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.util.QuickDateRange
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Formulaire d'ajout/édition d'un budget. Reconstruit en XML/Views (voir
 * instructions projet) ; [BudgetFormViewModel] est inchangé.
 *
 * [BudgetFormViewModel.availableCategories] n'expose que les catégories de
 * dépense sans budget actif (plus celle déjà assignée en mode édition) :
 * quand cette liste est vide en mode ajout, le menu déroulant est désactivé
 * et [noCategoriesHint] explique pourquoi, plutôt que de laisser un menu
 * déroulant vide sans explication.
 */
@AndroidEntryPoint
class BudgetFormFragment : Fragment(R.layout.fragment_budget_form) {

    private val viewModel: BudgetFormViewModel by viewModels()
    private var binding: FragmentBudgetFormBinding? = null

    private var latestCategories: List<Category> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentBudgetFormBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        setUpCategoryDropdown(viewBinding)
        setUpCurrencyDropdown(viewBinding)
        setUpPeriodToggle(viewBinding)
        setUpQuickRangeGroup(viewBinding)
        setUpDatePickers(viewBinding)
        setUpInputs(viewBinding)

        viewBinding.saveButton.setOnClickListener { viewModel.save() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        viewModel.formState,
                        viewModel.availableCategories
                    ) { state, categories -> state to categories }
                        .collect { (state, categories) -> render(state, categories) }
                }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpToolbar(binding: FragmentBudgetFormBinding) {
        binding.toolbar.title = getString(
            if (viewModel.isEditMode) R.string.budget_form_title_edit else R.string.budget_form_title_add
        )
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.inflateMenu(R.menu.form_delete_menu)
        binding.toolbar.menu.findItem(R.id.action_delete_item).isVisible = viewModel.isEditMode
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_delete_item) {
                confirmDelete()
                true
            } else {
                false
            }
        }
    }

    private fun setUpCategoryDropdown(binding: FragmentBudgetFormBinding) {
        binding.categoryField.dropdownLayout.hint = getString(R.string.budget_form_category_label)
        binding.categoryField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            latestCategories.getOrNull(position)?.let { viewModel.onCategoryChange(it.id) }
        }
    }

    private fun setUpCurrencyDropdown(binding: FragmentBudgetFormBinding) {
        binding.currencyField.dropdownLayout.hint = getString(R.string.account_form_currency_label)
        val labels = SupportedCurrency.entries.map { "${it.displayName} (${it.symbol})" }
        binding.currencyField.dropdownInput.setSimpleItems(labels.toTypedArray())
        binding.currencyField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            viewModel.onCurrencyChange(SupportedCurrency.entries[position].code)
        }
    }

    private fun setUpPeriodToggle(binding: FragmentBudgetFormBinding) {
        binding.periodGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val period = if (checkedId == R.id.periodWeekly) BudgetPeriod.WEEKLY else BudgetPeriod.MONTHLY
            viewModel.onPeriodChange(period)
        }
    }

    private fun setUpInputs(binding: FragmentBudgetFormBinding) {
        binding.limitInput.doAfterTextChanged { text ->
            viewModel.onLimitChange(text?.toString().orEmpty())
        }
    }

    private fun setUpQuickRangeGroup(binding: FragmentBudgetFormBinding) {
        binding.quickRangeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val range = quickRangeForButtonId(checkedId)
            if (range != null) viewModel.onQuickRangeSelected(range) else viewModel.onCustomRangeSelected()
        }
    }

    private fun quickRangeForButtonId(buttonId: Int): QuickDateRange? = when (buttonId) {
        R.id.quickRangeThisWeek -> QuickDateRange.THIS_WEEK
        R.id.quickRangeThisMonth -> QuickDateRange.THIS_MONTH
        R.id.quickRangeNextMonth -> QuickDateRange.NEXT_MONTH
        R.id.quickRangeThisYear -> QuickDateRange.THIS_YEAR
        else -> null // quickRangeCustom : voir onCustomRangeSelected.
    }

    private fun buttonIdForQuickRange(range: QuickDateRange?): Int = when (range) {
        QuickDateRange.THIS_WEEK -> R.id.quickRangeThisWeek
        QuickDateRange.THIS_MONTH -> R.id.quickRangeThisMonth
        QuickDateRange.NEXT_MONTH -> R.id.quickRangeNextMonth
        QuickDateRange.THIS_YEAR -> R.id.quickRangeThisYear
        null -> View.NO_ID
    }

    private fun setUpDatePickers(binding: FragmentBudgetFormBinding) {
        binding.startDateField.dateFieldLabel.text = getString(R.string.budget_form_start_date_label)
        binding.startDateRow.setOnClickListener {
            showDatePicker(R.string.budget_form_start_date_label) { viewModel.onStartDateChange(it) }
        }

        binding.endDateField.dateFieldLabel.text = getString(R.string.budget_form_end_date_label)
        binding.endDateRow.setOnClickListener {
            showDatePicker(R.string.budget_form_end_date_label) { viewModel.onEndDateChange(it) }
        }
    }

    /** Voir `FinancialPlanFormFragment.showDatePicker` pour le même raisonnement (reconversion
     * UTC → heure locale) : même convention de stockage réutilisée telle quelle ici. */
    private fun showDatePicker(@StringRes titleRes: Int, onSelected: (Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(titleRes)
            .build()
        picker.addOnPositiveButtonClickListener { selectionUtcMillis ->
            val localDate = Instant.ofEpochMilli(selectionUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            onSelected(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
        picker.show(parentFragmentManager, "budget_form_date_picker")
    }

    private fun formatDate(millis: Long?): String =
        millis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMATTER) }
            ?: getString(R.string.budget_form_date_placeholder)

    private fun render(state: BudgetFormState, categories: List<Category>) {
        val binding = binding ?: return
        latestCategories = categories

        val canPickCategory = categories.isNotEmpty()
        binding.categoryField.dropdownLayout.isEnabled = canPickCategory
        binding.noCategoriesHint.visibility = if (canPickCategory) View.GONE else View.VISIBLE

        binding.categoryField.dropdownInput.setSimpleItems(categories.map { it.name }.toTypedArray())
        val categoryLabel = categories.firstOrNull { it.id == state.categoryId }?.name.orEmpty()
        if (binding.categoryField.dropdownInput.text?.toString() != categoryLabel) {
            binding.categoryField.dropdownInput.setText(categoryLabel, false)
        }
        binding.categoryField.dropdownLayout.error = state.categoryError

        // Les deux blocs sont mutuellement exclusifs (voir BudgetFormState.isLegacyRecurring et
        // fragment_budget_form.xml) : jamais affichés en même temps.
        val legacyVisibility = if (state.isLegacyRecurring) View.VISIBLE else View.GONE
        val fixedPeriodVisibility = if (state.isLegacyRecurring) View.GONE else View.VISIBLE
        binding.periodLabel.visibility = legacyVisibility
        binding.periodGroup.visibility = legacyVisibility
        binding.quickRangeLabel.visibility = fixedPeriodVisibility
        binding.quickRangeGroup.visibility = fixedPeriodVisibility
        binding.startDateRow.visibility = fixedPeriodVisibility
        binding.endDateRow.visibility = fixedPeriodVisibility

        val expectedPeriodButtonId = if (state.period == BudgetPeriod.WEEKLY) R.id.periodWeekly else R.id.periodMonthly
        if (binding.periodGroup.checkedButtonId != expectedPeriodButtonId) {
            binding.periodGroup.check(expectedPeriodButtonId)
        }

        val expectedQuickRangeButtonId = buttonIdForQuickRange(state.quickRange)
        if (binding.quickRangeGroup.checkedButtonId != expectedQuickRangeButtonId) {
            if (expectedQuickRangeButtonId == View.NO_ID) {
                binding.quickRangeGroup.clearChecked()
            } else {
                binding.quickRangeGroup.check(expectedQuickRangeButtonId)
            }
        }
        binding.startDateField.dateFieldValue.text = formatDate(state.startDate)
        binding.endDateField.dateFieldValue.text = formatDate(state.endDate)
        binding.dateErrorText.text = state.dateError
        binding.dateErrorText.visibility = if (state.dateError != null) View.VISIBLE else View.GONE

        if (binding.limitInput.text?.toString() != state.limitInput) {
            binding.limitInput.setText(state.limitInput)
        }
        binding.limitLayout.error = state.limitError

        val currencyLabel = SupportedCurrency.entries.firstOrNull { it.code == state.currencyCode }
            ?.let { "${it.displayName} (${it.symbol})" }
            .orEmpty()
        if (binding.currencyField.dropdownInput.text?.toString() != currencyLabel) {
            binding.currencyField.dropdownInput.setText(currencyLabel, false)
        }
    }

    private fun handleEvent(event: BudgetFormEvent) {
        when (event) {
            BudgetFormEvent.Saved, BudgetFormEvent.Deleted -> findNavController().navigateUp()
        }
    }

    private fun confirmDelete() {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.budgets_delete_title),
            message = getString(R.string.budgets_delete_message),
            onConfirm = { viewModel.delete() }
        )
    }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
