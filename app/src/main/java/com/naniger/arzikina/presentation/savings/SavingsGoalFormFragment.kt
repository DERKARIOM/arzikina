package com.naniger.arzikina.presentation.savings

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentSavingsGoalFormBinding
import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.domain.model.SupportedCurrency
import com.naniger.arzikina.presentation.components.ConfirmDialogs
import com.naniger.arzikina.presentation.components.pickerLabel
import com.naniger.arzikina.util.AppDateFormats
import com.naniger.arzikina.util.DatePeriods
import com.naniger.arzikina.util.Money
import com.naniger.arzikina.util.MoneyInputFormatter
import com.naniger.arzikina.util.SavingsSuggestion
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

/**
 * Formulaire « Nouvel objectif » / « Modifier l'objectif » (voir la maquette, écran 3) : nom,
 * montant cible, devise, montant déjà épargné, échéance facultative et suggestion mensuelle en
 * direct. Même structure et mêmes composants (cartes « postcard », sélecteur de date, menu
 * Supprimer) que [com.naniger.arzikina.presentation.utilities.financialplan.FinancialPlanFormFragment].
 */
@AndroidEntryPoint
class SavingsGoalFormFragment : Fragment(R.layout.fragment_savings_goal_form) {

    private val viewModel: SavingsGoalFormViewModel by viewModels()
    private var binding: FragmentSavingsGoalFormBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentSavingsGoalFormBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        setUpInputs(viewBinding)
        setUpCurrencyDropdown(viewBinding)
        setUpDeadline(viewBinding)
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

    private fun setUpToolbar(binding: FragmentSavingsGoalFormBinding) {
        binding.toolbar.title = getString(
            if (viewModel.isEditMode) R.string.savings_goal_form_title_edit else R.string.savings_goal_form_title_add
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

    private fun setUpInputs(binding: FragmentSavingsGoalFormBinding) {
        binding.nameInput.doAfterTextChanged { text -> viewModel.onNameChange(text?.toString().orEmpty()) }
        MoneyInputFormatter.attach(binding.targetInput) { formatted -> viewModel.onTargetChange(formatted) }
        MoneyInputFormatter.attach(binding.currentInput) { formatted -> viewModel.onCurrentChange(formatted) }
    }

    /** Liste fermée [SupportedCurrency], mêmes libellés que Paramètres → Devise. */
    private fun setUpCurrencyDropdown(binding: FragmentSavingsGoalFormBinding) {
        binding.currencyField.dropdownLayout.hint = getString(R.string.settings_currency_label)
        val labels = SupportedCurrency.entries.map { it.pickerLabel(requireContext()) }
        binding.currencyField.dropdownInput.setSimpleItems(labels.toTypedArray())
        binding.currencyField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            SupportedCurrency.entries.getOrNull(position)?.let { viewModel.onCurrencyChange(it.code) }
        }
    }

    private fun setUpDeadline(binding: FragmentSavingsGoalFormBinding) {
        binding.deadlineSwitch.setOnCheckedChangeListener { _, isChecked -> viewModel.onHasDeadlineChange(isChecked) }
        binding.deadlineField.dateFieldLabel.text = getString(R.string.savings_goal_form_deadline_label)
        binding.deadlineRow.setOnClickListener { showDatePicker() }
    }

    /** Même conversion que les autres formulaires : le sélecteur renvoie minuit UTC, on garde la
     *  DATE choisie et on la ramène à minuit heure locale. */
    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.savings_goal_form_deadline_label)
            .setSelection(
                DatePeriods.toLocalDate(viewModel.formState.value.deadlineMillis)
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            .build()
        picker.addOnPositiveButtonClickListener { selectionUtcMillis ->
            val date = Instant.ofEpochMilli(selectionUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            viewModel.onDeadlineChange(DatePeriods.toEpochMillis(date))
        }
        picker.show(parentFragmentManager, "savings_goal_deadline_picker")
    }

    private fun render(state: SavingsGoalFormState) {
        val binding = binding ?: return

        if (binding.nameInput.text?.toString() != state.name) binding.nameInput.setText(state.name)
        binding.nameLayout.error = state.nameError?.let { getString(it) }

        if (binding.targetInput.text?.toString() != state.targetInput) binding.targetInput.setText(state.targetInput)
        binding.targetLayout.error = state.targetError?.let { getString(it) }

        if (binding.currentInput.text?.toString() != state.currentInput) binding.currentInput.setText(state.currentInput)
        binding.currentLayout.error = state.currentError?.let { getString(it) }

        val currency = SupportedCurrency.entries.firstOrNull { it.code == state.currencyCode }
        val currencyLabel = currency?.pickerLabel(requireContext()) ?: state.currencyCode
        if (binding.currencyField.dropdownInput.text?.toString() != currencyLabel) {
            binding.currencyField.dropdownInput.setText(currencyLabel, false)
        }
        val suffix = currency?.symbol ?: state.currencyCode
        binding.targetLayout.suffixText = suffix
        binding.currentLayout.suffixText = suffix

        if (binding.deadlineSwitch.isChecked != state.hasDeadline) binding.deadlineSwitch.isChecked = state.hasDeadline
        binding.deadlineCard.visibility = if (state.hasDeadline) View.VISIBLE else View.GONE
        binding.deadlineField.dateFieldValue.text =
            DatePeriods.toLocalDate(state.deadlineMillis).format(AppDateFormats.longDate(requireContext()))

        val suggestionText = when (val suggestion = state.suggestion) {
            is SavingsSuggestion.PerMonth -> getString(
                R.string.savings_goal_suggestion_monthly,
                Money.format(CurrencyAmount(state.currencyCode, suggestion.amountMinor))
            )
            is SavingsSuggestion.BeforeDeadline -> getString(
                R.string.savings_goal_suggestion_before_deadline,
                Money.format(CurrencyAmount(state.currencyCode, suggestion.amountMinor))
            )
            null -> null
        }
        binding.suggestionText.visibility = if (suggestionText != null) View.VISIBLE else View.GONE
        binding.suggestionText.text = suggestionText
    }

    private fun handleEvent(event: SavingsGoalFormEvent) {
        when (event) {
            SavingsGoalFormEvent.Saved, SavingsGoalFormEvent.Deleted -> findNavController().navigateUp()
        }
    }

    private fun confirmDelete() {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.savings_goals_delete_title),
            message = getString(R.string.savings_goals_delete_message, viewModel.formState.value.name.trim()),
            onConfirm = { viewModel.delete() }
        )
    }
}
