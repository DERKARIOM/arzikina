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
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentTransactionFormBinding
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.SupportedCurrency
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.presentation.accounts.AccountIconMapper
import com.arzikina.ne.presentation.components.AccountPickerDialog
import com.arzikina.ne.presentation.components.ConfirmDialogs
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
 * classiques. Pas de type "Transfert", de dictée vocale ni de scan de reçu
 * ici — chantiers séparés.
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
        onSelect = { category -> viewModel.onCategoryChange(category.id) },
        onAddNew = { navigateToNewCategory() }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentTransactionFormBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        setUpTypeToggle(viewBinding)
        setUpQuickAmounts(viewBinding)
        setUpCategoryGrid(viewBinding)
        setUpAccountRow(viewBinding)
        setUpPaymentMethodDropdown(viewBinding)
        setUpDateTime(viewBinding)
        setUpInputs(viewBinding)
        setUpMoreDetailsToggle(viewBinding)

        viewBinding.saveButton.setOnClickListener { viewModel.save() }
        viewBinding.deleteButton.setOnClickListener { confirmDelete() }

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

    private fun setUpTypeToggle(binding: FragmentTransactionFormBinding) {
        binding.typeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val type = if (checkedId == R.id.typeIncome) TransactionType.INCOME else TransactionType.EXPENSE
            viewModel.onTypeChange(type)
        }
    }

    /** Raccourcis "+1 000"/"+5 000"/"+10 000" : voir [TransactionFormViewModel.onQuickAmountAdd]. */
    private fun setUpQuickAmounts(binding: FragmentTransactionFormBinding) {
        val formatter = NumberFormat.getIntegerInstance(Locale.FRENCH)
        val buttons = listOf(binding.quickAmount1Button, binding.quickAmount2Button, binding.quickAmount3Button)
        buttons.forEachIndexed { index, button ->
            val amount = QUICK_AMOUNTS[index]
            button.text = "+${formatter.format(amount)}"
            button.setOnClickListener { viewModel.onQuickAmountAdd(amount) }
        }
    }

    private fun setUpCategoryGrid(binding: FragmentTransactionFormBinding) {
        binding.categoryGrid.adapter = categoryAdapter
        binding.categorySeeAllToggle.setOnClickListener {
            isCategoryGridExpanded = !isCategoryGridExpanded
            renderCategoryGrid()
        }
    }

    /** Ouvre [AccountPickerDialog] au lieu d'un menu déroulant classique. */
    private fun setUpAccountRow(binding: FragmentTransactionFormBinding) {
        binding.accountRow.setOnClickListener {
            AccountPickerDialog.show(
                context = requireContext(),
                accounts = latestAccounts,
                balanceFor = { account -> viewModel.accountBalances.value[account.id] ?: account.initialBalance },
                onSelect = { account -> viewModel.onAccountChange(account.id) }
            )
        }
    }

    /**
     * Liste FERMÉE et fixe (contrairement à setUpAccountRow, qui dépend de
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

    /** Une seule ligne cliquable (voir maquette) : la date puis l'heure s'enchaînent au lieu de deux champs séparés. */
    private fun setUpDateTime(binding: FragmentTransactionFormBinding) {
        binding.dateTimeRow.setOnClickListener { showDatePicker() }
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

        renderType(binding, state.type)
        renderAmount(binding, state, data.accounts, data.accountBalances)
        renderCategoryGrid()
        binding.categoryErrorText.text = state.categoryError
        binding.categoryErrorText.visibility = if (state.categoryError != null) View.VISIBLE else View.GONE

        renderAccountRow(binding, state, data.accounts, data.accountBalances)

        binding.dateTimeValue.text = formatDateTimeRowValue(state.dateTimeMillis)

        if (binding.descriptionInput.text?.toString() != state.description) {
            binding.descriptionInput.setText(state.description)
        }

        val paymentMethodLabel = state.paymentMethod?.let { getString(it.displayTextRes()) }
            ?: getString(R.string.transaction_form_payment_method_none)
        if (binding.paymentMethodInput.text?.toString() != paymentMethodLabel) {
            binding.paymentMethodInput.setText(paymentMethodLabel, false)
        }
    }

    private fun renderType(binding: FragmentTransactionFormBinding, type: TransactionType) {
        val expectedTypeButtonId = if (type == TransactionType.INCOME) R.id.typeIncome else R.id.typeExpense
        if (binding.typeGroup.checkedButtonId != expectedTypeButtonId) {
            binding.typeGroup.check(expectedTypeButtonId)
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

        val typeColor = ContextCompat.getColor(
            requireContext(),
            if (state.type == TransactionType.EXPENSE) R.color.expense_red else R.color.income_green
        )
        binding.amountInput.setTextColor(typeColor)
        binding.saveButton.backgroundTintList = ColorStateList.valueOf(typeColor)

        val selectedAccount = accounts.firstOrNull { it.id == state.accountId }
        binding.amountCurrencyBadge.text = selectedAccount?.let {
            SupportedCurrency.entries.firstOrNull { currency -> currency.code == it.currencyCode }?.symbol ?: it.currencyCode
        }.orEmpty()
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
        val selectedAccount = accounts.firstOrNull { it.id == state.accountId }
        if (selectedAccount != null) {
            binding.accountRowIcon.setImageResource(AccountIconMapper.iconFor(selectedAccount.icon))
            binding.accountRowIcon.backgroundTintList = ColorStateList.valueOf(selectedAccount.colorArgb.toInt())
            binding.accountRowName.text = selectedAccount.name
            val balance = accountBalances[selectedAccount.id] ?: selectedAccount.initialBalance
            binding.accountRowBalance.text = getString(
                R.string.transaction_form_account_balance,
                Money.format(CurrencyAmount(selectedAccount.currencyCode, balance))
            )
            binding.accountRowBalance.visibility = View.VISIBLE
        } else {
            binding.accountRowIcon.setImageResource(R.drawable.ic_account_other_24)
            binding.accountRowIcon.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.arzikina_outline)
            )
            binding.accountRowName.text = getString(R.string.transaction_form_account_placeholder)
            binding.accountRowBalance.visibility = View.GONE
        }
        binding.accountErrorText.text = state.accountError
        binding.accountErrorText.visibility = if (state.accountError != null) View.VISIBLE else View.GONE
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

    private fun navigateToNewCategory() {
        findNavController().navigate(R.id.categoryFormFragment, bundleOf("categoryId" to 0L))
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
    }
}
