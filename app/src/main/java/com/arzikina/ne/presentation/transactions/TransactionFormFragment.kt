package com.arzikina.ne.presentation.transactions

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentTransactionFormBinding
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formulaire d'ajout/édition d'une transaction. Reconstruit en XML/Views
 * (voir instructions projet) ; [TransactionFormViewModel] est inchangé.
 *
 * Les champs date/heure sont des [com.google.android.material.textfield.TextInputEditText]
 * non focusables (voir `fragment_transaction_form.xml`) : la saisie se fait
 * uniquement via [MaterialDatePicker]/[MaterialTimePicker], jamais au
 * clavier, donc pas de garde anti-boucle nécessaire pour ces deux champs
 * (contrairement à `amountInput`/`descriptionInput`).
 */
@AndroidEntryPoint
class TransactionFormFragment : Fragment(R.layout.fragment_transaction_form) {

    private val viewModel: TransactionFormViewModel by viewModels()
    private var binding: FragmentTransactionFormBinding? = null

    private var latestAccounts: List<Account> = emptyList()
    private var latestCategories: List<Category> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentTransactionFormBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        setUpTypeToggle(viewBinding)
        setUpDropdowns(viewBinding)
        setUpPaymentMethodDropdown(viewBinding)
        setUpDateTime(viewBinding)
        setUpInputs(viewBinding)

