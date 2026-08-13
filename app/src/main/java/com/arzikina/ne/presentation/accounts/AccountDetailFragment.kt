package com.arzikina.ne.presentation.accounts

import android.os.Bundle
import android.view.View
import android.view.WindowManager
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
import com.arzikina.ne.domain.model.AccountType
import com.arzikina.ne.domain.model.CardSecrets
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.presentation.components.NavAnimations
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

    /**
     * Dernier état métier connu (voir [render]) et dernier secret révélé connu (voir le
     * collecteur de [AccountDetailViewModel.cardSecrets] ci-dessous) : conservés séparément
     * plutôt que fusionnés dans un seul état, car ils viennent de deux flux indépendants du
     * ViewModel et se redessinent chacun à leur rythme (voir [renderCreditCardCard]).
     */
    private var latestUiState: AccountDetailUiState? = null
    private var latestCardSecrets: CardSecrets? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentAccountDetailBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        // Cartes incluses (voir fragment_account_detail.xml) : purement informatives
        // ici, contrairement aux mêmes cartes sur "Mes comptes" qui naviguent au clic.
        viewBinding.accountSummaryCard.accountCard.isClickable = false
        viewBinding.accountSummaryCard.accountCard.isFocusable = false
        viewBinding.accountSummaryCreditCard.accountCard.isClickable = false
        viewBinding.accountSummaryCreditCard.accountCard.isFocusable = false
        viewBinding.transactionsList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.transactionsList.adapter = adapter
        viewBinding.addTransactionButton.setOnClickListener { navigateToNewTransactionForm() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // `drop(1)` inutile ici : la 1ère valeur est toujours `null`, donc l'animation de
                // fondu (voir [animateCreditCardVisibilityChange]) part déjà d'un état masqué
                // cohérent avec le contenu initial dessiné par [render].
                viewModel.cardSecrets.collect { secrets ->
                    latestCardSecrets = secrets
                    animateCreditCardVisibilityChange()
                }
            }
        }
    }

    /**
     * Masquage automatique "lorsque l'écran n'est plus visible" (section
     * sécurité) : couvre l'app mise en arrière-plan ET la navigation vers un
     * autre écran, sans dépendre du délai auto-remasquage du ViewModel.
     * Défense en profondeur en plus de [WindowManager.LayoutParams.FLAG_SECURE]
     * (qui empêche captures d'écran/aperçu récents, mais pas un simple retour
     * au premier plan après un moment).
     */
    override fun onPause() {
        super.onPause()
        viewModel.hideCardSecrets()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Ne doit jamais "fuiter" vers un autre écran (voir sa pose dans [render]).
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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
        latestUiState = uiState

        binding.toolbar.title = account.name

        val isCreditCard = account.type == AccountType.CREDIT_CARD
        binding.accountSummaryCard.accountCard.visibility = if (isCreditCard) View.GONE else View.VISIBLE
        binding.accountSummaryCreditCard.accountCard.visibility = if (isCreditCard) View.VISIBLE else View.GONE
        if (isCreditCard) {
            renderCreditCardCard()
        } else {
            AccountCardBinder.bind(binding.accountSummaryCard, account, uiState.currentBalance)
        }

        // Protection contre les captures d'écran/aperçu récents UNIQUEMENT pour une carte de
        // crédit (voir section sécurité) : un compte classique n'affiche rien de sensible ici.
        activity?.window?.setFlags(
            if (isCreditCard) WindowManager.LayoutParams.FLAG_SECURE else 0,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val rows = uiState.sections.toListRows()
        val hasTransactions = rows.isNotEmpty()
        binding.transactionsList.visibility = if (hasTransactions) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasTransactions) View.GONE else View.VISIBLE
        adapter.submitList(rows)
    }

    /**
     * Ne redessine QUE la carte de crédit, à partir de [latestUiState] déjà connu — séparée de
     * [render] pour être rappelable seule depuis le bouton œil ou [onPause], sans dépendre d'une
     * nouvelle émission de [AccountDetailViewModel.uiState] (même principe que
     * `DashboardFragment.renderBalanceText`).
     */
    private fun renderCreditCardCard() {
        val binding = binding ?: return
        val uiState = latestUiState ?: return
        AccountCardCreditBinder.bind(
            binding = binding.accountSummaryCreditCard,
            account = uiState.account,
            currentBalance = uiState.currentBalance,
            cardHolderName = uiState.cardHolderName,
            revealedSecrets = latestCardSecrets,
            showVisibilityToggle = true,
            onToggleVisibility = { viewModel.toggleCardSecrets() }
        )
    }

    /**
     * Anime le passage masqué <-> révélé (numéro complet, expiration, CVV — "animations
     * légères... lors de l'affichage/masquage des informations", section UX) : un léger fondu
     * enchaîné plutôt qu'un remplacement de texte instantané. [renderCreditCardCard] reste
     * appelée pour le contenu ; cette fonction ne fait qu'entourer cet appel d'une transition
     * visuelle. Sans effet pour un compte classique (les vues n'existent pas dans son layout).
     */
    private fun animateCreditCardVisibilityChange() {
        val numberView = binding?.accountSummaryCreditCard?.cardMaskedNumber
        if (numberView == null) {
            renderCreditCardCard()
            return
        }
        numberView.animate()
            .alpha(0f)
            .setDuration(FADE_ANIM_MILLIS)
            .withEndAction {
                renderCreditCardCard()
                binding?.accountSummaryCreditCard?.cardMaskedNumber?.animate()
                    ?.alpha(1f)
                    ?.setDuration(FADE_ANIM_MILLIS)
                    ?.start()
            }
            .start()
    }

    private fun navigateToTransactionForm(item: TransactionUiItem) {
        findNavController().navigate(
            R.id.transactionFormFragment,
            bundleOf("transactionId" to item.transaction.id),
            NavAnimations.push
        )
    }

    private fun navigateToNewTransactionForm() {
        findNavController().navigate(
            R.id.transactionFormFragment,
            bundleOf("transactionId" to 0L, "presetAccountId" to viewModel.accountId),
            NavAnimations.push
        )
    }

    private fun navigateToEditForm() {
        findNavController().navigate(R.id.accountFormFragment, bundleOf("accountId" to viewModel.accountId), NavAnimations.push)
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

    private companion object {
        /** Durée du fondu d'affichage/masquage (voir [animateCreditCardVisibilityChange]). */
        const val FADE_ANIM_MILLIS = 120L
    }
}
