package com.arzikina.ne.presentation.utilities.financialplan

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
import com.arzikina.ne.databinding.FragmentFinancialPlanItemConvertBinding
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.presentation.accounts.AccountIconMapper
import com.arzikina.ne.presentation.categories.CategoryIconMapper
import com.arzikina.ne.presentation.components.AccountPickerDialog
import com.arzikina.ne.presentation.components.CategoryPickerDialog
import com.arzikina.ne.util.Constants
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
 * "Enregistrer comme transaction" (voir cahier des charges "Planification financière", section
 * 12) — atteint depuis le bouton dédié de [FinancialPlanItemFormFragment] (visible uniquement pour
 * une dépense prévue pas encore convertie, voir [FinancialPlanItemFormState.isAlreadyConverted]).
 * Retour à "Détail de la planification" sur enregistrement réussi (voir [handleEvent]) — jamais un
 * simple `navigateUp()`, qui laisserait le formulaire d'édition (désormais obsolète : la dépense
 * vient de passer à [com.arzikina.ne.domain.model.PlanItemStatus.DONE]) au milieu de la pile.
 */
@AndroidEntryPoint
class FinancialPlanItemConvertFragment : Fragment(R.layout.fragment_financial_plan_item_convert) {

    private val viewModel: FinancialPlanItemConvertViewModel by viewModels()
    private var binding: FragmentFinancialPlanItemConvertBinding? = null

    /** Voir `LoanPaymentFormFragment.latestAccounts`/`latestAccountBalances` pour le même
     * raisonnement (StateFlow `WhileSubscribed`, accès synchrone nécessaire depuis [setUpPickers]). */
    private var latestAccounts: List<Account> = emptyList()
    private var latestAccountBalances: Map<Long, Long> = emptyMap()
    private var latestCategories: List<Category> = emptyList()

    /** Voir `LoanPaymentFormFragment.hasNavigatedAwayOnNotFound` pour le même raisonnement. */
    private var hasNavigatedAwayOnNotFound = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentFinancialPlanItemConvertBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        setUpInputs(viewBinding)
        setUpPickers(viewBinding)
        viewBinding.saveButton.setOnClickListener { viewModel.save() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        viewModel.formState,
                        viewModel.accounts,
                        viewModel.accountBalances,
                        viewModel.categories
                    ) { state, accounts, balances, categories -> RenderState(state, accounts, balances, categories) }
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

    private fun setUpInputs(binding: FragmentFinancialPlanItemConvertBinding) {
        binding.amountInput.doAfterTextChanged { text -> viewModel.onActualAmountChange(text?.toString().orEmpty()) }
        binding.descriptionInput.doAfterTextChanged { text -> viewModel.onDescriptionChange(text?.toString().orEmpty()) }
    }

    private fun setUpPickers(binding: FragmentFinancialPlanItemConvertBinding) {
        binding.accountField.accountFieldLabel.text = getString(R.string.transaction_form_account_label)
        binding.accountRow.setOnClickListener {
            AccountPickerDialog.show(
                context = requireContext(),
                accounts = latestAccounts,
                balanceFor = { account -> latestAccountBalances[account.id] ?: account.initialBalance },
                onSelect = { account -> viewModel.onAccountChange(account) }
            )
        }

        binding.categoryField.categoryFieldLabel.text = getString(R.string.financial_plan_item_form_category_label)
        binding.categoryRow.setOnClickListener {
            CategoryPickerDialog.show(
                context = requireContext(),
                categories = latestCategories,
                onSelect = { category -> viewModel.onCategoryChange(category) }
            )
        }

        binding.dateField.dateFieldLabel.text = getString(R.string.financial_plan_item_convert_date_label)
        binding.dateRow.setOnClickListener { showDatePicker { viewModel.onDateChange(it) } }
    }

    private fun showDatePicker(onSelected: (Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.financial_plan_item_convert_date_label)
            .build()
        picker.addOnPositiveButtonClickListener { selectionUtcMillis ->
            val localDate = Instant.ofEpochMilli(selectionUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            onSelected(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
        picker.show(parentFragmentManager, "financial_plan_item_convert_date_picker")
    }

    private fun handleEvent(event: FinancialPlanItemConvertEvent) {
        when (event) {
            FinancialPlanItemConvertEvent.Saved ->
                findNavController().popBackStack(R.id.financialPlanDetailFragment, false)
        }
    }

    private fun render(data: RenderState) {
        val binding = binding ?: return
        val state = data.formState
        latestAccounts = data.accounts
        latestAccountBalances = data.accountBalances
        latestCategories = data.categories

        if (state.notFound) {
            if (!hasNavigatedAwayOnNotFound) {
                hasNavigatedAwayOnNotFound = true
                Snackbar.make(binding.root, R.string.financial_plan_item_convert_not_found_message, Snackbar.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            return
        }

        binding.saveButton.isEnabled = state.isLoaded && !state.isSaving
        if (!state.isLoaded) return

        val plannedLabel = Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, state.plannedAmount))
        binding.itemContextLabel.text = getString(R.string.financial_plan_item_convert_context, state.itemName, plannedLabel)

        val selectedAccount = latestAccounts.firstOrNull { it.id == state.accountId }
        bindAccountField(binding, selectedAccount)
        binding.accountErrorText.text = state.accountError
        binding.accountErrorText.visibility = if (state.accountError != null) View.VISIBLE else View.GONE

        bindCategoryField(binding, latestCategories.firstOrNull { it.id == state.categoryId })
        binding.categoryErrorText.text = state.categoryError
        binding.categoryErrorText.visibility = if (state.categoryError != null) View.VISIBLE else View.GONE

        if (binding.amountInput.text?.toString() != state.actualAmountInput) {
            binding.amountInput.setText(state.actualAmountInput)
        }
        binding.amountLayout.error = state.amountError

        binding.dateField.dateFieldValue.text = formatDate(state.dateMillis)

        if (binding.descriptionInput.text?.toString() != state.descriptionInput) {
            binding.descriptionInput.setText(state.descriptionInput)
        }
    }

    /** Voir `LoanPaymentFormFragment.bindAccountField` pour le même raisonnement (petite
     * duplication assumée entre formulaires indépendants). */
    private fun bindAccountField(binding: FragmentFinancialPlanItemConvertBinding, account: Account?) {
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

    /** Voir `FinancialPlanItemFormFragment.bindCategoryField` pour le même raisonnement. */
    private fun bindCategoryField(binding: FragmentFinancialPlanItemConvertBinding, category: Category?) {
        val fieldBinding = binding.categoryField
        if (category != null) {
            fieldBinding.categoryFieldIcon.setImageResource(CategoryIconMapper.iconFor(category.icon))
            fieldBinding.categoryFieldIcon.backgroundTintList = ColorStateList.valueOf(category.colorArgb.toInt())
            fieldBinding.categoryFieldName.text = category.name
        } else {
            fieldBinding.categoryFieldIcon.setImageResource(R.drawable.ic_category_other_24)
            fieldBinding.categoryFieldIcon.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.arzikina_outline))
            fieldBinding.categoryFieldName.text = getString(R.string.financial_plan_item_form_category_none)
        }
    }

    private fun formatDate(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMATTER)

    private data class RenderState(
        val formState: FinancialPlanItemConvertState,
        val accounts: List<Account>,
        val accountBalances: Map<Long, Long>,
        val categories: List<Category>
    )

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }
}
