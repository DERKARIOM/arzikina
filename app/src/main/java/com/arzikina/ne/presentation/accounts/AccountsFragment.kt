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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentAccountsBinding
import com.arzikina.ne.domain.model.AccountType
import com.arzikina.ne.presentation.components.NavAnimations
import com.arzikina.ne.presentation.utilities.financialplan.FinancialPlanUiItem
import com.arzikina.ne.presentation.utilities.financialplan.FinancialPlansAdapter
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Liste des comptes, sous forme de cartes façon carte bancaire (voir
 * `item_account.xml`), avec un 3e onglet "Planification" (voir [AccountsDisplayTab.PLANNING])
 * réutilisant tel quel [FinancialPlansAdapter]/`item_financial_plan.xml` de l'écran "Mes
 * planifications" dédié — même carte, même navigation, aucune logique dupliquée. Reconstruit en
 * XML/Views (voir instructions projet) ; [AccountsViewModel] est inchangé pour les onglets
 * Comptes/Cartes bancaires. Ajout via [addAccountButton] (adapté par onglet) ; modifier/supprimer
 * un compte se fait depuis "Détail du compte" (menu "⋮"), plus depuis cette liste (voir
 * [AccountsAdapter]) — une planification se modifie/supprime de même depuis son propre écran de
 * détail (voir [FinancialPlansAdapter]).
 */
@AndroidEntryPoint
class AccountsFragment : Fragment(R.layout.fragment_accounts) {

    private val viewModel: AccountsViewModel by viewModels()
    private var binding: FragmentAccountsBinding? = null
    private val adapter = AccountsAdapter(
        onClick = { account -> navigateToDetail(account.id) }
    )
    private val financialPlansAdapter = FinancialPlansAdapter(
        onClick = { item -> navigateToFinancialPlanDetail(item.plan.id) }
    )

    /** Dernière liste COMPLÈTE reçue de [AccountsViewModel.uiState] (tous types confondus) —
     * conservée pour pouvoir refiltrer immédiatement quand seul l'onglet change (voir
     * [renderTab]), sans attendre une nouvelle émission de `uiState`. */
    private var latestAccounts: List<AccountUiItem> = emptyList()

    /** Même raisonnement que [latestAccounts] mais pour l'onglet Planification (voir
     * [AccountsViewModel.financialPlans]) — flux entièrement séparé, propre écran vide (voir
     * [renderList]). */
    private var latestFinancialPlans: List<FinancialPlanUiItem> = emptyList()

    /** `null` tant qu'aucun onglet n'a encore été rendu (premier affichage, voir [renderTab]) —
     * distingue "changement d'onglet réel" (à animer) de la toute première émission de
     * [AccountsViewModel.selectedTab] au moment de la collecte (à afficher directement, sans
     * fondu sur un écran qui vient tout juste d'apparaître). */
    private var lastRenderedTab: AccountsDisplayTab? = null

    /**
     * `true` entre le début d'un glisser (`ACTION_STATE_DRAG`, voir [itemTouchHelperCallback]) et
     * son dépôt — [renderList] ignore toute mise à jour de la liste affichée tant que ce drapeau
     * est actif (voir sa doc) : sans cette garde, une émission de [AccountsViewModel.uiState]
     * survenant PENDANT le glisser (ex. une synchronisation en arrière-plan qui aboutit à ce moment
     * précis) ré-appellerait `adapter.submitList(...)` avec l'ordre encore "officiel", ce qui
     * ferait sauter la carte hors du doigt de l'utilisateur en plein déplacement.
     */
    private var isReordering = false

