package com.arzikina.ne.presentation.utilities.financialplan

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
import androidx.recyclerview.widget.LinearLayoutManager
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentFinancialPlanFormBinding
import com.arzikina.ne.domain.model.FinancialPlanIcon
import com.arzikina.ne.domain.model.PlanPeriodType
import com.arzikina.ne.presentation.components.ColorPickerAdapter
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.presentation.components.IconPickerAdapter
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Formulaire d'ajout/édition d'une planification — nom, montant disponible, icône, couleur (voir
 * [FinancialPlanFormViewModel] pour le périmètre exact de cette première étape). Même squelette
 * que [com.arzikina.ne.presentation.budget.BudgetFormFragment] pour la structure générale, et
 * même câblage des sélecteurs icône/couleur que
 * [com.arzikina.ne.presentation.accounts.AccountFormFragment] (`IconPickerAdapter<T>` générique,
 * `ColorPickerAdapter`).
 */
@AndroidEntryPoint
class FinancialPlanFormFragment : Fragment(R.layout.fragment_financial_plan_form) {

    private val viewModel: FinancialPlanFormViewModel by viewModels()
    private var binding: FragmentFinancialPlanFormBinding? = null

    private val iconPickerAdapter = IconPickerAdapter(
        items = FinancialPlanIcon.entries,
        iconRes = FinancialPlanIconMapper::iconFor,
        selected = FinancialPlanIcon.WALLET,
        onSelect = { icon -> viewModel.onIconChange(icon) }
    )

    private val colorPickerAdapter = ColorPickerAdapter(
        selected = FinancialPlanFormState.DEFAULT_COLOR_ARGB,
        onSelect = { color -> viewModel.onColorChange(color) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentFinancialPlanFormBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        setUpPickers(viewBinding)
        setUpInputs(viewBinding)
        setUpPeriodTypeDropdown(viewBinding)
        setUpDatePickers(viewBinding)

        viewBinding.saveButton.setOnClickListener { viewModel.save() }

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

    private fun setUpToolbar(binding: FragmentFinancialPlanFormBinding) {
        binding.toolbar.title = getString(
            if (viewModel.isEditMode) R.string.financial_plan_form_title_edit else R.string.financial_plan_form_title_add
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

    private fun setUpPickers(binding: FragmentFinancialPlanFormBinding) {
        binding.iconPicker.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.iconPicker.adapter = iconPickerAdapter
        binding.colorPicker.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.colorPicker.adapter = colorPickerAdapter
    }

    private fun setUpInputs(binding: FragmentFinancialPlanFormBinding) {
        binding.nameInput.doAfterTextChanged { text ->
            viewModel.onNameChange(text?.toString().orEmpty())
        }
        binding.availableAmountInput.doAfterTextChanged { text ->
            viewModel.onAvailableAmountChange(text?.toString().orEmpty())
        }
        binding.targetAmountInput.doAfterTextChanged { text ->
            viewModel.onTargetAmountChange(text?.toString().orEmpty())
        }
    }

    /** Liste FERMÉE et fixe, 1-à-1 avec [PlanPeriodType.entries] — même principe que
     * `RecurringTransactionFormFragment.setUpFrequencyDropdown`. */
    private fun setUpPeriodTypeDropdown(binding: FragmentFinancialPlanFormBinding) {
        binding.periodField.dropdownLayout.hint = getString(R.string.financial_plan_form_period_label)
        val labels = PlanPeriodType.entries.map { getString(it.labelRes()) }
        binding.periodField.dropdownInput.setSimpleItems(labels.toTypedArray())
        binding.periodField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            PlanPeriodType.entries.getOrNull(position)?.let { viewModel.onPeriodTypeChange(it) }
        }
    }

    private fun setUpDatePickers(binding: FragmentFinancialPlanFormBinding) {
        binding.startDateField.dateFieldLabel.text = getString(R.string.financial_plan_form_start_date_label)
        binding.startDateRow.setOnClickListener {
            showDatePicker(R.string.financial_plan_form_start_date_label) { viewModel.onStartDateChange(it) }
        }

        binding.endDateField.dateFieldLabel.text = getString(R.string.financial_plan_form_end_date_label)
        binding.endDateRow.setOnClickListener {
            showDatePicker(R.string.financial_plan_form_end_date_label) { viewModel.onEndDateChange(it) }
        }
    }

    /** Voir `RecurringTransactionFormFragment.showDatePicker` pour le même raisonnement
     * (reconversion UTC → heure locale). */
    private fun showDatePicker(@StringRes titleRes: Int, onSelected: (Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(titleRes)
            .build()
        picker.addOnPositiveButtonClickListener { selectionUtcMillis ->
            val localDate = Instant.ofEpochMilli(selectionUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            onSelected(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
        picker.show(parentFragmentManager, "financial_plan_form_date_picker")
    }

    private fun render(state: FinancialPlanFormState) {
        val binding = binding ?: return

        if (binding.nameInput.text?.toString() != state.nameInput) {
            binding.nameInput.setText(state.nameInput)
        }
        binding.nameLayout.error = state.nameError

        if (binding.availableAmountInput.text?.toString() != state.availableAmountInput) {
            binding.availableAmountInput.setText(state.availableAmountInput)
        }
        binding.availableAmountLayout.error = state.availableAmountError

        if (binding.targetAmountInput.text?.toString() != state.targetAmountInput) {
            binding.targetAmountInput.setText(state.targetAmountInput)
        }
        binding.targetAmountLayout.error = state.targetAmountError

        val periodLabel = getString(state.periodType.labelRes())
        if (binding.periodField.dropdownInput.text?.toString() != periodLabel) {
            binding.periodField.dropdownInput.setText(periodLabel, false)
        }

        val hasPeriod = state.periodType != PlanPeriodType.NONE
        binding.startDateRow.visibility = if (hasPeriod) View.VISIBLE else View.GONE
        binding.endDateRow.visibility = if (hasPeriod) View.VISIBLE else View.GONE
        binding.startDateField.dateFieldValue.text = formatDate(state.startDate)
        binding.endDateField.dateFieldValue.text = formatDate(state.endDate)
        binding.dateErrorText.text = state.dateError
        binding.dateErrorText.visibility = if (state.dateError != null) View.VISIBLE else View.GONE

        iconPickerAdapter.setSelected(state.icon)
        colorPickerAdapter.setSelected(state.colorArgb)
    }

    private fun formatDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMATTER)

    private fun handleEvent(event: FinancialPlanFormEvent) {
        when (event) {
            FinancialPlanFormEvent.Saved, FinancialPlanFormEvent.Deleted -> findNavController().navigateUp()
        }
    }

    private fun confirmDelete() {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.financial_plans_delete_title),
            message = getString(R.string.financial_plans_delete_message),
            onConfirm = { viewModel.delete() }
        )
    }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
