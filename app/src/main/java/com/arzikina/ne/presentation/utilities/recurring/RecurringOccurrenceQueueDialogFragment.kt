package com.arzikina.ne.presentation.utilities.recurring

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.arzikina.ne.R
import com.arzikina.ne.databinding.DialogRecurringOccurrenceQueueBinding
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.presentation.accounts.AccountIconMapper
import com.arzikina.ne.presentation.components.AccountPickerDialog
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.presentation.transactions.displayTextRes
import com.arzikina.ne.util.Money
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Dialogue de validation en file d'attente des occurrences `PENDING` (voir cahier des charges,
 * section "Dialog") — affiché une fois par ouverture d'app (voir `MainActivity`), traite les
 * occurrences UNE PAR UNE :
 * - "Enregistrer" ([RecurringOccurrenceQueueViewModel.accept]) crée la transaction à partir des
 *   valeurs actuelles de la règle et passe à l'occurrence suivante ;
 * - "Rejeter" ([RecurringOccurrenceQueueViewModel.reject]), avec confirmation (voir
 *   [confirmReject] — action définitive, voir [ConfirmDialogs]) marque l'occurrence `REJECTED`
 *   sans créer de transaction et passe à la suivante ;
 * - "Modifier" ([RecurringOccurrenceQueueViewModel.startEdit]) bascule vers un formulaire d'édition
 *   compact (montant/compte/catégorie/description/moyen de paiement/date/type, voir
 *   [OccurrenceEditState]) ; "Confirmer" ([RecurringOccurrenceQueueViewModel.confirmEdit]) crée la
 *   transaction à partir des valeurs modifiées SANS jamais toucher à la règle d'origine (voir
 *   `RecurringTransactionRepository.acceptOccurrenceWithChanges`) et passe à l'occurrence suivante ;
 *   "Annuler" ([RecurringOccurrenceQueueViewModel.cancelEdit]) revient au résumé en lecture sans
 *   rien enregistrer.
 *
 * Le dialogue se ferme automatiquement une fois la file vide ([RecurringOccurrenceQueueEvent.Dismiss]).
 * Fermable par ailleurs à tout moment (bouton retour/tap extérieur, comportement par défaut d'un
 * [DialogFragment], non modifié ici) : une occurrence non traitée reste simplement `PENDING` et sera
 * re-proposée à la prochaine ouverture — seule une action EXPLICITE (Enregistrer/Rejeter/Confirmer)
 * change son état, jamais une fermeture du dialogue (voir la doc de `RecurringTransactionRepository`,
 * "jamais silencieusement rejetée").
 */
@AndroidEntryPoint
class RecurringOccurrenceQueueDialogFragment : DialogFragment() {

    private val viewModel: RecurringOccurrenceQueueViewModel by viewModels()
    private var binding: DialogRecurringOccurrenceQueueBinding? = null

    /** Dernières valeurs reçues de [RecurringOccurrenceQueueViewModel.accounts]/
     * [RecurringOccurrenceQueueViewModel.categories] (voir [render]) — même principe que
     * `RecurringTransactionFormFragment.latestAccounts`/`latestCategories`. */
    private var latestAccounts: List<Account> = emptyList()
    private var latestCategories: List<Category> = emptyList()
    private var latestAccountBalances: Map<Long, Long> = emptyMap()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val viewBinding = DialogRecurringOccurrenceQueueBinding.inflate(inflater, container, false)
        binding = viewBinding
        return viewBinding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        // Fond transparent : seule la MaterialCardView du layout dessine un arrière-plan (coins
        // arrondis) — sans ça, le rectangle par défaut d'un Dialog dépasserait de la carte.
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = binding ?: return