    /**
     * Glisser-déposer vertical (voir cahier des charges "réorganiser les comptes") — actif
     * UNIQUEMENT sur l'onglet [AccountsDisplayTab.ACCOUNTS] ([getDragDirs] renvoie `0` sinon,
     * jamais sur Cartes bancaires/Planification, voir leur propre logique métier). Animations
     * volontairement légères (élévation + zoom discret, voir [animateDragStart]/[animateDragEnd]) :
     * "éviter les animations lourdes" (cahier des charges).
     */
    private val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0
    ) {
        override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
            if (viewModel.selectedTab.value == AccountsDisplayTab.ACCOUNTS) super.getDragDirs(recyclerView, viewHolder) else 0

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            adapter.moveItem(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit // pas de swipe sur cet écran

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                isReordering = true
                adapter.beginReorder()
                animateDragStart(viewHolder.itemView)
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            animateDragEnd(viewHolder.itemView)
            if (!isReordering) return
            isReordering = false
            val orderedIds = adapter.endReorder()
            viewModel.reorderAccounts(orderedIds)
            // Ré-applique désormais [latestAccounts] (potentiellement mis à jour PENDANT le
            // glisser, voir la doc d'[isReordering]) — sans effet visible si rien n'a changé
            // entretemps, puisque l'ordre qu'on vient d'écrire correspond déjà à ce qui est affiché.
            binding?.let { renderList(it) }
        }
    }
    private val itemTouchHelper = ItemTouchHelper(itemTouchHelperCallback)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentAccountsBinding.bind(view)
        binding = viewBinding

        viewBinding.accountsList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.accountsList.adapter = adapter
        viewBinding.planningList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.planningList.adapter = financialPlansAdapter
        // Changer d'onglet remplace la liste par un jeu d'ids ENTIÈREMENT différent (comptes vs
        // cartes bancaires, voir AccountUiItem.matchesTab) : DiffUtil traite donc ça comme "tout
        // supprimer, tout ajouter", ce qui déclenche les animations d'ajout/suppression par défaut
        // de RecyclerView (DefaultItemAnimator) EN PLUS de notre propre fondu sur `accountsContent`
        // (voir animateTabSwitch) — les deux animations se superposent avec des durées/courbes
        // différentes, d'où le clignotement observé. On désactive l'animateur d'item ici : le
        // fondu du conteneur suffit déjà à habiller le changement de contenu.
        viewBinding.accountsList.itemAnimator = null
        viewBinding.planningList.itemAnimator = null
        itemTouchHelper.attachToRecyclerView(viewBinding.accountsList)
        viewBinding.addAccountButton.setOnClickListener { navigateToForm() }
        viewBinding.planningEmptyAction.setOnClickListener { navigateToFinancialPlanForm() }

        setUpTabGroup(viewBinding)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state -> render(state) } }
                launch { viewModel.financialPlans.collect { state -> renderFinancialPlans(state) } }
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

    /** `isChecked` est rappelé pour TOUS les boutons à chaque bascule (celui qui se coche ET
     * ceux qui se décochent, voir la doc de `MaterialButtonToggleGroup`) — filtrer sur
     * `isChecked == true` évite de traiter l'événement en double. */
    private fun setUpTabGroup(binding: FragmentAccountsBinding) {
        binding.accountsTabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val tab = when (checkedId) {
                R.id.btnBankCards -> AccountsDisplayTab.BANK_CARDS
                R.id.btnPlanning -> AccountsDisplayTab.PLANNING
                else -> AccountsDisplayTab.ACCOUNTS
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

    /** Voir [AccountsViewModel.financialPlans] — même principe que [render] ci-dessus, pour
     * l'onglet Planification. */
    private fun renderFinancialPlans(state: AppResult<List<FinancialPlanUiItem>>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return
        latestFinancialPlans = state.data
        renderList(binding)
    }

    /** Synchronise le bouton coché (utile si l'onglet a été mémorisé par le ViewModel avant que
     * la vue ne soit recréée, ex. retour depuis "Détail du compte") — TOUJOURS immédiat, c'est le
     * retour visuel standard d'un ToggleGroup au moment même du tap. Le reste (libellé du bouton
     * d'ajout, texte de l'état vide, contenu de la liste, voir [applyTabContent]) est en revanche
     * différé au creux du fondu lors d'un changement d'onglet réel (voir [lastRenderedTab]),
     * jamais au tout premier affichage — sans ce report, ces éléments changeaient jusqu'ici
     * instantanément, AVANT même que l'ancien contenu ait commencé à disparaître, provoquant un
     * flash visible (bug signalé : "erreur d'animation" sur Comptes/Cartes bancaires, les deux
     * onglets qui partagent `emptyState`). */
    private fun renderTab(binding: FragmentAccountsBinding, tab: AccountsDisplayTab) {
        val checkedId = when (tab) {
            AccountsDisplayTab.BANK_CARDS -> R.id.btnBankCards
            AccountsDisplayTab.PLANNING -> R.id.btnPlanning
            AccountsDisplayTab.ACCOUNTS -> R.id.btnAccounts
        }
        if (binding.accountsTabGroup.checkedButtonId != checkedId) {
            binding.accountsTabGroup.check(checkedId)
        }

        val previousTab = lastRenderedTab
        lastRenderedTab = tab
        if (previousTab == null || previousTab == tab) {
            applyTabContent(binding, tab)
        } else {
            // Sens de l'animation = sens de lecture des onglets (voir la doc de
            // AccountsDisplayTab/animateTabSwitch) : Comptes(0) → Cartes bancaires(1) →
            // Planification(2), dans l'ordre de déclaration de l'enum, réutilisé tel quel comme
            // position plutôt qu'une table de positions séparée à maintenir en double.
            animateTabSwitch(binding, tab, forward = tab.ordinal > previousTab.ordinal)
        }
    }

    /**
     * Tout ce qui doit changer "en même temps" pour un onglet donné : libellé du bouton d'ajout,
     * texte de l'état vide (Comptes/Cartes bancaires uniquement — l'état vide de Planification a
     * son propre texte fixe, voir `fragment_accounts.xml`), puis le contenu de la liste elle-même
     * (voir [renderList]). Appelé soit immédiatement (premier affichage, voir [renderTab]), soit
     * au creux du fondu (voir [animateTabSwitch]) — jamais entre les deux, pour qu'aucun de ces
     * changements ne soit visible avant que l'ancien contenu ait fini de disparaître.
     */
    private fun applyTabContent(binding: FragmentAccountsBinding, tab: AccountsDisplayTab) {
        updateAddButtonLabel(binding, tab)
        if (tab != AccountsDisplayTab.PLANNING) {
            binding.emptyState.text = getString(
                if (tab == AccountsDisplayTab.BANK_CARDS) {
                    R.string.accounts_empty_bank_cards_message
                } else {
                    R.string.accounts_empty_message
                }
            )
        }
        renderList(binding)
    }

    /**
     * Libellé visible de [addAccountButton] selon l'onglet (voir cahier des charges) — le bouton
     * lui-même reste unique pour les 3 onglets (voir [navigateToForm]), seul son texte change.
     * `ExtendedFloatingActionButton` n'a jamais été rétréci en icône seule ici (pas de
     * `.shrink()`/comportement de défilement, ce FAB vit dans un `ConstraintLayout`, pas un
     * `CoordinatorLayout`) : une simple affectation de `text` suffit, sans `.extend()`.
     */
    private fun updateAddButtonLabel(binding: FragmentAccountsBinding, tab: AccountsDisplayTab) {
        binding.addAccountButton.text = getString(
            when (tab) {
                AccountsDisplayTab.BANK_CARDS -> R.string.accounts_add_action_bank_cards
                AccountsDisplayTab.PLANNING -> R.string.financial_plan_form_title_add
                AccountsDisplayTab.ACCOUNTS -> R.string.accounts_add_action_accounts
            }
        )
    }

    /**
     * Fondu enchaîné + léger glissement horizontal DIRECTIONNEL (voir cahier des charges : "fade +
     * léger slide horizontal, 200-300ms, sens intelligent selon la position de l'onglet") lors
     * d'un changement d'onglet réel. `accountsContent` (voir `fragment_accounts.xml`) regroupe
     * TOUTES les vues des 3 onglets (`accountsList`/`emptyState` pour Comptes/Cartes bancaires,
     * `planningList`/`planningEmptyState` pour Planification) : un seul alpha/translationX à
     * animer, pas plusieurs animations à coordonner sur des vues normalement mutuellement
     * exclusives. [applyTabContent] (qui recalcule juste un filtre/état en mémoire, aucun accès
     * disque/réseau — voir aussi le libellé du bouton d'ajout, hors de `accountsContent` mais
     * changé au même instant pour rester synchronisé) s'exécute pendant le creux du fondu, jamais
     * visible pour l'utilisateur tant que l'ancien contenu n'a pas fini de disparaître.
     *
     * [forward] : `true` quand on avance dans l'ordre des onglets (Comptes → Cartes bancaires →
     * Planification, voir [renderTab]) — l'ancien contenu sort vers la GAUCHE et le nouveau entre
     * depuis la DROITE ; `false` (on revient en arrière) inverse les deux sens. Même durée dans les
     * deux cas (les deux moitiés cumulées, 220ms, restent dans la fourchette 200-300ms demandée).
     */
    private fun animateTabSwitch(binding: FragmentAccountsBinding, tab: AccountsDisplayTab, forward: Boolean) {
        val content = binding.accountsContent
        content.animate().cancel()
        val slideDistance = resources.getDimension(R.dimen.spacing_m)
        val exitTranslation = if (forward) -slideDistance else slideDistance
        val enterFromTranslation = if (forward) slideDistance else -slideDistance
        content.animate()
            .alpha(0f)
            .translationX(exitTranslation)
            .setDuration(TAB_SWITCH_FADE_OUT_MS)
            .withEndAction {
                applyTabContent(binding, tab)
                content.translationX = enterFromTranslation
                content.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(TAB_SWITCH_FADE_IN_MS)
                    .start()
            }
            .start()
    }

    /**
     * Filtre/affiche le contenu de l'onglet actuellement sélectionné à partir des données déjà en
     * mémoire ([latestAccounts]/[latestFinancialPlans]) — aucun rechargement. L'onglet
     * Planification masque ENTIÈREMENT `accountsList`/`emptyState` (et inversement) : ce ne sont
     * pas des vues qu'on refiltre en commun, contrairement à Comptes/Cartes bancaires qui
     * partagent la même liste de comptes (voir [AccountUiItem.matchesTab]).
     */
    private fun renderList(binding: FragmentAccountsBinding) {
        val tab = viewModel.selectedTab.value
        if (tab == AccountsDisplayTab.PLANNING) {
            binding.accountsList.visibility = View.GONE
            binding.emptyState.visibility = View.GONE
            val hasPlans = latestFinancialPlans.isNotEmpty()
            binding.planningList.visibility = if (hasPlans) View.VISIBLE else View.GONE
            binding.planningEmptyState.visibility = if (hasPlans) View.GONE else View.VISIBLE
            financialPlansAdapter.submitList(latestFinancialPlans)
        } else {
            binding.planningList.visibility = View.GONE
            binding.planningEmptyState.visibility = View.GONE
            val filtered = latestAccounts.filter { it.matchesTab(tab) }
            val hasAccounts = filtered.isNotEmpty()
            binding.accountsList.visibility = if (hasAccounts) View.VISIBLE else View.GONE
            binding.emptyState.visibility = if (hasAccounts) View.GONE else View.VISIBLE
            // PAS de mise à jour de l'adapter en plein glisser — voir la doc d'[isReordering].
            if (!isReordering) {
                adapter.submitList(filtered)
            }
        }
    }

    /** Retour visuel discret au tout début d'un glisser (voir cahier des charges : "légère
     * animation indiquant que l'élément est sélectionné" + "effet visuel subtil pendant le
     * déplacement", ce dernier simplement en laissant l'élévation/zoom actifs jusqu'au dépôt) —
     * `elevation`/`scaleX`/`scaleY` (`ViewPropertyAnimator`, pas de dépendance à
     * `MaterialCardView.cardElevation` : une simple élévation `View` standard se superpose
     * proprement à l'ombre déjà dessinée par la carte, sans avoir à connaître son type exact
     * (`ClassicViewHolder`/`CreditCardViewHolder` partagent tous deux un `MaterialCardView` racine
     * nommé `accountCard`, mais rien n'oblige [itemView] à en être un ici). */
    private fun animateDragStart(itemView: View) {
        itemView.animate()
            .scaleX(DRAG_SCALE)
            .scaleY(DRAG_SCALE)
            .translationZ(resources.getDimension(R.dimen.elevation_raised_focused))
            .setDuration(DRAG_ANIMATION_MS)
            .start()
    }

    /** Retour à la position normale après le dépôt (voir cahier des charges) — symétrique
     * d'[animateDragStart]. */
    private fun animateDragEnd(itemView: View) {
        itemView.animate()
            .scaleX(1f)
            .scaleY(1f)
            .translationZ(0f)
            .setDuration(DRAG_ANIMATION_MS)
            .start()
    }

    /**
     * `initialType` (voir `nav_graph.xml`) : présélectionne "Carte de crédit" quand on ajoute
     * depuis l'onglet "Cartes bancaires", sinon `null` (comportement inchangé — l'utilisateur
     * choisit lui-même le type, comme avant l'ajout du ToggleGroup). Toujours `accountId = 0L`
     * (nouveau compte) : ce bouton ne sert qu'à l'ajout, jamais à l'édition.
     *
     * Depuis l'onglet Planification, ce même bouton ouvre [R.id.financialPlanFormFragment] à la
     * place (voir aussi [navigateToFinancialPlanForm], appelée à l'identique par
     * `planningEmptyAction`) : un seul bouton d'ajout pour les 3 onglets, sa destination s'adapte
     * plutôt que d'en dupliquer un par onglet.
     */
    private fun navigateToForm() {
        if (viewModel.selectedTab.value == AccountsDisplayTab.PLANNING) {
            navigateToFinancialPlanForm()
            return
        }
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

    /** Clic sur une carte de planification : ouvre son détail — même destination/navigation que
     * [com.arzikina.ne.presentation.utilities.financialplan.FinancialPlansFragment.navigateToDetail]
     * (écran dédié), aucune logique de navigation propre à cet onglet. */
    private fun navigateToFinancialPlanDetail(planId: Long) {
        findNavController().navigate(R.id.financialPlanDetailFragment, bundleOf("planId" to planId), NavAnimations.push)
    }

    /** `planId` par défaut (0L, voir `nav_graph.xml`) : toujours une création depuis cet onglet,
     * jamais une édition — même appel que l'ancien bloc "Mes planifications" du Dashboard. */
    private fun navigateToFinancialPlanForm() {
        findNavController().navigate(R.id.financialPlanFormFragment, null, NavAnimations.push)
    }

    private companion object {
        /** Voir [animateTabSwitch] — les deux moitiés cumulées (220ms) restent dans la fourchette
         * 200-250ms demandée pour la transition d'onglet. */
        const val TAB_SWITCH_FADE_OUT_MS = 110L
        const val TAB_SWITCH_FADE_IN_MS = 110L

        /** Voir [animateDragStart]/[animateDragEnd] — léger zoom (3%), à peine perceptible mais
         * suffisant pour signaler la sélection, cohérent avec la contrainte "animations discrètes"
         * du projet (voir aussi `AuthAnimations.PRESS_SCALE`, même ordre de grandeur). */
        const val DRAG_SCALE = 1.03f
        const val DRAG_ANIMATION_MS = 150L
    }
}
