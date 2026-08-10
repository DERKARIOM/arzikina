package com.arzikina.ne.presentation.utilities.loans

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.annotation.ColorRes
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
import com.arzikina.ne.databinding.FragmentLoanFormBinding
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.LoanType
import com.arzikina.ne.domain.model.Person
import com.arzikina.ne.domain.model.SupportedCurrency
import com.arzikina.ne.presentation.accounts.AccountIconMapper
import com.arzikina.ne.presentation.components.AccountPickerDialog
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.Money
import com.google.android.material.card.MaterialCardView
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
 * Ajout d'un prêt/emprunt, en 2 pages (voir maquette et la doc de [LoanFormViewModel]).
 * [findNavController]`.popBackStack()` sur enregistrement réussi : retour à l'écran principal
 * Prêts/Emprunts, qui observe déjà [com.arzikina.ne.domain.repository.LoanRepository] et se met
 * donc à jour automatiquement, sans rechargement explicite.
 */
@AndroidEntryPoint
class LoanFormFragment : Fragment(R.layout.fragment_loan_form) {

    private val viewModel: LoanFormViewModel by viewModels()
    private var binding: FragmentLoanFormBinding? = null

    /**
     * Dernières valeurs reçues de [LoanFormViewModel.accounts]/[LoanFormViewModel.persons]/
     * [LoanFormViewModel.accountBalances] (voir [render]) : ces trois `StateFlow` utilisent
     * `SharingStarted.WhileSubscribed` (voir leur doc), donc leur source ne se met à jour QUE
     * pendant qu'un collecteur est actif — les stocker ici, à jour à chaque [render], permet aux
     * gestionnaires de clic ([setUpPickers]) d'y accéder de façon synchrone sans les collecter une
     * seconde fois. Même principe que `TransactionFormFragment.latestAccounts`/`latestCategories`.
     */
    private var latestAccounts: List<Account> = emptyList()
    private var latestPersons: List<Person> = emptyList()
    private var latestAccountBalances: Map<Long, Long> = emptyMap()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentLoanFormBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        setUpTypeCards(viewBinding)
        setUpInputs(viewBinding)
        setUpPickers(viewBinding)
        setUpPrimaryAction(viewBinding)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        viewModel.formState,
                        viewModel.accounts,
                        viewModel.persons,
                        viewModel.accountBalances
                    ) { state, accounts, persons, balances ->
                        LoanFormRenderState(state, accounts, persons, balances)
                    }.collect { data -> render(data) }
                }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    private fun handleEvent(event: LoanFormEvent) {
        val binding = binding ?: return
        when (event) {
            LoanFormEvent.Saved -> {
                Snackbar.make(binding.root, R.string.loan_form_saved_message, Snackbar.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpToolbar(binding: FragmentLoanFormBinding) {
        binding.toolbar.setNavigationOnClickListener {
            if (viewModel.formState.value.step == 2) {
                viewModel.backToStep1()
            } else {
                findNavController().navigateUp()
            }
        }
    }

    private fun setUpTypeCards(binding: FragmentLoanFormBinding) {
        binding.typeLentCard.setOnClickListener { viewModel.onTypeChange(LoanType.LENT) }
        binding.typeBorrowedCard.setOnClickListener { viewModel.onTypeChange(LoanType.BORROWED) }
    }

    private fun setUpInputs(binding: FragmentLoanFormBinding) {
        binding.amountInput.doAfterTextChanged { text -> viewModel.onAmountChange(text?.toString().orEmpty()) }
        binding.descriptionInput.doAfterTextChanged { text -> viewModel.onDescriptionChange(text?.toString().orEmpty()) }
        binding.firstPaymentAmountInput.doAfterTextChanged { text ->
            viewModel.onFirstPaymentAmountChange(text?.toString().orEmpty())
        }
    }

    private fun setUpPickers(binding: FragmentLoanFormBinding) {
        binding.personRow.setOnClickListener {
            PersonPickerDialog.show(
                context = requireContext(),
                persons = latestPersons,
                onSelectExisting = { person -> viewModel.onPersonSelected(person) },
                onCreateNew = { name, phone -> viewModel.onNewPersonCreated(name, phone) }
            )
        }

        binding.accountRow.setOnClickListener {
            AccountPickerDialog.show(
                context = requireContext(),
                accounts = latestAccounts,
                balanceFor = { account -> latestAccountBalances[account.id] ?: account.initialBalance },
                onSelect = { account -> viewModel.onAccountSelected(account) }
            )
        }

        binding.startDateLayout.setOnClickListener { showDatePicker(R.string.loan_form_start_date_label) { viewModel.onStartDateChange(it) } }
        binding.startDateInput.setOnClickListener { showDatePicker(R.string.loan_form_start_date_label) { viewModel.onStartDateChange(it) } }
        binding.dueDateLayout.setOnClickListener { showDatePicker(R.string.loan_form_due_date_label) { viewModel.onDueDateChange(it) } }
        binding.dueDateInput.setOnClickListener { showDatePicker(R.string.loan_form_due_date_label) { viewModel.onDueDateChange(it) } }
        binding.firstPaymentDateLayout.setOnClickListener {
            showDatePicker(R.string.loan_form_first_payment_date_label) { viewModel.onFirstPaymentDateChange(it) }
        }
        binding.firstPaymentDateInput.setOnClickListener {
            showDatePicker(R.string.loan_form_first_payment_date_label) { viewModel.onFirstPaymentDateChange(it) }
        }
    }

    private fun setUpPrimaryAction(binding: FragmentLoanFormBinding) {
        binding.primaryActionButton.setOnClickListener {
            if (viewModel.formState.value.step == 1) viewModel.goToStep2() else viewModel.save()
        }
    }

    private fun showDatePicker(@StringRes titleRes: Int, onSelected: (Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(titleRes)
            .build()
        picker.addOnPositiveButtonClickListener { selectionUtcMillis ->
            // Le sélecteur retourne un instant UTC "début de journée" ; reconverti vers l'heure
            // locale pour rester cohérent avec System.currentTimeMillis() (voir TransactionFormFragment).
            val localDate = Instant.ofEpochMilli(selectionUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            val localMillis = localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            onSelected(localMillis)
        }
        picker.show(parentFragmentManager, "loan_form_date_picker")
    }

    private fun render(data: LoanFormRenderState) {
        val binding = binding ?: return
        val state = data.formState
        latestAccounts = data.accounts
        latestPersons = data.persons
        latestAccountBalances = data.accountBalances

        binding.toolbar.title = getString(if (state.step == 1) R.string.loan_form_title_step1 else R.string.loan_form_title_step2)
        binding.stepIndicator.text = getString(R.string.loan_form_step_indicator, state.step, TOTAL_STEPS)
        binding.page1.visibility = if (state.step == 1) View.VISIBLE else View.GONE
        binding.page2.visibility = if (state.step == 2) View.VISIBLE else View.GONE
        binding.primaryActionButton.text = getString(if (state.step == 1) R.string.loan_form_next_action else R.string.loan_form_save_action)
        binding.primaryActionButton.isEnabled = !state.isSaving

        if (state.step == 1) renderPage1(binding, state) else renderPage2(binding, state)
    }

    private fun renderPage1(binding: FragmentLoanFormBinding, state: LoanFormState) {
        renderTypeCard(binding.typeLentCard, isSelected = state.type == LoanType.LENT, selectedColorRes = R.color.loan_lent_color)
        renderTypeCard(binding.typeBorrowedCard, isSelected = state.type == LoanType.BORROWED, selectedColorRes = R.color.expense_red)

        binding.personFieldName.text = state.personName.ifBlank { getString(R.string.loan_form_person_placeholder) }
        binding.personErrorText.text = state.personError
        binding.personErrorText.visibility = if (state.personError != null) View.VISIBLE else View.GONE

        val selectedAccount = latestAccounts.firstOrNull { it.id == state.accountId }
        bindAccountField(binding, selectedAccount)
        binding.accountErrorText.text = state.accountError
        binding.accountErrorText.visibility = if (state.accountError != null) View.VISIBLE else View.GONE

        if (binding.amountInput.text?.toString() != state.amountInput) {
            binding.amountInput.setText(state.amountInput)
        }
        binding.amountErrorText.text = state.amountError
        binding.amountErrorText.visibility = if (state.amountError != null) View.VISIBLE else View.GONE
        binding.amountCurrencyBadge.text = selectedAccount?.let { currencySymbol(it.currencyCode) }.orEmpty()

        if (binding.startDateInput.text?.toString() != formatDate(state.startDateMillis)) {
            binding.startDateInput.setText(formatDate(state.startDateMillis))
        }
        if (binding.dueDateInput.text?.toString() != formatDate(state.dueDateMillis)) {
            binding.dueDateInput.setText(formatDate(state.dueDateMillis))
        }
        binding.dueDateErrorText.text = state.dueDateError
        binding.dueDateErrorText.visibility = if (state.dueDateError != null) View.VISIBLE else View.GONE
    }

    private fun renderPage2(binding: FragmentLoanFormBinding, state: LoanFormState) {
        binding.summaryPersonAvatar.text = personAvatarInitial(state.personName)
        binding.summaryPersonAvatar.backgroundTintList = ColorStateList.valueOf(personAvatarColorArgb(state.personName).toInt())

        binding.summaryTitle.text = state.description.ifBlank { getString(defaultLoanTitleRes(state.type)) }

        val personPrefixRes = if (state.type == LoanType.LENT) R.string.loans_person_prefix_lent else R.string.loans_person_prefix_borrowed
        binding.summaryPersonLine.text = getString(personPrefixRes, state.personName)
        binding.summaryPersonLine.setTextColor(
            ContextCompat.getColor(requireContext(), if (state.type == LoanType.LENT) R.color.loan_lent_color else R.color.expense_red)
        )

        val currencyCode = latestAccounts.firstOrNull { it.id == state.accountId }?.currencyCode
            ?: Constants.DEFAULT_CURRENCY_CODE
        val amountMinor = Money.parseToMinorUnits(state.amountInput) ?: 0L
        binding.summaryAmountValue.text = Money.format(CurrencyAmount(currencyCode, amountMinor))
        binding.summaryStartDateValue.text = formatDate(state.startDateMillis)
        binding.summaryDueDateValue.text = formatDate(state.dueDateMillis)

        if (binding.descriptionInput.text?.toString() != state.description) {
            binding.descriptionInput.setText(state.description)
        }

        if (binding.firstPaymentAmountInput.text?.toString() != state.firstPaymentAmountInput) {
            binding.firstPaymentAmountInput.setText(state.firstPaymentAmountInput)
        }
        binding.firstPaymentAmountLayout.error = state.firstPaymentAmountError

        if (binding.firstPaymentDateInput.text?.toString() != formatDate(state.firstPaymentDateMillis)) {
            binding.firstPaymentDateInput.setText(formatDate(state.firstPaymentDateMillis))
        }
    }

    private fun renderTypeCard(card: MaterialCardView, isSelected: Boolean, @ColorRes selectedColorRes: Int) {
        card.isChecked = isSelected
        card.strokeColor = ContextCompat.getColor(
            requireContext(),
            if (isSelected) selectedColorRes else R.color.arzikina_outline
        )
    }

    /** Même logique que `TransactionFormFragment.bindAccountField` (voir sa doc) : petite
     * duplication assumée, chaque formulaire garde son propre binder plutôt que de partager une
     * fonction entre deux Fragments indépendants. */
    private fun bindAccountField(binding: FragmentLoanFormBinding, account: Account?) {
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

    /** Regroupe les 4 flux observés pour éviter un `combine` imbriqué illisible (voir
     * [onViewCreated]) — même principe que `TransactionFormFragment.FormRenderState`. */
    private data class LoanFormRenderState(
        val formState: LoanFormState,
        val accounts: List<Account>,
        val persons: List<Person>,
        val accountBalances: Map<Long, Long>
    )

    private companion object {
        const val TOTAL_STEPS = 2
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
