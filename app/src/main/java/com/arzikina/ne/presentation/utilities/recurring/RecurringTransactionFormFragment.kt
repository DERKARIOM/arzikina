package com.arzikina.ne.presentation.utilities.recurring

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentRecurringTransactionFormBinding
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.RecurringFrequency
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.presentation.accounts.AccountIconMapper
import com.arzikina.ne.presentation.components.AccountPickerDialog
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.presentation.transactions.displayTextRes
import com.arzikina.ne.util.Money
import com.arzikina.ne.util.TriggerTimeFormatter
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Formulaire de création/édition d'une règle récurrente (voir [RecurringTransactionFormViewModel]).
 * Une seule page, sur le modèle de [com.arzikina.ne.presentation.budget.BudgetFormFragment] —
 * champs déroulants harmonisés via `item_dropdown_field`/`item_date_field`/`item_account_field`
 * (voir fragment_recurring_transaction_form.xml), même principe que
 * [com.arzikina.ne.presentation.utilities.loans.LoanFormFragment].
 *
 * [findNavController]`.popBackStack()` sur enregistrement/suppression réussi : retour à
 * [RecurringTransactionsFragment], qui observe déjà [RecurringTransactionsViewModel] et se met donc
 * à jour automatiquement, sans rechargement explicite.
 */
@AndroidEntryPoint
class RecurringTransactionFormFragment : Fragment(R.layout.fragment_recurring_transaction_form) {

    private val viewModel: RecurringTransactionFormViewModel by viewModels()
    private var binding: FragmentRecurringTransactionFormBinding? = null

