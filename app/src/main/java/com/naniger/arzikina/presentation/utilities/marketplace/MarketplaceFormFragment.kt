package com.naniger.arzikina.presentation.utilities.marketplace

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
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentMarketplaceFormBinding
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.Category
import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.domain.model.TransactionType
import com.naniger.arzikina.presentation.accounts.AccountIconMapper
import com.naniger.arzikina.presentation.components.AccountPickerDialog
import com.naniger.arzikina.presentation.components.ConfirmDialogs
import com.naniger.arzikina.presentation.components.TimePickerHelper
import com.naniger.arzikina.presentation.components.displayName
import com.naniger.arzikina.util.Money
import com.naniger.arzikina.util.MoneyInputFormatter
import com.naniger.arzikina.util.TriggerTimeFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Formulaire de création/édition d'un modèle (voir [MarketplaceFormViewModel], cahier des charges
 * "Marketplace personnelle", section 3). Même structure que
 * [com.naniger.arzikina.presentation.utilities.recurring.RecurringTransactionFormFragment], sans les
 * champs propres à une règle programmée (date/fréquence) — seule l'heure par défaut OPTIONNELLE est
 * reprise (voir [setUpDefaultTimeField], extension "Heure par défaut" du cahier des charges).
 *
 * [findNavController]`.popBackStack()` sur enregistrement/suppression réussi : retour à
 * [MarketplaceFragment], qui observe déjà [MarketplaceViewModel] et se met donc à jour
 * automatiquement, sans rechargement explicite.
 */
@AndroidEntryPoint
class MarketplaceFormFragment : Fragment(R.layout.fragment_marketplace_form) {

    private val viewModel: MarketplaceFormViewModel by viewModels()
    private var binding: FragmentMarketplaceFormBinding? = null

    /** Dernières valeurs reçues de [MarketplaceFormViewModel.accounts]/[MarketplaceFormViewModel.categories]/
     * [MarketplaceFormViewModel.accountBalances] (voir [render]) — même principe que
     * `RecurringTransactionFormFragment.latestAccounts`. */
    private var latestAccounts: List<Account> = emptyList()
    private var latestCategories: List<Category> = emptyList()
    private var latestAccountBalances: Map<Long, Long> = emptyMap()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentMarketplaceFormBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        setUpTypeToggle(viewBinding)
        setUpCategoryDropdown(viewBinding)
        setUpAccountPicker(viewBinding)
        setUpInputs(viewBinding)
        setUpDefaultTimeField(viewBinding)
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

    private fun setUpToolbar(binding: FragmentMarketplaceFormBinding) {
        binding.toolbar.title = getString(
            if (viewModel.isEditMode) R.string.marketplace_form_title_edit else R.string.marketplace_form_title_add
        )
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setUpTypeToggle(binding: FragmentMarketplaceFormBinding) {
        binding.typeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val type = if (checkedId == R.id.typeIncomeButton) TransactionType.INCOME else TransactionType.EXPENSE
            viewModel.onTypeChange(type)
        }
    }