        viewBinding.saveButton.setOnClickListener { viewModel.save() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        viewModel.formState,
                        viewModel.accounts,
                        viewModel.categories
                    ) { state, accounts, categories -> Triple(state, accounts, categories) }
                        .collect { (state, accounts, categories) -> render(state, accounts, categories) }
                }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpToolbar(binding: FragmentTransactionFormBinding) {
        binding.toolbar.title = getString(
            if (viewModel.isEditMode) R.string.transaction_form_title_edit else R.string.transaction_form_title_add
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

    private fun setUpTypeToggle(binding: FragmentTransactionFormBinding) {
        binding.typeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val type = if (checkedId == R.id.typeIncome) TransactionType.INCOME else TransactionType.EXPENSE
            viewModel.onTypeChange(type)
        }
    }

    private fun setUpDropdowns(binding: FragmentTransactionFormBinding) {
        binding.accountInput.setOnItemClickListener { _, _, position, _ ->
            latestAccounts.getOrNull(position)?.let { viewModel.onAccountChange(it.id) }
        }
        binding.categoryInput.setOnItemClickListener { _, _, position, _ ->
            latestCategories.getOrNull(position)?.let { viewModel.onCategoryChange(it.id) }
        }
    }

    /**
     * Liste FERMÉE et fixe (contrairement à setUpDropdowns, qui dépend de
     * données chargées) : un premier item "Non précisé" (index 0) remet le
     * champ à `null`, les suivants correspondent 1-à-1 à [PaymentMethod.entries].
     */
    private fun setUpPaymentMethodDropdown(binding: FragmentTransactionFormBinding) {
        val labels = listOf(getString(R.string.transaction_form_payment_method_none)) +
            PaymentMethod.entries.map { getString(it.displayTextRes()) }
        binding.paymentMethodInput.setSimpleItems(labels.toTypedArray())
        binding.paymentMethodInput.setOnItemClickListener { _, _, position, _ ->
            val method = PaymentMethod.entries.getOrNull(position - 1)
            viewModel.onPaymentMethodChange(method)
        }
    }

    private fun setUpDateTime(binding: FragmentTransactionFormBinding) {
        binding.dateInput.setOnClickListener { showDatePicker() }
        binding.dateLayout.setEndIconOnClickListener { showDatePicker() }
        binding.timeInput.setOnClickListener { showTimePicker() }
        binding.timeLayout.setEndIconOnClickListener { showTimePicker() }
    }

    private fun setUpInputs(binding: FragmentTransactionFormBinding) {
        binding.amountInput.doAfterTextChanged { text ->
            viewModel.onAmountChange(text?.toString().orEmpty())
        }
        binding.descriptionInput.doAfterTextChanged { text ->
            viewModel.onDescriptionChange(text?.toString().orEmpty())
        }
    }

    private fun showDatePicker() {
        val currentDate = Instant.ofEpochMilli(viewModel.formState.value.dateTimeMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val selectionUtcMillis = currentDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.transaction_form_date_label))
            .setSelection(selectionUtcMillis)
            .build()
        picker.addOnPositiveButtonClickListener { selectionMillis ->
            val selectedDate = Instant.ofEpochMilli(selectionMillis).atZone(ZoneOffset.UTC).toLocalDate()
            viewModel.onDateChange(selectedDate)
        }
        picker.show(parentFragmentManager, "transaction_date_picker")
    }

    private fun showTimePicker() {
        val currentTime = Instant.ofEpochMilli(viewModel.formState.value.dateTimeMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()

        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(currentTime.hour)
            .setMinute(currentTime.minute)
            .setTitleText(getString(R.string.transaction_form_time_label))
            .build()
        picker.addOnPositiveButtonClickListener {
            viewModel.onTimeChange(LocalTime.of(picker.hour, picker.minute))
        }
        picker.show(parentFragmentManager, "transaction_time_picker")
    }

    private fun render(state: TransactionFormState, accounts: List<Account>, categories: List<Category>) {
        val binding = binding ?: return
        latestAccounts = accounts
        latestCategories = categories

        if (binding.amountInput.text?.toString() != state.amountInput) {
            binding.amountInput.setText(state.amountInput)
        }
        binding.amountLayout.error = state.amountError

        val expectedTypeButtonId = if (state.type == TransactionType.INCOME) R.id.typeIncome else R.id.typeExpense
        if (binding.typeGroup.checkedButtonId != expectedTypeButtonId) {
            binding.typeGroup.check(expectedTypeButtonId)
        }

        binding.accountInput.setSimpleItems(accounts.map { it.name }.toTypedArray())
        val accountLabel = accounts.firstOrNull { it.id == state.accountId }?.name.orEmpty()
        if (binding.accountInput.text?.toString() != accountLabel) {
            binding.accountInput.setText(accountLabel, false)
        }
        binding.accountLayout.error = state.accountError

        binding.categoryInput.setSimpleItems(categories.map { it.name }.toTypedArray())
        val categoryLabel = categories.firstOrNull { it.id == state.categoryId }?.name.orEmpty()
        if (binding.categoryInput.text?.toString() != categoryLabel) {
            binding.categoryInput.setText(categoryLabel, false)
        }
        binding.categoryLayout.error = state.categoryError

        val zonedDateTime = Instant.ofEpochMilli(state.dateTimeMillis).atZone(ZoneId.systemDefault())
        binding.dateInput.setText(zonedDateTime.toLocalDate().format(DATE_FORMATTER))
        binding.timeInput.setText(zonedDateTime.toLocalTime().format(TIME_FORMATTER))

        if (binding.descriptionInput.text?.toString() != state.description) {
            binding.descriptionInput.setText(state.description)
        }

        val paymentMethodLabel = state.paymentMethod?.let { getString(it.displayTextRes()) }
            ?: getString(R.string.transaction_form_payment_method_none)
        if (binding.paymentMethodInput.text?.toString() != paymentMethodLabel) {
            binding.paymentMethodInput.setText(paymentMethodLabel, false)
        }
    }

    private fun handleEvent(event: TransactionFormEvent) {
        when (event) {
            TransactionFormEvent.Saved, TransactionFormEvent.Deleted -> findNavController().navigateUp()
        }
    }

    private fun confirmDelete() {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.transactions_delete_title),
            message = getString(R.string.transactions_delete_message),
            onConfirm = { viewModel.delete() }
        )
    }

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
    }
}
