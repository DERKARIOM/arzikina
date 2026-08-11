package com.arzikina.ne.presentation.utilities.loans

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentLoanPaymentFormBinding
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.LoanType
import com.arzikina.ne.domain.model.SupportedCurrency
import com.arzikina.ne.presentation.accounts.AccountIconMapper
import com.arzikina.ne.presentation.components.AccountPickerDialog
import com.arzikina.ne.util.Money
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Enregistrement d'un remboursement, atteint depuis le bouton "Enregistrer un remboursement" de
 * [LoanDetailFragment] — voir la doc de [LoanPaymentFormViewModel]. Retour à "Détail du prêt" sur
 * enregistrement réussi : cet écran observe déjà [com.arzikina.ne.domain.repository.LoanRepository]
 * et se met donc à jour automatiquement (montant remboursé, statut, liste des versements), sans
 * rechargement explicite — même principe que [LoanFormFragment].
 */
@AndroidEntryPoint
class LoanPaymentFormFragment : Fragment(R.layout.fragment_loan_payment_form) {

    private val viewModel: LoanPaymentFormViewModel by viewModels()
    private var binding: FragmentLoanPaymentFormBinding? = null

    /** Voir `LoanFormFragment.latestAccounts`/`latestAccountBalances` pour le même raisonnement
     * (StateFlow `WhileSubscribed`, accès synchrone nécessaire depuis [setUpPickers]). */
    private var latestAccounts: List<Account> = emptyList()
    private var latestAccountBalances: Map<Long, Long> = emptyMap()

    /** Voir `LoanDetailFragment.hasNavigatedAwayOnError` pour le même raisonnement (évite de
     * déclencher [findNavController.navigateUp] plusieurs fois si [LoanPaymentFormState.notFound]
     * reste `true` sur plusieurs émissions). */
    private var hasNavigatedAwayOnNotFound = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentLoanPaymentFormBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        setUpInputs(viewBinding)
        setUpPickers(viewBinding)
        viewBinding.primaryActionButton.setOnClickListener { viewModel.save() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        viewModel.formState,
                        viewModel.accounts,
                        viewModel.accountBalances
                    ) { state, accounts, balances -> RenderState(state, accounts, balances) }
                        .collect { data -> render(data) }
                }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun handleEvent(event: LoanPaymentFormEvent) {
        val binding = binding ?: return
        when (event) {
            LoanPaymentFormEvent.Saved -> {
                Snackbar.make(binding.root, R.string.loan_payment_form_saved_message, Snackbar.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }
    }

    private fun setUpInputs(binding: FragmentLoanPaymentFormBinding) {
        binding.amountInput.doAfterTextChanged { text -> viewModel.onAmountChange(text?.toString().orEmpty()) }
        binding.noteInput.doAfterTextChanged { text -> viewModel.onNoteChange(text?.toString().orEmpty()) }
    }

    private fun setUpPickers(binding: FragmentLoanPaymentFormBinding) {
        binding.accountRow.setOnClickListener {
            AccountPickerDialog.show(
                context = requireContext(),
                accounts = latestAccounts,
                balanceFor = { account -> latestAccountBalances[account.id] ?: account.initialBalance },
                onSelect = { account -> viewModel.onAccountSelected(account) }
            )
        }
        binding.dateLayout.setOnClickListener { showDatePicker { viewModel.onDateChange(it) } }
        binding.dateInput.setOnClickListener { showDatePicker { viewModel.onDateChange(it) } }
    }

    private fun showDatePicker(onSelected: (Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.loan_payment_form_date_label)
            .build()
        picker.addOnPositiveButtonClickListener { selectionUtcMillis ->
            val localDate = Instant.ofEpochMilli(selectionUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            onSelected(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
        picker.show(parentFragmentManager, "loan_payment_form_date_picker")
    }

    private fun render(data: RenderState) {
        val binding = binding ?: return
        val state = data.formState
        latestAccounts = data.accounts
        latestAccountBalances = data.accountBalances

        if (state.notFound) {
            if (!hasNavigatedAwayOnNotFound) {
                hasNavigatedAwayOnNotFound = true
                Snackbar.make(binding.root, R.string.loan_detail_not_found_message, Snackbar.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            return
        }

        binding.primaryActionButton.isEnabled = state.isLoaded && !state.isSaving
        if (!state.isLoaded) return

        binding.summaryPersonAvatar.text = personAvatarInitial(state.personName)
        binding.summaryPersonAvatar.backgroundTintList = ColorStateList.valueOf(personAvatarColorArgb(state.personName).toInt())
        binding.summaryTitle.text = state.loanTitle.ifBlank { getString(defaultLoanTitleRes(state.loanType)) }

        val personPrefixRes = if (state.loanType == LoanType.LENT) R.string.loans_person_prefix_lent else R.string.loans_person_prefix_borrowed
        binding.summaryPersonLine.text = getString(personPrefixRes, state.personName)
        binding.summaryPersonLine.setTextColor(
            ContextCompat.getColor(requireContext(), if (state.loanType == LoanType.LENT) R.color.loan_lent_color else R.color.expense_red)
        )
        binding.summaryRemainingValue.text = Money.format(CurrencyAmount(state.loanCurrencyCode, state.loanRemainingAmount))

        val selectedAccount = latestAccounts.firstOrNull { it.id == state.accountId }
        bindAccountField(binding, selectedAccount)
        binding.accountErrorText.text = state.accountError
        binding.accountErrorText.visibility = if (state.accountError != null) View.VISIBLE else View.GONE
        // Avertissement non bloquant (voir la doc de LoanPaymentFormState.amountInput) : le montant
        // saisi est toujours interprété dans la devise DU PRÊT, jamais convertie.
        val hasCurrencyMismatch = selectedAccount != null && selectedAccount.currencyCode != state.loanCurrencyCode
        binding.currencyMismatchWarningText.visibility = if (hasCurrencyMismatch) View.VISIBLE else View.GONE

        if (binding.amountInput.text?.toString() != state.amountInput) {
            binding.amountInput.setText(state.amountInput)
        }
        binding.amountErrorText.text = state.amountError
        binding.amountErrorText.visibility = if (state.amountError != null) View.VISIBLE else View.GONE
        binding.amountCurrencyBadge.text = currencySymbol(state.loanCurrencyCode)

        if (binding.dateInput.text?.toString() != formatDate(state.dateMillis)) {
            binding.dateInput.setText(formatDate(state.dateMillis))
        }
        if (binding.noteInput.text?.toString() != state.note) {
            binding.noteInput.setText(state.note)
        }
    }

    /** Même logique que `LoanFormFragment.bindAccountField` (voir sa doc, petite duplication
     * assumée entre formulaires indépendants). */
    private fun bindAccountField(binding: FragmentLoanPaymentFormBinding, account: Account?) {
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

    private fun currencySymbol(currencyCode: String): String =
        SupportedCurrency.entries.firstOrNull { it.code == currencyCode }?.symbol ?: currencyCode

    private fun formatDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMATTER)

    private data class RenderState(
        val formState: LoanPaymentFormState,
        val accounts: List<Account>,
        val accountBalances: Map<Long, Long>
    )

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
