package com.arzikina.ne.presentation.transactions

import android.content.res.ColorStateList
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentTransactionFormBinding
import com.arzikina.ne.databinding.ItemAccountFieldBinding
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.FeeType
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.SupportedCurrency
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.presentation.accounts.AccountIconMapper
import com.arzikina.ne.presentation.components.AccountPickerDialog
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.presentation.components.NavAnimations
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.Money
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formulaire d'ajout/édition d'une transaction. Refonte visuelle (voir
 * maquette "PERSONNALISATION – AJOUT DE TRANSACTION") : mêmes champs et
 * comportements qu'avant (voir [TransactionFormViewModel], inchangé sur le
 * fond), présentés en cartes/lignes cliquables plutôt qu'en menus déroulants
 * classiques ; le type (Dépense/Revenu/Transfert) est un menu déroulant fermé
 * (voir [setUpTypeDropdown]). Le transfert masque la carte Catégorie (sans
 * objet) et affiche une seconde ligne "Compte destination" (voir
 * [renderDestinationAccountRow]) — pas de dictée vocale ni de scan de reçu
 * ici, chantiers séparés.
 *
 * Les champs date/heure restent non focusables (voir `fragment_transaction_form.xml`) :
 * la saisie se fait uniquement via [MaterialDatePicker]/[MaterialTimePicker],
 * enchaînés l'un après l'autre depuis une seule ligne désormais.
 */
@AndroidEntryPoint
class TransactionFormFragment : Fragment(R.layout.fragment_transaction_form) {

    private val viewModel: TransactionFormViewModel by viewModels()
    private var binding: FragmentTransactionFormBinding? = null

    private var latestAccounts: List<Account> = emptyList()
    private var latestCategories: List<Category> = emptyList()
    private var latestSelectedCategoryId: Long = 0L
    private var isCategoryGridExpanded = false
    private var isMoreDetailsExpanded = false

    private val categoryAdapter = CategoryQuickPickAdapter(
        onSelect = { category -> if (isLoanLinked()) Unit else viewModel.onCategoryChange(category.id) },
        onAddNew = { navigateToNewCategory() }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentTransactionFormBinding.bind(view)
        binding = viewBinding

        // Seul le libellé diffère de item_account_field.xml (qui vaut par défaut
        // "Compte", correct pour accountField) : pas besoin de re-fixer ceci à
        // chaque render(), une seule fois ici suffit.
        viewBinding.destinationAccountField.accountFieldLabel.text =
            getString(R.string.transaction_form_destination_account_label)
        viewBinding.feeAccountField.accountFieldLabel.text =
            getString(R.string.transaction_form_fee_account_label)

        setUpToolbar(viewBinding)
        setUpTypeDropdown(viewBinding)
        setUpQuickAmounts(viewBinding)
        setUpCategoryGrid(viewBinding)
        setUpAccountRow(viewBinding)
        setUpDestinationAccountRow(viewBinding)
        setUpPaymentMethodDropdown(viewBinding)
        setUpDateTime(viewBinding)
        setUpInputs(viewBinding)
        setUpMoreDetailsToggle(viewBinding)
        setUpFeeSwitch(viewBinding)
        setUpFeeTypeDropdown(viewBinding)
        setUpFeeAccountRow(viewBinding)
        setUpFeeInputs(viewBinding)

        viewBinding.saveButton.setOnClickListener { viewModel.save() }
        viewBinding.deleteButton.setOnClickListener { confirmDelete() }
        viewBinding.loanLinkedBanner.setOnClickListener {
            viewModel.formState.value.linkedLoanId?.let { loanId -> navigateToLoanDetail(loanId) }
        }

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

    /**
     * Le bouton "Supprimer" ne s'affiche qu'en modification. L'action rapide
     * "✓" de la Toolbar (voir `transaction_form_menu.xml`) appelle le même
     * [TransactionFormViewModel.save] que le bouton "Enregistrer la
     * transaction" en bas de l'écran — deux points d'accès, une seule action.
     */
    private fun setUpToolbar(binding: FragmentTransactionFormBinding) {
        binding.toolbar.title = getString(
            if (viewModel.isEditMode) R.string.transaction_form_title_edit else R.string.transaction_form_title_add
        )
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.inflateMenu(R.menu.transaction_form_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_save_transaction) {
                viewModel.save()
                true
            } else {
                false
            }
        }
        binding.deleteButton.visibility = if (viewModel.isEditMode) View.VISIBLE else View.GONE
    }