    private fun setUpCategoryDropdown(binding: FragmentMarketplaceFormBinding) {
        binding.categoryField.dropdownLayout.hint = getString(R.string.recurring_transaction_form_category_label)
        binding.categoryField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            latestCategories.getOrNull(position)?.let { viewModel.onCategoryChange(it.id) }
        }
    }

    private fun setUpAccountPicker(binding: FragmentMarketplaceFormBinding) {
        binding.accountRow.setOnClickListener {
            AccountPickerDialog.show(
                context = requireContext(),
                accounts = latestAccounts,
                balanceFor = { account -> latestAccountBalances[account.id] ?: account.initialBalance },
                onSelect = { account -> viewModel.onAccountChange(account.id) }
            )
        }
    }

    private fun setUpInputs(binding: FragmentMarketplaceFormBinding) {
        binding.nameInput.doAfterTextChanged { text -> viewModel.onNameChange(text?.toString().orEmpty()) }
        MoneyInputFormatter.attach(binding.amountInput) { formatted -> viewModel.onAmountChange(formatted) }
        binding.descriptionInput.doAfterTextChanged { text -> viewModel.onDescriptionChange(text?.toString().orEmpty()) }
    }

    /**
     * Champ "Heure par défaut" (extension du cahier des charges "Marketplace personnelle") — même
     * pattern switch + card conditionnelle que `RecurringTransactionFormFragment.setUpEndDateSwitch`.
     * `dateFieldIcon`/`dateFieldLabel` figés une seule fois ici après inflate (jamais dans [render],
     * voir la doc de `item_date_field.xml`) ; seule `dateFieldValue` est mise à jour dans [render].
     */
    private fun setUpDefaultTimeField(binding: FragmentMarketplaceFormBinding) {
        binding.defaultTimeField.dateFieldIcon.setImageResource(R.drawable.ic_time_24)
        binding.defaultTimeField.dateFieldLabel.text = getString(R.string.marketplace_form_default_time_label)
        binding.defaultTimeSwitch.setOnCheckedChangeListener { _, isChecked -> viewModel.onDefaultTimeToggle(isChecked) }
        binding.defaultTimeRow.setOnClickListener { showDefaultTimePicker() }
    }

    private fun showDefaultTimePicker() {
        val state = viewModel.formState.value
        TimePickerHelper.show(
            context = requireContext(),
            fragmentManager = parentFragmentManager,
            initialHour = state.defaultHour,
            initialMinute = state.defaultMinute,
            titleText = getString(R.string.marketplace_form_default_time_label),
            tag = "marketplace_default_time_picker",
            onTimeSelected = { hour, minute -> viewModel.onDefaultTimeChange(hour, minute) }
        )
    }

    private fun setUpPrimaryActions(binding: FragmentMarketplaceFormBinding) {
        binding.saveButton.setOnClickListener { viewModel.save() }
        binding.deleteButton.setOnClickListener { confirmDelete() }
    }

    private fun confirmDelete() {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.marketplace_delete_confirm_title),
            message = getString(R.string.marketplace_delete_confirm_message, viewModel.formState.value.name),
            onConfirm = { viewModel.delete() }
        )
    }

    private fun handleEvent(event: MarketplaceFormEvent) {
        when (event) {
            MarketplaceFormEvent.Saved, MarketplaceFormEvent.Deleted -> findNavController().popBackStack()
        }
    }

    private fun render(data: FormRenderState) {
        val binding = binding ?: return
        val state = data.formState
        latestAccounts = data.accounts
        latestCategories = data.categories
        latestAccountBalances = data.accountBalances

        if (binding.nameInput.text?.toString() != state.name) {
            binding.nameInput.setText(state.name)
        }
        binding.nameLayout.error = state.nameError?.let { getString(it) }

        val expectedTypeButtonId = if (state.type == TransactionType.INCOME) R.id.typeIncomeButton else R.id.typeExpenseButton
        if (binding.typeGroup.checkedButtonId != expectedTypeButtonId) {
            binding.typeGroup.check(expectedTypeButtonId)
        }

        binding.categoryField.dropdownInput.setSimpleItems(data.categories.map { it.displayName(requireContext()) }.toTypedArray())
        val categoryLabel = data.categories.firstOrNull { it.id == state.categoryId }?.displayName(requireContext()).orEmpty()
        if (binding.categoryField.dropdownInput.text?.toString() != categoryLabel) {
            binding.categoryField.dropdownInput.setText(categoryLabel, false)
        }
        binding.categoryField.dropdownLayout.error = state.categoryError?.let { getString(it) }

        if (binding.amountInput.text?.toString() != state.amountInput) {
            binding.amountInput.setText(state.amountInput)
        }
        binding.amountLayout.error = state.amountError?.let { getString(it) }

        val selectedAccount = data.accounts.firstOrNull { it.id == state.accountId }
        bindAccountField(binding, selectedAccount)
        binding.accountErrorText.text = state.accountError?.let { getString(it) }
        binding.accountErrorText.visibility = if (state.accountError != null) View.VISIBLE else View.GONE

        if (binding.descriptionInput.text?.toString() != state.description) {
            binding.descriptionInput.setText(state.description)
        }

        if (binding.defaultTimeSwitch.isChecked != state.hasDefaultTime) {
            binding.defaultTimeSwitch.isChecked = state.hasDefaultTime
        }
        binding.defaultTimeCard.visibility = if (state.hasDefaultTime) View.VISIBLE else View.GONE
        binding.defaultTimeField.dateFieldValue.text = TriggerTimeFormatter.format(
            requireContext(),
            state.defaultHour,
            state.defaultMinute
        )

        binding.deleteButton.visibility = if (viewModel.isEditMode) View.VISIBLE else View.GONE
    }

    /** Même logique que `RecurringTransactionFormFragment.bindAccountField`/`LoanFormFragment.bindAccountField`
     * (voir leur doc) : petite duplication assumée, chaque formulaire garde son propre binder plutôt
     * que de partager une fonction entre Fragments indépendants. */
    private fun bindAccountField(binding: FragmentMarketplaceFormBinding, account: Account?) {
        val fieldBinding = binding.accountField
        if (account != null) {
            fieldBinding.accountFieldIcon.setImageResource(AccountIconMapper.iconFor(account.icon))
            fieldBinding.accountFieldIcon.backgroundTintList = ColorStateList.valueOf(account.colorArgb.toInt())
            fieldBinding.accountFieldName.text = account.displayName(requireContext())
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

    /** Regroupe les 4 flux observés pour éviter un `combine` imbriqué illisible (voir
     * [onViewCreated]) — même principe que `RecurringTransactionFormFragment.FormRenderState`. */
    private data class FormRenderState(
        val formState: MarketplaceFormState,
        val accounts: List<Account>,
        val categories: List<Category>,
        val accountBalances: Map<Long, Long>
    )
}