        setUpReadActions(viewBinding)
        setUpEditForm(viewBinding)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        viewModel.uiState,
                        viewModel.accounts,
                        viewModel.categories,
                        viewModel.accountBalances
                    ) { state, accounts, categories, balances -> RenderState(state, accounts, categories, balances) }
                        .collect { data -> render(data) }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            RecurringOccurrenceQueueEvent.Dismiss -> dismissAllowingStateLoss()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpReadActions(binding: DialogRecurringOccurrenceQueueBinding) {
        binding.saveButton.setOnClickListener { viewModel.accept() }
        binding.editButton.setOnClickListener { viewModel.startEdit() }
        binding.rejectButton.setOnClickListener { confirmReject() }
    }

    private fun confirmReject() {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.recurring_queue_reject_confirm_title),
            message = getString(R.string.recurring_queue_reject_confirm_message),
            confirmLabel = getString(R.string.recurring_queue_reject_action),
            onConfirm = { viewModel.reject() }
        )
    }

    private fun setUpEditForm(binding: DialogRecurringOccurrenceQueueBinding) {
        binding.editTypeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val type = if (checkedId == R.id.editTypeIncomeButton) TransactionType.INCOME else TransactionType.EXPENSE
            viewModel.onEditTypeChange(type)
        }

        binding.editCategoryField.dropdownLayout.hint = getString(R.string.recurring_transaction_form_category_label)
        binding.editCategoryField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            latestCategories.getOrNull(position)?.let { viewModel.onEditCategoryChange(it.id) }
        }

        binding.editAmountInput.doAfterTextChanged { text -> viewModel.onEditAmountChange(text?.toString().orEmpty()) }

        binding.editAccountRow.setOnClickListener {
            AccountPickerDialog.show(
                context = requireContext(),
                accounts = latestAccounts,
                balanceFor = { account -> latestAccountBalances[account.id] ?: account.initialBalance },
                onSelect = { account -> viewModel.onEditAccountChange(account.id) }
            )
        }

        binding.editDescriptionInput.doAfterTextChanged { text -> viewModel.onEditDescriptionChange(text?.toString().orEmpty()) }

        binding.editPaymentMethodField.dropdownLayout.hint = getString(R.string.transaction_form_payment_method_label)
        val paymentMethodLabels = listOf(getString(R.string.transaction_form_payment_method_none)) +
            PaymentMethod.entries.map { getString(it.displayTextRes()) }
        binding.editPaymentMethodField.dropdownInput.setSimpleItems(paymentMethodLabels.toTypedArray())
        binding.editPaymentMethodField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            viewModel.onEditPaymentMethodChange(PaymentMethod.entries.getOrNull(position - 1))
        }

        binding.editDateField.dateFieldLabel.text = getString(R.string.recurring_queue_edit_date_label)
        binding.editDateRow.setOnClickListener {
            showDatePicker(R.string.recurring_queue_edit_date_label) { viewModel.onEditDateChange(it) }
        }

        binding.cancelEditButton.setOnClickListener { viewModel.cancelEdit() }
        binding.confirmEditButton.setOnClickListener { viewModel.confirmEdit() }
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
        picker.show(parentFragmentManager, "recurring_queue_edit_date_picker")
    }

    private fun render(data: RenderState) {
        val binding = binding ?: return
        val state = data.uiState
        val item = state.currentItem ?: return
        latestAccounts = data.accounts
        latestCategories = data.categories
        latestAccountBalances = data.accountBalances

        binding.queueProgress.text = getString(R.string.recurring_queue_progress, state.currentPosition, state.totalCount)

        val isEditing = state.editState != null
        binding.readSection.visibility = if (isEditing) View.GONE else View.VISIBLE
        binding.readActions.visibility = if (isEditing) View.GONE else View.VISIBLE
        binding.editSection.visibility = if (isEditing) View.VISIBLE else View.GONE
        binding.editActions.visibility = if (isEditing) View.VISIBLE else View.GONE

        if (isEditing) {
            renderEditForm(binding, state.editState!!)
        } else {
            RecurringOccurrenceItemBinder.bind(binding.occurrenceSummary, item, RecurringSection.PENDING)
            binding.accountLine.text = getString(
                R.string.recurring_queue_account_line,
                item.account?.name ?: getString(R.string.transaction_form_account_placeholder)
            )
        }

        val busy = state.isProcessing
        binding.loadingIndicator.visibility = if (busy) View.VISIBLE else View.GONE
        binding.saveButton.isEnabled = !busy
        binding.editButton.isEnabled = !busy
        binding.rejectButton.isEnabled = !busy
        binding.cancelEditButton.isEnabled = !busy
        binding.confirmEditButton.isEnabled = !busy
    }

    private fun renderEditForm(binding: DialogRecurringOccurrenceQueueBinding, edit: OccurrenceEditState) {
        val expectedTypeButtonId = if (edit.type == TransactionType.INCOME) R.id.editTypeIncomeButton else R.id.editTypeExpenseButton
        if (binding.editTypeGroup.checkedButtonId != expectedTypeButtonId) {
            binding.editTypeGroup.check(expectedTypeButtonId)
        }

        binding.editCategoryField.dropdownInput.setSimpleItems(latestCategories.map { it.name }.toTypedArray())
        val categoryLabel = latestCategories.firstOrNull { it.id == edit.categoryId }?.name.orEmpty()
        if (binding.editCategoryField.dropdownInput.text?.toString() != categoryLabel) {
            binding.editCategoryField.dropdownInput.setText(categoryLabel, false)
        }
        binding.editCategoryField.dropdownLayout.error = edit.categoryError

        if (binding.editAmountInput.text?.toString() != edit.amountInput) {
            binding.editAmountInput.setText(edit.amountInput)
        }
        binding.editAmountLayout.error = edit.amountError

        val selectedAccount = latestAccounts.firstOrNull { it.id == edit.accountId }
        bindAccountField(binding, selectedAccount)
        binding.editAccountErrorText.text = edit.accountError
        binding.editAccountErrorText.visibility = if (edit.accountError != null) View.VISIBLE else View.GONE

        if (binding.editDescriptionInput.text?.toString() != edit.description) {
            binding.editDescriptionInput.setText(edit.description)
        }

        val paymentMethodLabel = edit.paymentMethod?.let { getString(it.displayTextRes()) }
            ?: getString(R.string.transaction_form_payment_method_none)
        if (binding.editPaymentMethodField.dropdownInput.text?.toString() != paymentMethodLabel) {
            binding.editPaymentMethodField.dropdownInput.setText(paymentMethodLabel, false)
        }

        binding.editDateField.dateFieldValue.text = formatDate(edit.date)
    }

    /** Même logique que `RecurringTransactionFormFragment.bindAccountField` (voir sa doc) : petite
     * duplication assumée, chaque formulaire garde son propre binder. Solde COURANT (voir
     * [RecurringOccurrenceQueueViewModel.accountBalances]), jamais [Account.initialBalance] seul. */
    private fun bindAccountField(binding: DialogRecurringOccurrenceQueueBinding, account: Account?) {
        val fieldBinding = binding.editAccountField
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
    private data class RenderState(
        val uiState: RecurringOccurrenceQueueUiState,
        val accounts: List<Account>,
        val categories: List<Category>,
        val accountBalances: Map<Long, Long>
    )

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
