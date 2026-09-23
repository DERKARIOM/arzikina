package com.naniger.arzikina.presentation.utilities.recurring

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
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.DialogRecurringOccurrenceEditBinding
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.Category
import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.domain.model.PaymentMethod
import com.naniger.arzikina.domain.model.TransactionType
import com.naniger.arzikina.domain.model.combineDayAndTime
import com.naniger.arzikina.presentation.accounts.AccountIconMapper
import com.naniger.arzikina.presentation.components.AccountPickerDialog
import com.naniger.arzikina.presentation.transactions.displayTextRes
import com.naniger.arzikina.util.AppDateFormats
import com.naniger.arzikina.util.Money
import com.naniger.arzikina.util.MoneyInputFormatter
import com.google.android.material.datepicker.MaterialDatePicker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Formulaire d'édition d'UNE occurrence `PENDING` avant validation (voir cahier des charges
 * "Garder Modifier en action secondaire") — remplace l'ancienne `RecurringOccurrenceQueueDialogFragment`
 * (file d'attente ouverte automatiquement au lancement, supprimée). Ouvert UNIQUEMENT sur tap d'une
 * ligne "À traiter" (voir `RecurringTransactionsFragment.onOccurrenceRowClick`) — toujours en mode
 * édition dès l'ouverture, pas de résumé en lecture intermédiaire : Valider/Rejeter SANS
 * modification sont déjà accessibles directement sur la ligne, ce dialogue n'a donc plus qu'un seul
 * mode.
 *
 * "Confirmer" ([RecurringOccurrenceEditViewModel.confirm]) crée la transaction à partir des valeurs
 * modifiées SANS jamais toucher à la règle d'origine (voir
 * `RecurringTransactionRepository.acceptOccurrenceWithChanges`) puis ferme le dialogue. "Annuler"
 * ferme sans rien enregistrer — l'occurrence reste `PENDING`, toujours visible dans "À traiter".
 */
@AndroidEntryPoint
class RecurringOccurrenceEditDialogFragment : DialogFragment() {

    private val viewModel: RecurringOccurrenceEditViewModel by viewModels()
    private var binding: DialogRecurringOccurrenceEditBinding? = null

    /** Dernières valeurs reçues de [RecurringOccurrenceEditViewModel.accounts]/
     * [RecurringOccurrenceEditViewModel.categories] (voir [render]) — même principe que
     * `RecurringTransactionFormFragment.latestAccounts`/`latestCategories`. */
    private var latestAccounts: List<Account> = emptyList()
    private var latestCategories: List<Category> = emptyList()
    private var latestAccountBalances: Map<Long, Long> = emptyMap()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val viewBinding = DialogRecurringOccurrenceEditBinding.inflate(inflater, container, false)
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

        setUpForm(viewBinding)
        viewBinding.cancelButton.setOnClickListener { dismissAllowingStateLoss() }
        viewBinding.confirmButton.setOnClickListener { viewModel.confirm() }

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
                            RecurringOccurrenceEditEvent.Dismiss -> dismissAllowingStateLoss()
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

    private fun setUpForm(binding: DialogRecurringOccurrenceEditBinding) {
        binding.editTypeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val type = if (checkedId == R.id.editTypeIncomeButton) TransactionType.INCOME else TransactionType.EXPENSE
            viewModel.onTypeChange(type)
        }

