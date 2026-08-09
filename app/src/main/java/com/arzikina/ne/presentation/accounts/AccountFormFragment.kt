package com.arzikina.ne.presentation.accounts

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
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentAccountFormBinding
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.model.AccountType
import com.arzikina.ne.domain.model.SupportedCurrency
import com.arzikina.ne.presentation.components.ColorPickerAdapter
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.presentation.components.IconPickerAdapter
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
        val labels = TYPE_OPTIONS.map { getString(it.displayTextRes()) }
        binding.typeInput.setSimpleItems(labels.toTypedArray())
        binding.typeInput.setOnItemClickListener { _, _, position, _ ->
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
        val labels = SupportedCurrency.entries.map { "${it.displayName} (${it.symbol})" }
        binding.currencyInput.setSimpleItems(labels.toTypedArray())
        binding.currencyInput.setOnItemClickListener { _, _, position, _ ->
            viewModel.onCurrencyChange(SupportedCurrency.entries[position].code)
        }
    }

    private fun setUpInputs(binding: FragmentAccountFormBinding) {
        binding.nameInput.doAfterTextChanged { text ->
            viewModel.onNameChange(text?.toString().orEmpty())
        }
        binding.balanceInput.doAfterTextChanged { text ->
            viewModel.onInitialBalanceChange(text?.toString().orEmpty())
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
    }

    private fun render(state: AccountFormState) {
        val binding = binding ?: return

        if (binding.nameInput.text?.toString() != state.name) {
            binding.nameInput.setText(state.name)
        }
        binding.nameLayout.error = state.nameError

        if (binding.balanceInput.text?.toString() != state.initialBalanceInput) {
            binding.balanceInput.setText(state.initialBalanceInput)
        }
        binding.balanceLayout.error = state.balanceError

        val currencyLabel = SupportedCurrency.entries.firstOrNull { it.code == state.currencyCode }
            ?.let { "${it.displayName} (${it.symbol})" }
            .orEmpty()
        if (binding.currencyInput.text?.toString() != currencyLabel) {
            binding.currencyInput.setText(currencyLabel, false)
        }

        iconPickerAdapter.setSelected(state.icon)
        colorPickerAdapter.setSelected(state.colorArgb)

        val typeLabel = getString(state.type.displayTextRes())
        if (binding.typeInput.text?.toString() != typeLabel) {
            binding.typeInput.setText(typeLabel, false)
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
            binding.cardNumberLayout.error = state.cardNumberError
            binding.cardNumberLayout.helperText = state.existingCardLastFourDigits?.let {
                getString(R.string.account_form_card_number_helper_edit, it)
            }

            if (binding.cardExpiryInput.text?.toString() != state.cardExpiryInput) {
                binding.cardExpiryInput.setText(state.cardExpiryInput)
                binding.cardExpiryInput.setSelection(state.cardExpiryInput.length)
            }
            binding.cardExpiryLayout.error = state.cardExpiryError

            if (binding.cardCvvInput.text?.toString() != state.cardCvvInput) {
                binding.cardCvvInput.setText(state.cardCvvInput)
                binding.cardCvvInput.setSelection(state.cardCvvInput.length)
            }
            binding.cardCvvLayout.error = state.cardCvvError
        }
    }

    private fun handleEvent(event: AccountFormEvent) {
        when (event) {
            AccountFormEvent.Saved, AccountFormEvent.Deleted -> findNavController().navigateUp()
        }
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