    /**
     * Dernières valeurs reçues de [RecurringTransactionFormViewModel.accounts]/[RecurringTransactionFormViewModel.categories]/
     * [RecurringTransactionFormViewModel.accountBalances] (voir [render]) : ces trois `StateFlow`
     * utilisent `SharingStarted.WhileSubscribed` (voir leur doc), donc leur source ne se met à jour
     * QUE pendant qu'un collecteur est actif — les stocker ici, à jour à chaque [render], permet aux
     * gestionnaires de clic ([setUpPickers]/[setUpCategoryDropdown]) d'y accéder de façon synchrone
     * sans les collecter une seconde fois. Même principe que `LoanFormFragment.latestAccounts`.
     */
    private var latestAccounts: List<Account> = emptyList()
    private var latestCategories: List<Category> = emptyList()
    private var latestAccountBalances: Map<Long, Long> = emptyMap()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentRecurringTransactionFormBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        setUpTypeToggle(viewBinding)
        setUpCategoryDropdown(viewBinding)
        setUpPaymentMethodDropdown(viewBinding)
        setUpFrequencyDropdown(viewBinding)
        setUpPickers(viewBinding)
        setUpEndDateSwitch(viewBinding)
        setUpInputs(viewBinding)
        setUpPrimaryActions(viewBinding)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        viewModel.formState,
                        viewModel.accounts,
                        viewModel.categories,
                        viewModel.accountBalances
                    ) { state, accounts, categories, balances ->
                        FormRenderState(state, accounts, categories, balances)
                    }.collect { data -> render(data) }
                }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpToolbar(binding: FragmentRecurringTransactionFormBinding) {
        binding.toolbar.title = getString(
            if (viewModel.isEditMode) R.string.recurring_transaction_form_title_edit else R.string.recurring_transaction_form_title_add
        )
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setUpTypeToggle(binding: FragmentRecurringTransactionFormBinding) {
        binding.typeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val type = if (checkedId == R.id.typeIncomeButton) TransactionType.INCOME else TransactionType.EXPENSE
            viewModel.onTypeChange(type)
        }
    }

    private fun setUpCategoryDropdown(binding: FragmentRecurringTransactionFormBinding) {
        binding.categoryField.dropdownLayout.hint = getString(R.string.recurring_transaction_form_category_label)
        binding.categoryField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            latestCategories.getOrNull(position)?.let { viewModel.onCategoryChange(it.id) }
        }
    }

    /** Liste FERMÉE et fixe (contrairement à setUpCategoryDropdown, qui dépend de données
     * chargées) : un premier item "Non précisé" (index 0) remet le champ à `null`, les suivants
     * correspondent 1-à-1 à [PaymentMethod.entries] — même principe que
     * `TransactionFormFragment.setUpPaymentMethodDropdown`. */
    private fun setUpPaymentMethodDropdown(binding: FragmentRecurringTransactionFormBinding) {
        binding.paymentMethodField.dropdownLayout.hint = getString(R.string.transaction_form_payment_method_label)
        val labels = listOf(getString(R.string.transaction_form_payment_method_none)) +
            PaymentMethod.entries.map { getString(it.displayTextRes()) }
        binding.paymentMethodField.dropdownInput.setSimpleItems(labels.toTypedArray())
        binding.paymentMethodField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            viewModel.onPaymentMethodChange(PaymentMethod.entries.getOrNull(position - 1))
        }
    }

    /** Liste FERMÉE et fixe, 1-à-1 avec [RecurringFrequency.entries] (voir [RecurringFrequencyDisplay]). */
    private fun setUpFrequencyDropdown(binding: FragmentRecurringTransactionFormBinding) {
        binding.frequencyField.dropdownLayout.hint = getString(R.string.recurring_transaction_form_frequency_label)
        val labels = RecurringFrequency.entries.map { getString(it.labelRes()) }
        binding.frequencyField.dropdownInput.setSimpleItems(labels.toTypedArray())
        binding.frequencyField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            RecurringFrequency.entries.getOrNull(position)?.let { viewModel.onFrequencyChange(it) }
        }
    }

    private fun setUpPickers(binding: FragmentRecurringTransactionFormBinding) {
        binding.accountRow.setOnClickListener {
            AccountPickerDialog.show(
                context = requireContext(),
                accounts = latestAccounts,
                balanceFor = { account -> latestAccountBalances[account.id] ?: account.initialBalance },
                onSelect = { account -> viewModel.onAccountChange(account.id) }
            )
        }

        binding.startDateField.dateFieldLabel.text = getString(R.string.recurring_transaction_form_start_date_label)
        binding.startDateRow.setOnClickListener {
            showDatePicker(R.string.recurring_transaction_form_start_date_label) { viewModel.onStartDateChange(it) }
        }

        binding.endDateField.dateFieldLabel.text = getString(R.string.recurring_transaction_form_end_date_label)
        binding.endDateRow.setOnClickListener {
            showDatePicker(R.string.recurring_transaction_form_end_date_label) { viewModel.onEndDateChange(it) }
        }

        // item_date_field.xml est générique (voir sa doc) : icône ré-affectée ici à ic_time_24 pour
        // ce seul champ, sur le modèle des autres lignes de cet écran (label écrasé une fois après
        // inflate, jamais dans render() — voir la doc de item_date_field.xml).
        binding.triggerTimeField.dateFieldIcon.setImageResource(R.drawable.ic_time_24)
        binding.triggerTimeField.dateFieldLabel.text = getString(R.string.recurring_transaction_form_trigger_time_label)
        binding.triggerTimeRow.setOnClickListener { showTimePicker() }
    }

    private fun setUpEndDateSwitch(binding: FragmentRecurringTransactionFormBinding) {
        binding.endDateSwitch.setOnCheckedChangeListener { _, isChecked -> viewModel.onEndDateToggle(isChecked) }
    }

    private fun setUpInputs(binding: FragmentRecurringTransactionFormBinding) {
        binding.amountInput.doAfterTextChanged { text -> viewModel.onAmountChange(text?.toString().orEmpty()) }
        binding.descriptionInput.doAfterTextChanged { text -> viewModel.onDescriptionChange(text?.toString().orEmpty()) }
    }

    private fun setUpPrimaryActions(binding: FragmentRecurringTransactionFormBinding) {
        binding.saveButton.setOnClickListener { viewModel.save() }
        binding.deleteButton.setOnClickListener { confirmDelete() }
    }

    private fun confirmDelete() {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.recurring_transaction_form_delete_title),
            message = getString(R.string.recurring_transaction_form_delete_message),
            onConfirm = { viewModel.delete() }
        )
    }

    private fun handleEvent(event: RecurringTransactionFormEvent) {
        when (event) {
            RecurringTransactionFormEvent.Saved, RecurringTransactionFormEvent.Deleted -> findNavController().popBackStack()
        }
    }

    private fun showDatePicker(@StringRes titleRes: Int, onSelected: (Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(titleRes)
            .build()
        picker.addOnPositiveButtonClickListener { selectionUtcMillis ->
            // Le sélecteur retourne un instant UTC "début de journée" ; reconverti vers l'heure
            // locale pour rester cohérent avec System.currentTimeMillis() (voir LoanFormFragment).
            val localDate = Instant.ofEpochMilli(selectionUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            val localMillis = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            onSelected(localMillis)
        }
        picker.show(parentFragmentManager, "recurring_transaction_form_date_picker")
    }

    /**
     * `TimeFormat.CLOCK_24H`/`CLOCK_12H` choisi selon le réglage système (voir
     * [android.text.format.DateFormat.is24HourFormat]) — cahier des charges "Ajouter l'heure de
     * déclenchement à Automatisation", section 1 : le sélecteur doit respecter le format horaire
     * configuré sur l'appareil, contrairement à `TransactionFormFragment.showTimePicker` qui force
     * `CLOCK_24H` (précédent existant, volontairement non repris ici : la spécification de cette
     * fonctionnalité demande explicitement ce respect, et le corriger ailleurs sortirait du
     * périmètre de cette étape).
     */
    private fun showTimePicker() {
        val state = viewModel.formState.value
        val clockFormat = if (android.text.format.DateFormat.is24HourFormat(requireContext())) {
            TimeFormat.CLOCK_24H
        } else {
            TimeFormat.CLOCK_12H
        }
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(clockFormat)
            .setHour(state.triggerHour)
            .setMinute(state.triggerMinute)
            .setTitleText(getString(R.string.recurring_transaction_form_trigger_time_label))
            .build()
        picker.addOnPositiveButtonClickListener {
            viewModel.onTriggerTimeChange(picker.hour, picker.minute)
        }
        picker.show(parentFragmentManager, "recurring_transaction_form_time_picker")
    }

    private fun render(data: FormRenderState) {
        val binding = binding ?: return
        val state = data.formState
        latestAccounts = data.accounts
        latestCategories = data.categories
        latestAccountBalances = data.accountBalances

        val expectedTypeButtonId = if (state.type == TransactionType.INCOME) R.id.typeIncomeButton else R.id.typeExpenseButton
        if (binding.typeGroup.checkedButtonId != expectedTypeButtonId) {
            binding.typeGroup.check(expectedTypeButtonId)
        }

        binding.categoryField.dropdownInput.setSimpleItems(data.categories.map { it.name }.toTypedArray())
        val categoryLabel = data.categories.firstOrNull { it.id == state.categoryId }?.name.orEmpty()
        if (binding.categoryField.dropdownInput.text?.toString() != categoryLabel) {
            binding.categoryField.dropdownInput.setText(categoryLabel, false)
        }
        binding.categoryField.dropdownLayout.error = state.categoryError

        if (binding.amountInput.text?.toString() != state.amountInput) {
            binding.amountInput.setText(state.amountInput)
        }
        binding.amountLayout.error = state.amountError

        val selectedAccount = data.accounts.firstOrNull { it.id == state.accountId }
        bindAccountField(binding, selectedAccount)
        binding.accountErrorText.text = state.accountError
        binding.accountErrorText.visibility = if (state.accountError != null) View.VISIBLE else View.GONE

        if (binding.descriptionInput.text?.toString() != state.description) {
            binding.descriptionInput.setText(state.description)
        }

        val paymentMethodLabel = state.paymentMethod?.let { getString(it.displayTextRes()) }
            ?: getString(R.string.transaction_form_payment_method_none)
        if (binding.paymentMethodField.dropdownInput.text?.toString() != paymentMethodLabel) {
            binding.paymentMethodField.dropdownInput.setText(paymentMethodLabel, false)
        }

        val frequencyLabel = getString(state.frequency.labelRes())
        if (binding.frequencyField.dropdownInput.text?.toString() != frequencyLabel) {
            binding.frequencyField.dropdownInput.setText(frequencyLabel, false)
        }

        // Résumé combiné (ex. "Tous les jours à 12:30"), pas seulement l'heure seule — voir cahier
        // des charges "Ajouter l'heure de déclenchement à Automatisation", section 13.
        binding.triggerTimeField.dateFieldValue.text = getString(
            R.string.recurring_transaction_form_trigger_time_summary,
            frequencyLabel,
            TriggerTimeFormatter.format(requireContext(), state.triggerHour, state.triggerMinute)
        )

        binding.startDateField.dateFieldValue.text = formatDate(state.startDate)

        if (binding.endDateSwitch.isChecked != state.hasEndDate) {
            binding.endDateSwitch.isChecked = state.hasEndDate
        }
        binding.endDateCard.visibility = if (state.hasEndDate) View.VISIBLE else View.GONE
        binding.endDateField.dateFieldValue.text = formatDate(state.endDate)
        binding.endDateErrorText.text = state.endDateError
        binding.endDateErrorText.visibility = if (state.endDateError != null) View.VISIBLE else View.GONE

        binding.deleteButton.visibility = if (viewModel.isEditMode) View.VISIBLE else View.GONE
    }

    /** Même logique que `LoanFormFragment.bindAccountField`/`TransactionFormFragment.bindAccountField`
     * (voir leur doc) : petite duplication assumée, chaque formulaire garde son propre binder plutôt
     * que de partager une fonction entre Fragments indépendants. */
    private fun bindAccountField(binding: FragmentRecurringTransactionFormBinding, account: Account?) {
        val fieldBinding = binding.accountField
        if (account != null) {
            fieldBinding.accountFieldIcon.setImageResource(AccountIconMapper.iconFor(account.icon))
            fieldBinding.accountFieldIcon.backgroundTintList = ColorStateList.valueOf(account.colorArgb.toInt())
            fieldBinding.accountFieldName.text = account.name
            val balance = latestAccountBalances[account.id] ?: account.initialBalance
            fieldBinding.accountFieldBalance.text = getString(
                R.string.transaction_form_account_balance,
                Money.format(CurrencyAmount(account.currencyCode, balance))
            )
            fieldBinding.accountFieldBalance.visibility = View.VISIBLE
        } else {
            fieldBinding.accountFieldIcon.setImageResource(R.drawable.ic_account_other_24)
            fieldBinding.accountFieldIcon.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.arzikina_outline)
            )
            fieldBinding.accountFieldName.text = getString(R.string.transaction_form_account_placeholder)
            fieldBinding.accountFieldBalance.visibility = View.GONE
        }
    }

    private fun formatDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMATTER)

    /** Regroupe les 4 flux observés pour éviter un `combine` imbriqué illisible (voir
     * [onViewCreated]) — même principe que `LoanFormFragment.LoanFormRenderState`. */
    private data class FormRenderState(
        val formState: RecurringTransactionFormState,
        val accounts: List<Account>,
        val categories: List<Category>,
        val accountBalances: Map<Long, Long>
    )

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