        binding.editCategoryField.dropdownLayout.hint = getString(R.string.recurring_transaction_form_category_label)
        binding.editCategoryField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            latestCategories.getOrNull(position)?.let { viewModel.onCategoryChange(it.id) }
        }

        MoneyInputFormatter.attach(binding.editAmountInput) { formatted -> viewModel.onAmountChange(formatted) }

        binding.editAccountRow.setOnClickListener {
            AccountPickerDialog.show(
                context = requireContext(),
                accounts = latestAccounts,
                balanceFor = { account -> latestAccountBalances[account.id] ?: account.initialBalance },
                onSelect = { account -> viewModel.onAccountChange(account.id) }
            )
        }

        binding.editDescriptionInput.doAfterTextChanged { text -> viewModel.onDescriptionChange(text?.toString().orEmpty()) }

        binding.editPaymentMethodField.dropdownLayout.hint = getString(R.string.transaction_form_payment_method_label)
        val paymentMethodLabels = listOf(getString(R.string.transaction_form_payment_method_none)) +
            PaymentMethod.entries.map { getString(it.displayTextRes()) }
        binding.editPaymentMethodField.dropdownInput.setSimpleItems(paymentMethodLabels.toTypedArray())
        binding.editPaymentMethodField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            viewModel.onPaymentMethodChange(PaymentMethod.entries.getOrNull(position - 1))
        }

        binding.editDateField.dateFieldLabel.text = getString(R.string.recurring_queue_edit_date_label)
        binding.editDateRow.setOnClickListener {
            showDatePicker(R.string.recurring_queue_edit_date_label) { newDayMillis ->
                // Ne change QUE le jour : réutilise l'heure déjà affichée (celle de la règle par
                // défaut, voir `RecurringOccurrenceEditViewModel.init`, ou un choix précédent dans
                // ce même formulaire) plutôt que de la réinitialiser à minuit — même bug/même
                // correctif que `RecurringTransactionRepositoryImpl.acceptOccurrence`.
                val currentDate = viewModel.uiState.value.edit?.date ?: newDayMillis
                val currentTime = Instant.ofEpochMilli(currentDate).atZone(ZoneId.systemDefault()).toLocalTime()
                viewModel.onDateChange(combineDayAndTime(newDayMillis, currentTime.hour, currentTime.minute))
            }
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
        picker.show(parentFragmentManager, "recurring_occurrence_edit_date_picker")
    }

    private fun render(data: RenderState) {
        val binding = binding ?: return
        val edit = data.uiState.edit ?: return
        latestAccounts = data.accounts
        latestCategories = data.categories
        latestAccountBalances = data.accountBalances

        val expectedTypeButtonId = if (edit.type == TransactionType.INCOME) R.id.editTypeIncomeButton else R.id.editTypeExpenseButton
        if (binding.editTypeGroup.checkedButtonId != expectedTypeButtonId) {
            binding.editTypeGroup.check(expectedTypeButtonId)
        }

        binding.editCategoryField.dropdownInput.setSimpleItems(latestCategories.map { it.name }.toTypedArray())
        val categoryLabel = latestCategories.firstOrNull { it.id == edit.categoryId }?.name.orEmpty()
        if (binding.editCategoryField.dropdownInput.text?.toString() != categoryLabel) {
            binding.editCategoryField.dropdownInput.setText(categoryLabel, false)
        }
        binding.editCategoryField.dropdownLayout.error = edit.categoryError?.let { getString(it) }

        if (binding.editAmountInput.text?.toString() != edit.amountInput) {
            binding.editAmountInput.setText(edit.amountInput)
        }
        binding.editAmountLayout.error = edit.amountError?.let { getString(it) }

        val selectedAccount = latestAccounts.firstOrNull { it.id == edit.accountId }
        bindAccountField(binding, selectedAccount)
        binding.editAccountErrorText.text = edit.accountError?.let { getString(it) }
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

        val busy = data.uiState.isProcessing
        binding.loadingIndicator.visibility = if (busy) View.VISIBLE else View.GONE
        binding.cancelButton.isEnabled = !busy
        binding.confirmButton.isEnabled = !busy
    }

    /** Même logique que `RecurringTransactionFormFragment.bindAccountField` (voir sa doc) : petite
     * duplication assumée, chaque formulaire garde son propre binder. Solde COURANT (voir
     * [RecurringOccurrenceEditViewModel.accountBalances]), jamais [Account.initialBalance] seul. */
    private fun bindAccountField(binding: DialogRecurringOccurrenceEditBinding, account: Account?) {
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
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(AppDateFormats.NUMERIC_DATE)

    /** Regroupe les 4 flux observés pour éviter un `combine` imbriqué illisible (voir
     * [onViewCreated]) — même principe que `LoanFormFragment.LoanFormRenderState`. */
    private data class RenderState(
        val uiState: RecurringOccurrenceEditUiState,
        val accounts: List<Account>,
        val categories: List<Category>,
        val accountBalances: Map<Long, Long>
    )

    companion object {
        private const val TAG = "recurring_occurrence_edit"

        /** Point d'entrée UNIQUE pour ouvrir ce dialogue (voir
         * `RecurringTransactionsFragment.onOccurrenceRowClick`) — construit l'instance avec son
         * argument [RecurringOccurrenceEditViewModel.ARG_OCCURRENCE_ID] déjà posé, jamais de
         * `RecurringOccurrenceEditDialogFragment()` nu ailleurs dans l'app. */
        fun show(fragmentManager: FragmentManager, occurrenceId: Long) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            val fragment = RecurringOccurrenceEditDialogFragment().apply {
                arguments = bundleOf(RecurringOccurrenceEditViewModel.ARG_OCCURRENCE_ID to occurrenceId)
            }
            fragment.show(fragmentManager, TAG)
        }
    }
}
