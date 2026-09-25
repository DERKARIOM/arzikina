package com.naniger.arzikina.presentation.accounts

import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentAccountFormBinding
import com.naniger.arzikina.domain.model.AccountIcon
import com.naniger.arzikina.domain.model.AccountType
import com.naniger.arzikina.domain.model.SupportedCurrency
import com.naniger.arzikina.presentation.components.ColorPickerAdapter
import com.naniger.arzikina.presentation.components.ConfirmDialogs
import com.naniger.arzikina.presentation.components.ExternalAppPickerDialog
import com.naniger.arzikina.presentation.components.IconPickerAdapter
import com.naniger.arzikina.presentation.components.pickerLabel
import com.naniger.arzikina.util.MoneyInputFormatter
import com.naniger.arzikina.util.external.ExternalAppInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Formulaire d'ajout/édition d'un compte. Reconstruit en XML/Views (voir
 * instructions projet) ; [AccountFormViewModel] est inchangé.
 *
 * Les champs texte ne remettent leur contenu à jour que si la valeur affichée
 * diffère de celle du [AccountFormState] : sans cette garde, chaque frappe
 * déclencherait un aller-retour ViewModel -> champ -> ViewModel qui replace
 * le curseur en fin de texte à chaque caractère.
 */
@AndroidEntryPoint
class AccountFormFragment : Fragment(R.layout.fragment_account_form) {

    private val viewModel: AccountFormViewModel by viewModels()
    private var binding: FragmentAccountFormBinding? = null

    private val iconPickerAdapter = IconPickerAdapter(
        items = AccountIcon.entries,
        iconRes = AccountIconMapper::iconFor,
        selected = AccountIcon.CASH,
        onSelect = { icon -> viewModel.onIconChange(icon) }
    )

