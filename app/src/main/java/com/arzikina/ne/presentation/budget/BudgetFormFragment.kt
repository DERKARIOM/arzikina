package com.arzikina.ne.presentation.budget

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentBudgetFormBinding
import com.arzikina.ne.domain.model.BudgetPeriod
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.SupportedCurrency
import com.arzikina.ne.presentation.components.ConfirmDialogs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Formulaire d'ajout/édition d'un budget. Reconstruit en XML/Views (voir
 * instructions projet) ; [BudgetFormViewModel] est inchangé.
 *
 * [BudgetFormViewModel.availableCategories] n'expose que les catégories de
 * dépense sans budget actif (plus celle déjà assignée en mode édition) :
 * quand cette liste est vide en mode ajout, le menu déroulant est désactivé
 * et [noCategoriesHint] explique pourquoi, plutôt que de laisser un menu
 * déroulant vide sans explication.
 */
@AndroidEntryPoint
class BudgetFormFragment : Fragment(R.layout.fragment_budget_form) {

    private val viewModel: BudgetFormViewModel by viewModels()
    private var binding: FragmentBudgetFormBinding? = null

    private var latestCategories: List<Category> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentBudgetFormBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        setUpCategoryDropdown(viewBinding)
        setUpCurrencyDropdown(viewBinding)
        setUpPeriodToggle(viewBinding)
        setUpInputs(viewBinding)

        viewBinding.saveButton.setOnClickListener { viewModel.save() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(
                        viewModel.formState,
                        viewModel.availableCategories
                    ) { state, categories -> state to categories }
                        .collect { (state, categories) -> render(state, categories) }
                }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpToolbar(binding: FragmentBudgetFormBinding) {
        binding.toolbar.title = getString(
            if (viewModel.isEditMode) R.string.budget_form_title_edit else R.string.budget_form_title_add
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

    private fun setUpCategoryDropdown(binding: FragmentBudgetFormBinding) {
        binding.categoryInput.setOnItemClickListener { _, _, position, _ ->
            latestCategories.getOrNull(position)?.let { viewModel.onCategoryChange(it.id) }
        }
    }

    private fun setUpCurrencyDropdown(binding: FragmentBudgetFormBinding) {
        val labels = SupportedCurrency.entries.map { "${it.displayName} (${it.symbol})" }
        binding.currencyInput.setSimpleItems(labels.toTypedArray())
        binding.currencyInput.setOnItemClickListener { _, _, position, _ ->
            viewModel.onCurrencyChange(SupportedCurrency.entries[position].code)
        }
    }

    private fun setUpPeriodToggle(binding: FragmentBudgetFormBinding) {
        binding.periodGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val period = if (checkedId == R.id.periodWeekly) BudgetPeriod.WEEKLY else BudgetPeriod.MONTHLY
            viewModel.onPeriodChange(period)
        }
    }

    private fun setUpInputs(binding: FragmentBudgetFormBinding) {
        binding.limitInput.doAfterTextChanged { text ->
            viewModel.onLimitChange(text?.toString().orEmpty())
        }
    }

    private fun render(state: BudgetFormState, categories: List<Category>) {
        val binding = binding ?: return
        latestCategories = categories

        val canPickCategory = categories.isNotEmpty()
        binding.categoryLayout.isEnabled = canPickCategory
        binding.noCategoriesHint.visibility = if (canPickCategory) View.GONE else View.VISIBLE

        binding.categoryInput.setSimpleItems(categories.map { it.name }.toTypedArray())
        val categoryLabel = categories.firstOrNull { it.id == state.categoryId }?.name.orEmpty()
        if (binding.categoryInput.text?.toString() != categoryLabel) {
            binding.categoryInput.setText(categoryLabel, false)
        }
        binding.categoryLayout.error = state.categoryError

        val expectedPeriodButtonId = if (state.period == BudgetPeriod.WEEKLY) R.id.periodWeekly else R.id.periodMonthly
        if (binding.periodGroup.checkedButtonId != expectedPeriodButtonId) {
            binding.periodGroup.check(expectedPeriodButtonId)
        }

        if (binding.limitInput.text?.toString() != state.limitInput) {
            binding.limitInput.setText(state.limitInput)
        }
        binding.limitLayout.error = state.limitError

        val currencyLabel = SupportedCurrency.entries.firstOrNull { it.code == state.currencyCode }
            ?.let { "${it.displayName} (${it.symbol})" }
            .orEmpty()
        if (binding.currencyInput.text?.toString() != currencyLabel) {
            binding.currencyInput.setText(currencyLabel, false)
        }
    }

    private fun handleEvent(event: BudgetFormEvent) {
        when (event) {
            BudgetFormEvent.Saved, BudgetFormEvent.Deleted -> findNavController().navigateUp()
        }
    }

    private fun confirmDelete() {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.budgets_delete_title),
            message = getString(R.string.budgets_delete_message),
            onConfirm = { viewModel.delete() }
        )
    }
}
