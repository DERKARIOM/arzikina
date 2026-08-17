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
import com.arzikina.ne.domain.model.AccountType
import com.arzikina.ne.presentation.components.NavAnimations
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Liste des comptes, sous forme de cartes façon carte bancaire (voir
 * `item_account.xml`). Reconstruit en XML/Views (voir instructions projet) ;
 * [AccountsViewModel] est inchangé. Ajout via [addAccountButton] ; modifier/
 * supprimer se fait depuis "Détail du compte" (menu "⋮"), plus depuis cette
 * liste (voir [AccountsAdapter]).
 */
@AndroidEntryPoint
class AccountsFragment : Fragment(R.layout.fragment_accounts) {

    private val viewModel: AccountsViewModel by viewModels()
    private var binding: FragmentAccountsBinding? = null
    private val adapter = AccountsAdapter(
        onClick = { account -> navigateToDetail(account.id) }
    )

    /** Dernière liste COMPLÈTE reçue de [AccountsViewModel.uiState] (tous types confondus) —
     * conservée pour pouvoir refiltrer immédiatement quand seul l'onglet change (voir
     * [renderTab]), sans attendre une nouvelle émission de `uiState`. */
    private var latestAccounts: List<AccountUiItem> = emptyList()

    /** `null` tant qu'aucun onglet n'a encore été rendu (premier affichage, voir [renderTab]) —
     * distingue "changement d'onglet réel" (à animer) de la toute première émission de
     * [AccountsViewModel.selectedTab] au moment de la collecte (à afficher directement, sans
     * fondu sur un écran qui vient tout juste d'apparaître). */
    private var lastRenderedTab: AccountsDisplayTab? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentAccountsBinding.bind(view)
        binding = viewBinding

        viewBinding.accountsList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.accountsList.adapter = adapter
        viewBinding.addAccountButton.setOnClickListener { navigateToForm() }

        setUpTabGroup(viewBinding)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state -> render(state) } }
                launch { viewModel.selectedTab.collect { tab -> renderTab(viewBinding, tab) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        // Vue détruite (ex. navigation vers "Détail du compte") : la prochaine recréation doit
        // considérer son premier rendu comme un "premier affichage" (pas de fondu), même si
        // AccountsViewModel.selectedTab n'a pas changé entre-temps (voir la doc de la propriété).
        lastRenderedTab = null
    }

    /** `isChecked` est rappelé pour LES DEUX boutons à chaque bascule (celui qui se coche ET
     * celui qui se décoche, voir la doc de `MaterialButtonToggleGroup`) — filtrer sur
     * `isChecked == true` évite de traiter l'événement en double. */
    private fun setUpTabGroup(binding: FragmentAccountsBinding) {
        binding.accountsTabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val tab = if (checkedId == R.id.btnBankCards) {
                AccountsDisplayTab.BANK_CARDS
            } else {
                AccountsDisplayTab.ACCOUNTS
            }
            viewModel.onTabSelected(tab)
        }
    }

    private fun render(state: AppResult<AccountsUiState>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return
        latestAccounts = state.data.accounts
        renderList(binding)
    }

    /** Synchronise le bouton coché (utile si l'onglet a été mémorisé par le ViewModel avant que
     * la vue ne soit recréée, ex. retour depuis "Détail du compte") et le message d'écran vide,
     * puis refiltre la liste déjà en mémoire — aucun rechargement de données. N'anime QUE lors
     * d'un changement d'onglet réel (voir [lastRenderedTab]), jamais au tout premier affichage. */
    private fun renderTab(binding: FragmentAccountsBinding, tab: AccountsDisplayTab) {
        val checkedId = if (tab == AccountsDisplayTab.BANK_CARDS) R.id.btnBankCards else R.id.btnAccounts
        if (binding.accountsTabGroup.checkedButtonId != checkedId) {
            binding.accountsTabGroup.check(checkedId)
        }
        binding.emptyState.text = getString(
            if (tab == AccountsDisplayTab.BANK_CARDS) {
                R.string.accounts_empty_bank_cards_message
            } else {
                R.string.accounts_empty_message
            }
        )

        val previousTab = lastRenderedTab
        lastRenderedTab = tab
        if (previousTab == null || previousTab == tab) {
            renderList(binding)
        } else {
            animateTabSwitch(binding)
        }
    }

    /**
     * Fondu enchaîné + léger glissement horizontal (voir cahier des charges : "fade, éventuellement
     * léger slide horizontal, 200-250ms") lors d'un changement d'onglet réel. `accountsContent`
     * (voir `fragment_accounts.xml`) regroupe `accountsList`/`emptyState` : un seul alpha à animer,
     * pas deux animations à coordonner sur des vues normalement mutuellement exclusives.
     * `renderList` (qui recalcule juste un filtre en mémoire, aucun accès disque/réseau) s'exécute
     * pendant le creux du fondu, jamais visible pour l'utilisateur.
     */
    private fun animateTabSwitch(binding: FragmentAccountsBinding) {
        val content = binding.accountsContent
        content.animate().cancel()
        val slideDistance = resources.getDimension(R.dimen.spacing_m)
        content.animate()
            .alpha(0f)
            .translationX(-slideDistance)
            .setDuration(TAB_SWITCH_FADE_OUT_MS)
            .withEndAction {
                renderList(binding)
                content.translationX = slideDistance
                content.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(TAB_SWITCH_FADE_IN_MS)
                    .start()
            }
            .start()
    }

    private fun renderList(binding: FragmentAccountsBinding) {
        val filtered = latestAccounts.filter { it.matchesTab(viewModel.selectedTab.value) }
        val hasAccounts = filtered.isNotEmpty()
        binding.accountsList.visibility = if (hasAccounts) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasAccounts) View.GONE else View.VISIBLE
        adapter.submitList(filtered)
    }

    /**
     * `initialType` (voir `nav_graph.xml`) : présélectionne "Carte de crédit" quand on ajoute
     * depuis l'onglet "Cartes bancaires", sinon `null` (comportement inchangé — l'utilisateur
     * choisit lui-même le type, comme avant l'ajout du ToggleGroup). Toujours `accountId = 0L`
     * (nouveau compte) : ce bouton ne sert qu'à l'ajout, jamais à l'édition.
     */
    private fun navigateToForm() {
        val initialType = if (viewModel.selectedTab.value == AccountsDisplayTab.BANK_CARDS) {
            AccountType.CREDIT_CARD.name
        } else {
            null
        }
        findNavController().navigate(
            R.id.accountFormFragment,
            bundleOf("accountId" to 0L, "initialType" to initialType),
            NavAnimations.push
        )
    }

    /**
     * Clic sur un compte de la liste : ouvre désormais ses transactions
     * (voir [AccountDetailFragment]), plus le formulaire d'édition — éditer
     * un compte se fait dorénavant depuis le menu "⋮" de cet écran détail.
     */
    private fun navigateToDetail(accountId: Long) {
        findNavController().navigate(R.id.accountDetailFragment, bundleOf("accountId" to accountId), NavAnimations.push)
    }

    private companion object {
        /** Voir [animateTabSwitch] — les deux moitiés cumulées (220ms) restent dans la fourchette
         * 200-250ms demandée pour la transition d'onglet. */
        const val TAB_SWITCH_FADE_OUT_MS = 110L
        const val TAB_SWITCH_FADE_IN_MS = 110L
    }
}
