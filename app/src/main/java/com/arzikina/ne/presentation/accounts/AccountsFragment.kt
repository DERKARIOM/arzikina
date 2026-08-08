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
import com.arzikina.ne.databinding.FragmentAccountsBinding
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.Money
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Liste des comptes, avec ajout/édition/suppression. Reconstruit en
 * XML/Views (voir instructions projet) ; [AccountsViewModel] est inchangé.
 */
@AndroidEntryPoint
class AccountsFragment : Fragment(R.layout.fragment_accounts) {

    private val viewModel: AccountsViewModel by viewModels()
    private var binding: FragmentAccountsBinding? = null
    private val adapter = AccountsAdapter(
        onClick = { account -> navigateToDetail(account.id) },
        onDeleteClick = { account -> confirmDelete(account) }
    )

    /**
     * Purement local à l'écran, comme [com.arzikina.ne.presentation.dashboard.DashboardFragment.isBalanceHidden] :
     * revient à "visible" à chaque ouverture, pas de préférence persistée.
     */
    private var isTotalBalanceHidden = false
    private var latestTotalBalance: CurrencyAmount? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentAccountsBinding.bind(view)
        binding = viewBinding

        viewBinding.accountsList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.accountsList.adapter = adapter
        viewBinding.addAccountButton.setOnClickListener { navigateToForm(accountId = 0L) }
        viewBinding.toggleTotalBalanceVisibility.setOnClickListener {
            isTotalBalanceHidden = !isTotalBalanceHidden
            renderTotalBalanceText()
        }

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

    private fun render(state: AppResult<AccountsUiState>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return
        val uiState = state.data

        val hasAccounts = uiState.accounts.isNotEmpty()
        binding.accountsList.visibility = if (hasAccounts) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasAccounts) View.GONE else View.VISIBLE
        binding.totalBalanceCard.visibility = if (hasAccounts) View.VISIBLE else View.GONE
        adapter.submitList(uiState.accounts)

        latestTotalBalance = uiState.totalBalance
        renderTotalBalanceText()
    }

    private fun renderTotalBalanceText() {
        val binding = binding ?: return
        val totalBalance = latestTotalBalance ?: return
        binding.totalBalanceValue.text = if (isTotalBalanceHidden) {
            getString(R.string.dashboard_balance_masked)
        } else {
            Money.format(totalBalance)
        }
        binding.toggleTotalBalanceVisibility.setImageResource(
            if (isTotalBalanceHidden) R.drawable.ic_visibility_off_24 else R.drawable.ic_visibility_24
        )
        binding.toggleTotalBalanceVisibility.contentDescription = getString(
            if (isTotalBalanceHidden) R.string.accounts_balance_show_action else R.string.accounts_balance_hide_action
        )
    }

    private fun confirmDelete(account: Account) {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.accounts_delete_title),
            message = getString(R.string.accounts_delete_message, account.name),
            onConfirm = { viewModel.deleteAccount(account.id) }
        )
    }

    private fun navigateToForm(accountId: Long) {
        findNavController().navigate(R.id.accountFormFragment, bundleOf("accountId" to accountId))
    }

    /**
     * Clic sur un compte de la liste : ouvre désormais ses transactions
     * (voir [AccountDetailFragment]), plus le formulaire d'édition — éditer
     * un compte se fait dorénavant depuis le menu "⋮" de cet écran détail.
     */
    private fun navigateToDetail(accountId: Long) {
        findNavController().navigate(R.id.accountDetailFragment, bundleOf("accountId" to accountId))
    }
}
