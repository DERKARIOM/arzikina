package com.arzikina.ne.presentation.accounts

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentAccountDetailBinding
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.presentation.transactions.GroupedTransactionsAdapter
import com.arzikina.ne.presentation.transactions.TransactionUiItem
import com.arzikina.ne.presentation.transactions.toListRows
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Transactions d'UN compte, groupées par jour — atteint en cliquant sur un
 * compte depuis "Mes comptes" (voir [AccountsFragment]). Remplace l'ancien
 * comportement (clic → formulaire d'édition) : éditer/supprimer le compte se
 * fait désormais via le menu "⋮" de la Toolbar de cet écran.
 */
@AndroidEntryPoint
class AccountDetailFragment : Fragment(R.layout.fragment_account_detail) {

    private val viewModel: AccountDetailViewModel by viewModels()
    private var binding: FragmentAccountDetailBinding? = null
    /**
     * Aucune suppression de transaction depuis cette liste (ni ailleurs dans
     * l'app désormais, voir [GroupedTransactionsAdapter]) : elle se fait
     * uniquement depuis le formulaire de modification, ouvert par [onClick]
     * ci-dessous — le compte, lui, garde son propre menu de suppression, "⋮".
     */
    private val adapter = GroupedTransactionsAdapter(
        onClick = { item -> navigateToTransactionForm(item) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentAccountDetailBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        // Carte incluse (voir fragment_account_detail.xml) : purement informative
        // ici, contrairement à la même carte sur "Mes comptes" qui navigue au clic.
        viewBinding.accountSummaryCard.accountCard.isClickable = false
        viewBinding.accountSummaryCard.accountCard.isFocusable = false
        viewBinding.transactionsList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.transactionsList.adapter = adapter
        viewBinding.addTransactionButton.setOnClickListener { navigateToNewTransactionForm() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpToolbar(binding: FragmentAccountDetailBinding) {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.inflateMenu(R.menu.account_detail_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit_account -> {
                    navigateToEditForm()
                    true
                }
                R.id.action_delete_account -> {
                    confirmDelete()
                    true
                }
                else -> false
            }
        }
    }

    private fun render(state: AppResult<AccountDetailUiState>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return
        val uiState = state.data
        val account = uiState.account

        binding.toolbar.title = account.name
        AccountCardBinder.bind(binding.accountSummaryCard, account, uiState.currentBalance)

        val rows = uiState.sections.toListRows()
        val hasTransactions = rows.isNotEmpty()
        binding.transactionsList.visibility = if (hasTransactions) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasTransactions) View.GONE else View.VISIBLE
        adapter.submitList(rows)
    }

    private fun navigateToTransactionForm(item: TransactionUiItem) {
        findNavController().navigate(
            R.id.transactionFormFragment,
            bundleOf("transactionId" to item.transaction.id)
        )
    }

    private fun navigateToNewTransactionForm() {
        findNavController().navigate(
            R.id.transactionFormFragment,
            bundleOf("transactionId" to 0L, "presetAccountId" to viewModel.accountId)
        )
    }

    private fun navigateToEditForm() {
        findNavController().navigate(R.id.accountFormFragment, bundleOf("accountId" to viewModel.accountId))
    }

    private fun confirmDelete() {
        val account = (viewModel.uiState.value as? AppResult.Success)?.data?.account ?: return
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.accounts_delete_title),
            message = getString(R.string.accounts_delete_message, account.name),
            onConfirm = {
                viewModel.deleteAccount()
                findNavController().navigateUp()
            }
        )
    }
}