    private val colorPickerAdapter = ColorPickerAdapter(
        selected = 0xFF10B981L,
        onSelect = { color -> viewModel.onColorChange(color) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentAccountFormBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        setUpTypeDropdown(viewBinding)
        setUpPickers(viewBinding)
        setUpCurrencyDropdown(viewBinding)
        setUpInputs(viewBinding)
        setUpExcludeFromStatisticsSwitch(viewBinding)

        viewBinding.saveButton.setOnClickListener { viewModel.save() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.formState.collect { state -> render(state) } }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpToolbar(binding: FragmentAccountFormBinding) {
        binding.toolbar.title = getString(
            if (viewModel.isEditMode) R.string.account_form_title_edit else R.string.account_form_title_add
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

    /** Liste FERMÉE (même pattern que `TransactionFormFragment.setUpTypeDropdown`) : les
     * libellés viennent de [TYPE_OPTIONS], dont l'ordre pilote celui affiché dans le menu. */
    private fun setUpTypeDropdown(binding: FragmentAccountFormBinding) {
        binding.typeField.dropdownLayout.hint = getString(R.string.account_form_type_label)
        val labels = TYPE_OPTIONS.map { getString(it.displayTextRes()) }
        binding.typeField.dropdownInput.setSimpleItems(labels.toTypedArray())
        binding.typeField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            viewModel.onTypeChange(TYPE_OPTIONS[position])
        }
    }

    private fun setUpPickers(binding: FragmentAccountFormBinding) {
        binding.iconPicker.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.iconPicker.adapter = iconPickerAdapter
        binding.colorPicker.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.colorPicker.adapter = colorPickerAdapter
    }

    private fun setUpCurrencyDropdown(binding: FragmentAccountFormBinding) {
        binding.currencyField.dropdownLayout.hint = getString(R.string.account_form_currency_label)
        val labels = SupportedCurrency.entries.map { it.pickerLabel(requireContext()) }
        binding.currencyField.dropdownInput.setSimpleItems(labels.toTypedArray())
        binding.currencyField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            viewModel.onCurrencyChange(SupportedCurrency.entries[position].code)
        }
    }

    private fun setUpInputs(binding: FragmentAccountFormBinding) {
        binding.nameInput.doAfterTextChanged { text ->
            viewModel.onNameChange(text?.toString().orEmpty())
        }
        MoneyInputFormatter.attach(binding.balanceInput) { formatted -> viewModel.onInitialBalanceChange(formatted) }
        MoneyInputFormatter.attach(binding.savingsTargetInput) { formatted -> viewModel.onSavingsTargetChange(formatted) }
        binding.savingsDescriptionInput.doAfterTextChanged { text ->
            viewModel.onSavingsDescriptionChange(text?.toString().orEmpty())
        }
        binding.cardNumberInput.doAfterTextChanged { text ->
            viewModel.onCardNumberChange(text?.toString().orEmpty())
        }
        binding.cardExpiryInput.doAfterTextChanged { text ->
            viewModel.onCardExpiryChange(text?.toString().orEmpty())
        }
        binding.cardCvvInput.doAfterTextChanged { text ->
            viewModel.onCardCvvChange(text?.toString().orEmpty())
        }
        binding.mobileMoneyPackageInput.doAfterTextChanged { text ->
            viewModel.onMobileMoneyPackageNameChange(text?.toString().orEmpty())
        }
        binding.selectMobileMoneyAppButton.setOnClickListener {
            viewModel.onSelectMobileMoneyAppClicked()
        }
    }

    private fun setUpExcludeFromStatisticsSwitch(binding: FragmentAccountFormBinding) {
        binding.excludeFromStatisticsSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.onExcludedFromStatisticsChange(isChecked)
        }
    }

    private fun render(state: AccountFormState) {
        val binding = binding ?: return

        if (binding.nameInput.text?.toString() != state.name) {
            binding.nameInput.setText(state.name)
        }
        binding.nameLayout.error = state.nameError?.let { getString(it) }

        if (binding.balanceInput.text?.toString() != state.initialBalanceInput) {
            binding.balanceInput.setText(state.initialBalanceInput)
        }
        binding.balanceLayout.error = state.balanceError?.let { getString(it) }

        val currencyLabel = SupportedCurrency.entries.firstOrNull { it.code == state.currencyCode }
            ?.pickerLabel(requireContext())
            .orEmpty()
        if (binding.currencyField.dropdownInput.text?.toString() != currencyLabel) {
            binding.currencyField.dropdownInput.setText(currencyLabel, false)
        }

        iconPickerAdapter.setSelected(state.icon)
        colorPickerAdapter.setSelected(state.colorArgb)

        if (binding.excludeFromStatisticsSwitch.isChecked != state.isExcludedFromStatistics) {
            binding.excludeFromStatisticsSwitch.isChecked = state.isExcludedFromStatistics
        }

        val typeLabel = getString(state.type.displayTextRes())
        if (binding.typeField.dropdownInput.text?.toString() != typeLabel) {
            binding.typeField.dropdownInput.setText(typeLabel, false)
        }

        val isSavingsGoal = state.type == AccountType.SAVINGS_GOAL
        val wasSavingsGoalFieldsVisible = binding.savingsGoalFieldsGroup.visibility == View.VISIBLE
        if (wasSavingsGoalFieldsVisible != isSavingsGoal) {
            TransitionManager.beginDelayedTransition(binding.formFieldsContainer, AutoTransition().setDuration(200L))
        }
        binding.savingsGoalFieldsGroup.visibility = if (isSavingsGoal) View.VISIBLE else View.GONE
        if (isSavingsGoal) {
            if (binding.savingsTargetInput.text?.toString() != state.savingsTargetInput) {
                binding.savingsTargetInput.setText(state.savingsTargetInput)
            }
            binding.savingsTargetLayout.error = state.savingsTargetError?.let { getString(it) }
            if (binding.savingsDescriptionInput.text?.toString() != state.savingsDescriptionInput) {
                binding.savingsDescriptionInput.setText(state.savingsDescriptionInput)
                binding.savingsDescriptionInput.setSelection(state.savingsDescriptionInput.length)
            }
        }

        val isCreditCard = state.type == AccountType.CREDIT_CARD
        val wasCreditCardFieldsVisible = binding.creditCardFieldsGroup.visibility == View.VISIBLE
        if (wasCreditCardFieldsVisible != isCreditCard) {
            // "Animations légères... lors de l'ajout de la carte" (section UX) : un fondu enchaîné
            // avec léger redimensionnement plutôt qu'une apparition/disparition instantanée du bloc
            // de champs — seulement quand la visibilité change RÉELLEMENT (pas à chaque frappe).
            TransitionManager.beginDelayedTransition(binding.formFieldsContainer, AutoTransition().setDuration(200L))
        }
        binding.creditCardFieldsGroup.visibility = if (isCreditCard) View.VISIBLE else View.GONE
        if (isCreditCard) {
            if (binding.cardNumberInput.text?.toString() != state.cardNumberInput) {
                binding.cardNumberInput.setText(state.cardNumberInput)
                binding.cardNumberInput.setSelection(state.cardNumberInput.length)
            }
            binding.cardNumberLayout.error = state.cardNumberError?.let { getString(it) }
            binding.cardNumberLayout.helperText = state.existingCardLastFourDigits?.let {
                getString(R.string.account_form_card_number_helper_edit, it)
            }

            if (binding.cardExpiryInput.text?.toString() != state.cardExpiryInput) {
                binding.cardExpiryInput.setText(state.cardExpiryInput)
                binding.cardExpiryInput.setSelection(state.cardExpiryInput.length)
            }
            binding.cardExpiryLayout.error = state.cardExpiryError?.let { getString(it) }

            if (binding.cardCvvInput.text?.toString() != state.cardCvvInput) {
                binding.cardCvvInput.setText(state.cardCvvInput)
                binding.cardCvvInput.setSelection(state.cardCvvInput.length)
            }
            binding.cardCvvLayout.error = state.cardCvvError?.let { getString(it) }
        }

        val isMobileMoney = state.type == AccountType.MOBILE_MONEY
        val wasMobileMoneyFieldsVisible = binding.mobileMoneyFieldsGroup.visibility == View.VISIBLE
        if (wasMobileMoneyFieldsVisible != isMobileMoney) {
            TransitionManager.beginDelayedTransition(binding.formFieldsContainer, AutoTransition().setDuration(200L))
        }
        binding.mobileMoneyFieldsGroup.visibility = if (isMobileMoney) View.VISIBLE else View.GONE
        if (isMobileMoney) {
            if (binding.mobileMoneyPackageInput.text?.toString() != state.mobileMoneyPackageNameInput) {
                binding.mobileMoneyPackageInput.setText(state.mobileMoneyPackageNameInput)
                binding.mobileMoneyPackageInput.setSelection(state.mobileMoneyPackageNameInput.length)
            }
            // Voir AccountFormState.mobileMoneyAppLabel : purement informatif, jamais un message
            // d'erreur (contrairement aux autres `.error` de ce formulaire) — un package non
            // résolu ici reste parfaitement valide (l'application peut être installée sur
            // l'appareil de l'utilisateur au moment où il ouvrira ce compte, pas forcément ici).
            binding.mobileMoneyPackageLayout.helperText = state.mobileMoneyAppLabel?.let {
                getString(R.string.account_form_mobile_money_detected_app, it)
            }
        }
    }

    private fun handleEvent(event: AccountFormEvent) {
        when (event) {
            AccountFormEvent.Saved, AccountFormEvent.Deleted -> findNavController().navigateUp()
            is AccountFormEvent.ShowAppPicker -> showAppPicker(event.apps)
            AccountFormEvent.ConfirmSavingsGoalRemoval -> confirmSavingsGoalRemoval()
        }
    }

    private fun showAppPicker(apps: List<ExternalAppInfo>) {
        ExternalAppPickerDialog.show(
            context = requireContext(),
            apps = apps,
            onSelect = { app -> viewModel.onMobileMoneyAppSelected(app) }
        )
    }

    /** Objectif d'épargne → compte classique : confirmation pour éviter une transformation
     * accidentelle (le montant cible et la description sont effacés ; solde et transactions,
     * eux, sont conservés — voir AccountFormViewModel.save). */
    private fun confirmSavingsGoalRemoval() {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.account_form_savings_remove_title),
            message = getString(R.string.account_form_savings_remove_message, viewModel.formState.value.name.trim()),
            confirmLabel = getString(R.string.account_form_savings_remove_action),
            onConfirm = { viewModel.save(confirmedSavingsGoalRemoval = true) }
        )
    }

    private fun confirmDelete() {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.accounts_delete_title),
            message = getString(R.string.accounts_delete_message, viewModel.formState.value.name),
            onConfirm = { viewModel.delete() }
        )
    }

    private companion object {
        /** Ordre d'affichage du menu déroulant "Type de compte" (voir [setUpTypeDropdown]). */
        val TYPE_OPTIONS = AccountType.entries
    }
}