    /**
     * Liste FERMÉE (même pattern que [setUpPaymentMethodDropdown]) : les
     * libellés viennent de [TYPE_OPTIONS], dont l'ordre pilote celui affiché
     * dans le menu (Dépense, Revenu, Transfert).
     */
    private fun setUpTypeDropdown(binding: FragmentTransactionFormBinding) {
        binding.typeField.dropdownLayout.hint = getString(R.string.transaction_form_type_label)
        val labels = TYPE_OPTIONS.map { (_, labelRes) -> getString(labelRes) }
        binding.typeField.dropdownInput.setSimpleItems(labels.toTypedArray())
        binding.typeField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            viewModel.onTypeChange(TYPE_OPTIONS[position].first)
        }
    }

    /** Raccourcis "+1 000"/"+5 000"/"+10 000" : voir [TransactionFormViewModel.onQuickAmountAdd]. */
    private fun setUpQuickAmounts(binding: FragmentTransactionFormBinding) {
        val formatter = NumberFormat.getIntegerInstance(Locale.FRENCH)
        val buttons = listOf(binding.quickAmount1Button, binding.quickAmount2Button, binding.quickAmount3Button)
        buttons.forEachIndexed { index, button ->
            val amount = QUICK_AMOUNTS[index]
            button.text = "+${formatter.format(amount)}"
            button.setOnClickListener { if (!isLoanLinked()) viewModel.onQuickAmountAdd(amount) }
        }
    }

    private fun setUpCategoryGrid(binding: FragmentTransactionFormBinding) {
        binding.categoryGrid.adapter = categoryAdapter
        binding.categorySeeAllToggle.setOnClickListener {
            isCategoryGridExpanded = !isCategoryGridExpanded
            renderCategoryGrid()
        }
    }

    /**
     * Ouvre [AccountPickerDialog] au lieu d'un menu déroulant classique. Pour
     * un transfert, exclut le compte destination déjà choisi (voir
     * [setUpDestinationAccountRow] pour l'exclusion symétrique) : impossible
     * de choisir deux fois le même compte plutôt qu'une erreur après coup.
     */
    private fun setUpAccountRow(binding: FragmentTransactionFormBinding) {
        binding.accountRow.setOnClickListener {
            if (isLoanLinked()) return@setOnClickListener
            val state = viewModel.formState.value
            val excludedId = state.transferAccountId.takeIf { state.type == TransactionType.TRANSFER }
            AccountPickerDialog.show(
                context = requireContext(),
                accounts = latestAccounts.filter { it.id != excludedId },
                balanceFor = { account -> viewModel.accountBalances.value[account.id] ?: account.initialBalance },
                onSelect = { account -> viewModel.onAccountChange(account.id) }
            )
        }
    }

    /** Uniquement pertinent pour un transfert (voir [renderDestinationAccountRow]) : exclut le compte source. */
    private fun setUpDestinationAccountRow(binding: FragmentTransactionFormBinding) {
        binding.destinationAccountRow.setOnClickListener {
            if (isLoanLinked()) return@setOnClickListener
            val excludedId = viewModel.formState.value.accountId
            AccountPickerDialog.show(
                context = requireContext(),
                accounts = latestAccounts.filter { it.id != excludedId },
                balanceFor = { account -> viewModel.accountBalances.value[account.id] ?: account.initialBalance },
                onSelect = { account -> viewModel.onTransferAccountChange(account.id) }
            )
        }
    }

    /**
     * Liste FERMÉE et fixe (contrairement à setUpAccountRow, qui dépend de
     * données chargées) : un premier item "Non précisé" (index 0) remet le
     * champ à `null`, les suivants correspondent 1-à-1 à [PaymentMethod.entries].
     */
    private fun setUpPaymentMethodDropdown(binding: FragmentTransactionFormBinding) {
        binding.paymentMethodField.dropdownLayout.hint = getString(R.string.transaction_form_payment_method_label)
        val labels = listOf(getString(R.string.transaction_form_payment_method_none)) +
            PaymentMethod.entries.map { getString(it.displayTextRes()) }
        binding.paymentMethodField.dropdownInput.setSimpleItems(labels.toTypedArray())
        binding.paymentMethodField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            val method = PaymentMethod.entries.getOrNull(position - 1)
            viewModel.onPaymentMethodChange(method)
        }
    }

    /** Une seule ligne cliquable (voir maquette) : la date puis l'heure s'enchaînent au lieu de deux champs séparés. */
    private fun setUpDateTime(binding: FragmentTransactionFormBinding) {
        binding.dateTimeField.dateFieldLabel.text = getString(R.string.transaction_form_date_time_label)
        binding.dateTimeRow.setOnClickListener { if (!isLoanLinked()) showDatePicker() }
    }

    private fun setUpInputs(binding: FragmentTransactionFormBinding) {
        binding.amountInput.doAfterTextChanged { text ->
            viewModel.onAmountChange(text?.toString().orEmpty())
        }
        binding.descriptionInput.doAfterTextChanged { text ->
            viewModel.onDescriptionChange(text?.toString().orEmpty())
        }
    }

    /** Section repliable (voir maquette "Ajouter plus de détails") : seul le moyen de paiement s'y trouve pour l'instant. */
    private fun setUpMoreDetailsToggle(binding: FragmentTransactionFormBinding) {
        binding.moreDetailsToggle.setOnClickListener {
            isMoreDetailsExpanded = !isMoreDetailsExpanded
            TransitionManager.beginDelayedTransition(binding.root, AutoTransition())
            binding.moreDetailsContainer.visibility = if (isMoreDetailsExpanded) View.VISIBLE else View.GONE
        }
    }

    private fun setUpFeeSwitch(binding: FragmentTransactionFormBinding) {
        binding.addFeeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isLoanLinked()) viewModel.onHasFeeToggle(isChecked)
        }
    }

    /** Liste FERMÉE (même pattern que [setUpPaymentMethodDropdown]) : les libellés viennent de
     * [FeeType.entries], dans leur ordre de déclaration. */
    private fun setUpFeeTypeDropdown(binding: FragmentTransactionFormBinding) {
        binding.feeTypeField.dropdownLayout.hint = getString(R.string.transaction_form_fee_type_label)
        val labels = FeeType.entries.map { getString(it.displayTextRes()) }
        binding.feeTypeField.dropdownInput.setSimpleItems(labels.toTypedArray())
        binding.feeTypeField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            viewModel.onFeeTypeChange(FeeType.entries[position])
        }
    }

    /** Pas d'exclusion (contrairement à [setUpAccountRow]/[setUpDestinationAccountRow]) : le
     * compte des frais peut être identique au compte source ou destination, ou un compte tiers
     * (voir cahier des charges, section "Compte utilisé pour les frais"). */
    private fun setUpFeeAccountRow(binding: FragmentTransactionFormBinding) {
        binding.feeAccountRow.setOnClickListener {
            if (isLoanLinked()) return@setOnClickListener
            AccountPickerDialog.show(
                context = requireContext(),
                accounts = latestAccounts,
                balanceFor = { account -> viewModel.accountBalances.value[account.id] ?: account.initialBalance },
                onSelect = { account -> viewModel.onFeeAccountChange(account.id) }
            )
        }
    }

    private fun setUpFeeInputs(binding: FragmentTransactionFormBinding) {
        binding.feeAmountInput.doAfterTextChanged { text ->
            viewModel.onFeeAmountChange(text?.toString().orEmpty())
        }
        binding.feeDescriptionInput.doAfterTextChanged { text ->
            viewModel.onFeeDescriptionChange(text?.toString().orEmpty())
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
            // Enchaîne directement sur l'heure : une seule interaction pour la ligne "Date et heure" (voir maquette).
            showTimePicker()
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

    private fun render(data: FormRenderState) {
        val binding = binding ?: return
        val state = data.formState
        latestAccounts = data.accounts
        latestCategories = data.categories
        latestSelectedCategoryId = state.categoryId

        val isTransfer = state.type == TransactionType.TRANSFER
        val isLoanLinked = state.linkedLoanId != null

        // Voir la doc de TransactionFormState.linkedLoanId : bannière + verrouillage des champs de
        // saisie DIRECTE (montant, description, listes déroulantes) plutôt qu'un écran entièrement
        // différent — les lignes cliquables (compte, catégorie, date) restent, elles, guardées
        // individuellement dans chaque setUpXxx() ci-dessus (isLoanLinked()).
        binding.loanLinkedBanner.visibility = if (isLoanLinked) View.VISIBLE else View.GONE
        binding.amountInput.isEnabled = !isLoanLinked
        binding.descriptionInput.isEnabled = !isLoanLinked
        binding.typeField.dropdownInput.isEnabled = !isLoanLinked
        binding.paymentMethodField.dropdownInput.isEnabled = !isLoanLinked
        binding.addFeeSwitch.isEnabled = !isLoanLinked
        binding.feeAmountInput.isEnabled = !isLoanLinked
        binding.feeTypeField.dropdownInput.isEnabled = !isLoanLinked
        binding.feeDescriptionInput.isEnabled = !isLoanLinked
        binding.deleteButton.visibility = if (viewModel.isEditMode && !isLoanLinked) View.VISIBLE else View.GONE

        renderType(binding, state.type)
        renderAmount(binding, state, data.accounts, data.accountBalances)
        renderFee(binding, state, data.accounts, data.accountBalances)

        // Une catégorie n'a pas de sens pour un transfert (voir TransactionType.TRANSFER) :
        // toute la carte disparaît plutôt que de la vider, pour ne pas laisser un bloc vide.
        binding.categoryCard.visibility = if (isTransfer) View.GONE else View.VISIBLE
        renderCategoryGrid()
        binding.categoryErrorText.text = state.categoryError
        binding.categoryErrorText.visibility = if (!isTransfer && state.categoryError != null) View.VISIBLE else View.GONE

        renderAccountRow(binding, state, data.accounts, data.accountBalances)
        renderDestinationAccountRow(binding, state, isTransfer, data.accounts, data.accountBalances)

        binding.dateTimeField.dateFieldValue.text = formatDateTimeRowValue(state.dateTimeMillis)

        if (binding.descriptionInput.text?.toString() != state.description) {
            binding.descriptionInput.setText(state.description)
        }

        val paymentMethodLabel = state.paymentMethod?.let { getString(it.displayTextRes()) }
            ?: getString(R.string.transaction_form_payment_method_none)
        if (binding.paymentMethodField.dropdownInput.text?.toString() != paymentMethodLabel) {
            binding.paymentMethodField.dropdownInput.setText(paymentMethodLabel, false)
        }
    }

    private fun renderType(binding: FragmentTransactionFormBinding, type: TransactionType) {
        val label = getString(TYPE_OPTIONS.first { (optionType, _) -> optionType == type }.second)
        if (binding.typeField.dropdownInput.text?.toString() != label) {
            binding.typeField.dropdownInput.setText(label, false)
        }
    }

    /** Montant et bouton "Enregistrer" teintés rouge (dépense) ou vert (revenu), voir maquette. */
    private fun renderAmount(
        binding: FragmentTransactionFormBinding,
        state: TransactionFormState,
        accounts: List<Account>,
        accountBalances: Map<Long, Long>
    ) {
        if (binding.amountInput.text?.toString() != state.amountInput) {
            binding.amountInput.setText(state.amountInput)
        }
        binding.amountErrorText.text = state.amountError
        binding.amountErrorText.visibility = if (state.amountError != null) View.VISIBLE else View.GONE

        // `when` exhaustif (pas de `else`) : le compilateur signale l'oubli si un
        // futur type s'ajoute à TransactionType, plutôt qu'un fallback silencieux.
        val typeColor = ContextCompat.getColor(
            requireContext(),
            when (state.type) {
                TransactionType.EXPENSE -> R.color.expense_red
                TransactionType.INCOME -> R.color.income_green
                TransactionType.TRANSFER -> R.color.arzikina_primary
            }
        )
        binding.amountInput.setTextColor(typeColor)

        val selectedAccount = accounts.firstOrNull { it.id == state.accountId }
        binding.amountCurrencyBadge.text = selectedAccount?.let {
            SupportedCurrency.entries.firstOrNull { currency -> currency.code == it.currencyCode }?.symbol ?: it.currencyCode
        }.orEmpty()
    }

    /**
     * Section "Frais supplémentaires" (voir [TransactionFormViewModel.onHasFeeToggle]) : Switch,
     * champs révélés ([FragmentTransactionFormBinding.feeDetailsCard]/[FragmentTransactionFormBinding.feeAccountRow])
     * et résumé dynamique (voir [renderFeeSummary]).
     */
    private fun renderFee(
        binding: FragmentTransactionFormBinding,
        state: TransactionFormState,
        accounts: List<Account>,
        accountBalances: Map<Long, Long>
    ) {
        if (binding.addFeeSwitch.isChecked != state.hasFee) {
            binding.addFeeSwitch.isChecked = state.hasFee
        }
        val feeVisibility = if (state.hasFee) View.VISIBLE else View.GONE
        binding.feeDetailsCard.visibility = feeVisibility
        binding.feeAccountRow.visibility = feeVisibility
        if (!state.hasFee) {
            binding.feeAccountErrorText.visibility = View.GONE
            return
        }

        if (binding.feeAmountInput.text?.toString() != state.feeAmountInput) {
            binding.feeAmountInput.setText(state.feeAmountInput)
        }
        binding.feeAmountErrorText.text = state.feeAmountError
        binding.feeAmountErrorText.visibility = if (state.feeAmountError != null) View.VISIBLE else View.GONE

        val feeTypeLabel = getString(state.feeType.displayTextRes())
        if (binding.feeTypeField.dropdownInput.text?.toString() != feeTypeLabel) {
            binding.feeTypeField.dropdownInput.setText(feeTypeLabel, false)
        }

        if (binding.feeDescriptionInput.text?.toString() != state.feeDescriptionInput) {
            binding.feeDescriptionInput.setText(state.feeDescriptionInput)
        }

        bindAccountField(binding.feeAccountField, accounts.firstOrNull { it.id == state.feeAccountId }, accountBalances)
        binding.feeAccountErrorText.text = state.feeAccountError
        binding.feeAccountErrorText.visibility = if (state.feeAccountError != null) View.VISIBLE else View.GONE

        renderFeeSummary(binding, state, accounts)
    }

    /**
     * Montant / Frais / Total(ou Revenu net), recalculé à chaque frappe (voir cahier des charges,
     * "le calcul doit être dynamique"). Dépense/Transfert : coût total = montant + frais. Revenu :
     * montant net REÇU = montant - frais (jamais négatif à l'affichage, une saisie de frais
     * supérieurs au montant reste par ailleurs bloquée par [TransactionFormViewModel.save] ?
     * non — volontairement PAS bloquée ici : rien dans le cahier des charges ne l'interdit, un
     * "revenu net" à 0 reste un résultat valide et compréhensible plutôt qu'une erreur bloquante).
     */
    private fun renderFeeSummary(binding: FragmentTransactionFormBinding, state: TransactionFormState, accounts: List<Account>) {
        val currencyCode = accounts.firstOrNull { it.id == state.accountId }?.currencyCode ?: Constants.DEFAULT_CURRENCY_CODE
        val amountMinor = Money.parseToMinorUnits(state.amountInput) ?: 0L
        val feeMinor = Money.parseToMinorUnits(state.feeAmountInput) ?: 0L

        binding.feeSummaryAmountValue.text = Money.format(CurrencyAmount(currencyCode, amountMinor))
        binding.feeSummaryFeeValue.text = Money.format(CurrencyAmount(currencyCode, feeMinor))

        val isIncome = state.type == TransactionType.INCOME
        val totalMinor = if (isIncome) (amountMinor - feeMinor).coerceAtLeast(0L) else amountMinor + feeMinor
        binding.feeSummaryTotalLabel.text = getString(
            if (isIncome) R.string.transaction_form_fee_summary_net_label else R.string.transaction_form_fee_summary_total_label
        )
        binding.feeSummaryTotalValue.text = Money.format(CurrencyAmount(currencyCode, totalMinor))
    }

    private fun renderCategoryGrid() {
        val binding = binding ?: return
        val visibleCategories = if (isCategoryGridExpanded || latestCategories.size <= COLLAPSED_CATEGORY_LIMIT) {
            latestCategories
        } else {
            latestCategories.take(COLLAPSED_CATEGORY_LIMIT)
        }
        val items = visibleCategories.map { CategoryPickerItem.Entry(it) } + CategoryPickerItem.AddNew
        categoryAdapter.submitItems(items, latestSelectedCategoryId)

        val canToggle = latestCategories.size > COLLAPSED_CATEGORY_LIMIT
        binding.categorySeeAllToggle.visibility = if (canToggle) View.VISIBLE else View.GONE
        binding.categorySeeAllToggle.text = getString(
            if (isCategoryGridExpanded) R.string.transaction_form_category_see_less else R.string.transaction_form_category_see_all
        )
    }

    private fun renderAccountRow(
        binding: FragmentTransactionFormBinding,
        state: TransactionFormState,
        accounts: List<Account>,
        accountBalances: Map<Long, Long>
    ) {
        bindAccountField(binding.accountField, accounts.firstOrNull { it.id == state.accountId }, accountBalances)
        binding.accountErrorText.text = state.accountError
        binding.accountErrorText.visibility = if (state.accountError != null) View.VISIBLE else View.GONE
    }

    /** Compte destination : uniquement visible pour un transfert (voir TransactionType.TRANSFER). */
    private fun renderDestinationAccountRow(
        binding: FragmentTransactionFormBinding,
        state: TransactionFormState,
        isTransfer: Boolean,
        accounts: List<Account>,
        accountBalances: Map<Long, Long>
    ) {
        binding.destinationAccountRow.visibility = if (isTransfer) View.VISIBLE else View.GONE
        if (!isTransfer) {
            binding.destinationAccountErrorText.visibility = View.GONE
            return
        }
        bindAccountField(binding.destinationAccountField, accounts.firstOrNull { it.id == state.transferAccountId }, accountBalances)
        binding.destinationAccountErrorText.text = state.transferAccountError
        binding.destinationAccountErrorText.visibility = if (state.transferAccountError != null) View.VISIBLE else View.GONE
    }

    /**
     * Logique de rendu partagée par [renderAccountRow] et [renderDestinationAccountRow]
     * (voir item_account_field.xml, inclus deux fois dans fragment_transaction_form.xml)
     * pour ne pas dupliquer ce if/else.
     */
    private fun bindAccountField(fieldBinding: ItemAccountFieldBinding, account: Account?, accountBalances: Map<Long, Long>) {
        if (account != null) {
            fieldBinding.accountFieldIcon.setImageResource(AccountIconMapper.iconFor(account.icon))
            fieldBinding.accountFieldIcon.backgroundTintList = ColorStateList.valueOf(account.colorArgb.toInt())
            fieldBinding.accountFieldName.text = account.name
            val balance = accountBalances[account.id] ?: account.initialBalance
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

    /**
     * "Aujourd'hui - 08/08/2026 · 15:41" (voir maquette) : le libellé relatif
     * réutilise les mêmes chaînes que les en-têtes de jour des listes de
     * transactions ([R.string.transaction_day_today]/[transaction_day_yesterday])
     * plutôt que d'en dupliquer une variante ici.
     */
    private fun formatDateTimeRowValue(dateTimeMillis: Long): String {
        val zonedDateTime = Instant.ofEpochMilli(dateTimeMillis).atZone(ZoneId.systemDefault())
        val date = zonedDateTime.toLocalDate()
        val today = LocalDate.now()
        val relativeLabel = when (date) {
            today -> getString(R.string.transaction_day_today)
            today.minusDays(1) -> getString(R.string.transaction_day_yesterday)
            else -> null
        }
        val datePart = date.format(DATE_FORMATTER)
        val timePart = zonedDateTime.toLocalTime().format(TIME_FORMATTER)
        val dateLabel = if (relativeLabel != null) "$relativeLabel - $datePart" else datePart
        return "$dateLabel · $timePart"
    }

    private fun handleEvent(event: TransactionFormEvent) {
        when (event) {
            TransactionFormEvent.Saved, TransactionFormEvent.Deleted -> findNavController().navigateUp()
            is TransactionFormEvent.LoanLinked -> navigateToLoanDetail(event.loanId)
        }
    }

    private fun confirmDelete() {
        if (isLoanLinked()) return
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.transactions_delete_title),
            message = getString(R.string.transactions_delete_message),
            onConfirm = { viewModel.delete() }
        )
    }

    private fun navigateToNewCategory() {
        findNavController().navigate(R.id.categoryFormFragment, bundleOf("categoryId" to 0L))
    }

    /** Voir la doc de `TransactionFormState.linkedLoanId` : accès synchrone utilisé par les
     * gestionnaires de clic (même principe que `viewModel.formState.value` ailleurs dans ce
     * Fragment, ex. [setUpAccountRow]). */
    private fun isLoanLinked(): Boolean = viewModel.formState.value.linkedLoanId != null

    /** `popUpTo` retire ce formulaire de la pile de retour (voir [TransactionFormEvent.LoanLinked]) :
     * revenir en arrière depuis "Détail du prêt/emprunt" doit ramener à l'écran d'où cette
     * transaction a été ouverte (Transactions/Détail de compte), pas à ce formulaire bloqué. Même
     * animation de fondu que [NavAnimations.fade], reconstruite ici pour y ajouter `popUpTo` (objet
     * partagé volontairement minimal, voir sa doc). */
    private fun navigateToLoanDetail(loanId: Long) {
        val options = NavOptions.Builder()
            .setEnterAnim(R.anim.fade_in)
            .setExitAnim(R.anim.fade_out)
            .setPopEnterAnim(R.anim.fade_in)
            .setPopExitAnim(R.anim.fade_out)
            .setPopUpTo(R.id.transactionFormFragment, inclusive = true)
            .build()
        findNavController().navigate(R.id.loanDetailFragment, bundleOf("loanId" to loanId), options)
    }

    /** Regroupe les 4 flux observés pour éviter un `combine` imbriqué illisible (voir [onViewCreated]). */
    private data class FormRenderState(
        val formState: TransactionFormState,
        val accounts: List<Account>,
        val categories: List<Category>,
        val accountBalances: Map<Long, Long>
    )

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
        const val COLLAPSED_CATEGORY_LIMIT = 7
        val QUICK_AMOUNTS = listOf(1_000L, 5_000L, 10_000L)

        /** Ordre d'affichage du menu déroulant "Type" (voir [setUpTypeDropdown]/[renderType]). */
        val TYPE_OPTIONS = listOf(
            TransactionType.EXPENSE to R.string.category_type_expense,
            TransactionType.INCOME to R.string.category_type_income,
            TransactionType.TRANSFER to R.string.transaction_form_type_transfer
        )
    }
}
