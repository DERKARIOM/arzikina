package com.arzikina.ne.presentation.transactions

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentTransactionsBinding
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Liste des transactions avec recherche libre, filtres (type, période,
 * compte, catégorie) et suppression. Reconstruite en XML/Views (voir
 * instructions projet) ; [TransactionsViewModel] est inchangé.
 *
 * Le panneau de filtres est masqué par défaut (voir `fragment_transactions.xml`)
 * et basculé par l'action "filtres" de la barre d'outils — état purement
 * visuel, pas besoin de le faire porter par le ViewModel.
 */
@AndroidEntryPoint
class TransactionsFragment : Fragment(R.layout.fragment_transactions) {

    private val viewModel: TransactionsViewModel by viewModels()
    private var binding: FragmentTransactionsBinding? = null

    private val adapter = TransactionsAdapter(
        onClick = { item -> navigateToForm(item.transaction.id) },
        onDeleteClick = { item -> confirmDelete(item) }
    )

    /** Dernières listes reçues, pour convertir une position de menu déroulant en identifiant. */
    private var latestAccounts: List<Account> = emptyList()
    private var latestCategories: List<Category> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentTransactionsBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        setUpList(viewBinding)
        setUpSearch(viewBinding)
        setUpChips(viewBinding)
        setUpDropdowns(viewBinding)

        viewBinding.resetFiltersButton.setOnClickListener { viewModel.resetFilters() }
        viewBinding.addTransactionButton.setOnClickListener { navigateToForm(transactionId = 0L) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state -> render(state) } }
                launch {
                    combine(
                        viewModel.filters,
                        viewModel.accounts,
                        viewModel.categories
                    ) { filters, accounts, categories -> Triple(filters, accounts, categories) }
                        .collect { (filters, accounts, categories) ->
                            renderFilters(filters, accounts, categories)
                        }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpToolbar(binding: FragmentTransactionsBinding) {
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_toggle_filters) {
                toggleFiltersPanel(binding)
                true
            } else {
                false
            }
        }
    }

    private fun toggleFiltersPanel(binding: FragmentTransactionsBinding) {
        binding.filtersPanel.visibility = if (binding.filtersPanel.visibility == View.VISIBLE) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun setUpList(binding: FragmentTransactionsBinding) {
        binding.transactionsList.layoutManager = LinearLayoutManager(requireContext())
        binding.transactionsList.adapter = adapter
    }

    private fun setUpSearch(binding: FragmentTransactionsBinding) {
        binding.searchInput.doAfterTextChanged { text ->
            viewModel.onQueryChange(text?.toString().orEmpty())
        }
    }

    private fun setUpChips(binding: FragmentTransactionsBinding) {
        binding.typeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val type = when (checkedIds.firstOrNull()) {
                R.id.typeChipExpense -> TransactionTypeFilter.EXPENSE
                R.id.typeChipIncome -> TransactionTypeFilter.INCOME
                else -> TransactionTypeFilter.ALL
            }
            if (type != viewModel.filters.value.type) {
                viewModel.onTypeFilterChange(type)
            }
        }
        binding.periodChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val period = when (checkedIds.firstOrNull()) {
                R.id.periodChipWeek -> TransactionPeriodFilter.THIS_WEEK
                R.id.periodChipMonth -> TransactionPeriodFilter.THIS_MONTH
                else -> TransactionPeriodFilter.ALL
            }
            if (period != viewModel.filters.value.period) {
                viewModel.onPeriodFilterChange(period)
            }
        }
    }

    private fun setUpDropdowns(binding: FragmentTransactionsBinding) {
        binding.accountFilterInput.setOnItemClickListener { _, _, position, _ ->
            val accountId = if (position == 0) null else latestAccounts.getOrNull(position - 1)?.id
            viewModel.onAccountFilterChange(accountId)
        }
        binding.categoryFilterInput.setOnItemClickListener { _, _, position, _ ->
            val categoryId = if (position == 0) null else latestCategories.getOrNull(position - 1)?.id
            viewModel.onCategoryFilterChange(categoryId)
        }
    }

    private fun render(state: AppResult<List<TransactionUiItem>>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return

        val hasTransactions = state.data.isNotEmpty()
        binding.transactionsList.visibility = if (hasTransactions) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasTransactions) View.GONE else View.VISIBLE
        adapter.submitList(state.data)
    }

    private fun renderFilters(filters: TransactionFilters, accounts: List<Account>, categories: List<Category>) {
        val binding = binding ?: return
        latestAccounts = accounts
        latestCategories = categories

        if (binding.searchInput.text?.toString() != filters.query) {
            binding.searchInput.setText(filters.query)
        }

        val expectedTypeChip = when (filters.type) {
            TransactionTypeFilter.ALL -> R.id.typeChipAll
            TransactionTypeFilter.EXPENSE -> R.id.typeChipExpense
            TransactionTypeFilter.INCOME -> R.id.typeChipIncome
        }
        if (binding.typeChipGroup.checkedChipId != expectedTypeChip) {
            binding.typeChipGroup.check(expectedTypeChip)
        }

        val expectedPeriodChip = when (filters.period) {
            TransactionPeriodFilter.ALL -> R.id.periodChipAll
            TransactionPeriodFilter.THIS_WEEK -> R.id.periodChipWeek
            TransactionPeriodFilter.THIS_MONTH -> R.id.periodChipMonth
        }
        if (binding.periodChipGroup.checkedChipId != expectedPeriodChip) {
            binding.periodChipGroup.check(expectedPeriodChip)
        }

        val accountLabels = listOf(getString(R.string.transactions_filter_all_accounts)) + accounts.map { it.name }
        binding.accountFilterInput.setSimpleItems(accountLabels.toTypedArray())
        val accountLabel = accounts.firstOrNull { it.id == filters.accountId }?.name
            ?: getString(R.string.transactions_filter_all_accounts)
        if (binding.accountFilterInput.text?.toString() != accountLabel) {
            binding.accountFilterInput.setText(accountLabel, false)
        }

        val categoryLabels = listOf(getString(R.string.transactions_filter_all_categories)) + categories.map { it.name }
        binding.categoryFilterInput.setSimpleItems(categoryLabels.toTypedArray())
        val categoryLabel = categories.firstOrNull { it.id == filters.categoryId }?.name
            ?: getString(R.string.transactions_filter_all_categories)
        if (binding.categoryFilterInput.text?.toString() != categoryLabel) {
            binding.categoryFilterInput.setText(categoryLabel, false)
        }

        binding.resetFiltersButton.visibility = if (filters.hasActiveFilters) View.VISIBLE else View.GONE
    }

    private fun confirmDelete(item: TransactionUiItem) {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.transactions_delete_title),
            message = getString(R.string.transactions_delete_message),
            onConfirm = { viewModel.deleteTransaction(item.transaction.id) }
        )
    }

    private fun navigateToForm(transactionId: Long) {
        findNavController().navigate(R.id.transactionFormFragment, bundleOf("transactionId" to transactionId))
    }
}
