package com.arzikina.ne.presentation.dashboard

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentDashboardBinding
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.presentation.accounts.AccountCardGradient
import com.arzikina.ne.presentation.budget.BudgetAdapter
import com.arzikina.ne.presentation.budget.BudgetUiItem
import com.arzikina.ne.presentation.components.NavAnimations
import com.arzikina.ne.presentation.components.SyncButtonEvent
import com.arzikina.ne.presentation.components.SyncIndicatorLevel
import com.arzikina.ne.presentation.components.SyncIndicatorUiState
import com.arzikina.ne.presentation.components.SyncNowUiState
import com.arzikina.ne.presentation.utilities.UtilityCatalog
import com.arzikina.ne.presentation.utilities.UtilityTileAdapter
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.Money
import coil3.load
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Écran d'accueil : solde total, revenus/dépenses du mois en cours et
 * dernières transactions. Reconstruit en XML/Views (voir instructions
 * projet) ; [DashboardViewModel] est inchangé depuis la version Compose.
 */
@AndroidEntryPoint
class DashboardFragment : Fragment(R.layout.fragment_dashboard) {

    private val viewModel: DashboardViewModel by viewModels()
    private var binding: FragmentDashboardBinding? = null
    private val recentTransactionsAdapter = RecentTransactionsAdapter()

    /**
     * État de masquage du solde, purement local à l'écran (non persisté ni
     * exposé par [DashboardViewModel]) : il s'agit d'une préférence d'affichage
     * ponctuelle, pas d'une donnée métier — elle revient à "visible" à chaque
     * ouverture de l'écran, comme dans la plupart des apps bancaires.
     */
    private var isBalanceHidden = false
    private var latestBalances: List<CurrencyAmount> = emptyList()

    /** Anime `syncButton` (voir [renderSyncButton]) pendant l'envoi — référence gardée pour pouvoir
     *  l'arrêter ([stopSyncRotation]) aussi bien en fin de synchronisation qu'à [onDestroyView]
     *  (fuite sinon : un `ObjectAnimator` infini garderait une référence à la vue). */
    private var syncRotationAnimator: ObjectAnimator? = null

    /** [UtilityCatalog.all] en intégralité pour l'instant (voir sa doc : le Dashboard affichera
     * une sélection restreinte plutôt que la totalité une fois le catalogue plus grand). Même
     * instance d'adapter réutilisée pour toute la durée de vie de la vue, comme
     * [recentTransactionsAdapter] ci-dessus — seule la pastille de comptage d'une entrée change au
     * fil du temps, via [UtilityTileAdapter.submitItems] (voir [render]), jamais l'ensemble des
     * entrées lui-même. */
    private val utilitiesAdapter = UtilityTileAdapter(UtilityCatalog.all()) { item ->
        findNavController().navigate(item.destinationId, null, NavAnimations.push)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentDashboardBinding.bind(view)
        binding = viewBinding

        // Pas de DividerItemDecoration ici : item_transaction_compact.xml (voir ce layout)
        // porte déjà sa propre ligne de séparation fine en haut de CHAQUE ligne (vue "divider"),
        // partagée avec GroupedTransactionsAdapter (écran Transactions/Détail du compte) — en
        // ajouter une seconde ici aurait doublé le trait. Mêmes lignes de séparation que
        // fragment_transactions.xml, sans rien dupliquer.
        viewBinding.recentTransactionsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentTransactionsAdapter
        }
        // Le fond dégradé de l'en-tête (dashboardHeaderBackground) s'étend
        // volontairement sous la barre de statut, désormais transparente
        // (voir MainActivity.isTopInsetTransparent) : c'est ce Fragment, et
        // non le conteneur de navigation partagé, qui absorbe cet inset —
        // en padding interne sur headerRow, pour que seul le CONTENU
        // (avatar/nom/icône) soit repoussé sous la barre, sans repousser le
        // fond avec lui.
        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.headerRow) { row, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            row.updatePadding(top = statusBarInset)
            insets
        }
        // Ce Fragment est recréé à chaque navigation vers le Dashboard, bien
        // après la première distribution d'insets de la fenêtre : sans cet
        // appel, le listener ci-dessus ne serait jamais invoqué pour cette
        // nouvelle vue.
        ViewCompat.requestApplyInsets(viewBinding.headerRow)

        // Dégradé façon carte VISA virtuelle (voir AccountCardGradient, réutilisé tel quel
        // depuis "Mes comptes") : fixe, pas issu d'un compte réel puisque cette carte
        // représente le solde TOTAL, tous comptes confondus (voir BALANCE_CARD_COLOR).
        viewBinding.balanceCard.background = AccountCardGradient.create(BALANCE_CARD_COLOR)

        // Raccourci vers Paramètres (remplace l'ancien raccourci Catégories) :
        // réutilise la destination settingsFragment déjà existante, atteignable
        // aussi depuis l'onglet "Autre".
        viewBinding.settingsShortcut.setOnClickListener {
            findNavController().navigate(R.id.settingsFragment, null, NavAnimations.push)
        }
        // Voir DashboardViewModel.syncNow / SyncButtonController : même comportement que
        // syncNowRow sur l'écran Paramètres (bouton désactivé par ce même StateFlow pendant
        // l'envoi, voir renderSyncButton), réutilisé tel quel ici.
        viewBinding.syncButton.setOnClickListener { viewModel.syncNow() }
        viewBinding.utilitiesList.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        viewBinding.utilitiesList.adapter = utilitiesAdapter
        viewBinding.utilitiesSeeAll.setOnClickListener {
            findNavController().navigate(R.id.allUtilitiesFragment, null, NavAnimations.push)
        }
        viewBinding.balanceCard.setOnClickListener {
            // accountsFromDashboardFragment (pas accountsFragment) : voir sa doc dans
            // nav_graph.xml — évite que ce raccourci fasse basculer la sélection de la
            // Bottom Navigation sur l'onglet "Compte" alors qu'on reste dans le contexte
            // "Accueil".
            findNavController().navigate(R.id.accountsFromDashboardFragment, null, NavAnimations.push)
        }
        viewBinding.toggleBalanceVisibility.setOnClickListener {
            isBalanceHidden = !isBalanceHidden
            renderBalanceText()
        }
        viewBinding.budgetSeeAll.setOnClickListener {
            findNavController().navigate(R.id.budgetFragment, null, NavAnimations.push)
        }
        // transactionsFragment n'est plus un onglet (voir bottom_nav_menu.xml) : seul point
        // d'entrée désormais, sans faire recocher aucun onglet de la Bottom Navigation (voir
        // MainActivity.TAB_DESTINATION_IDS).
        viewBinding.recentTransactionsSeeAll.setOnClickListener {
            findNavController().navigate(R.id.transactionsFragment, null, NavAnimations.push)
        }
        // Réutilise transactionFormFragment tel quel (même destination que le FAB de l'écran
        // Transactions) : aucune nouvelle logique d'ajout.
        viewBinding.recentTransactionsEmptyAction.setOnClickListener {
            findNavController().navigate(R.id.transactionFormFragment, null, NavAnimations.push)
        }
        // FAB "+" — même destination, même comportement que addTransactionButton sur
        // fragment_transactions.xml (voir TransactionsFragment.navigateToForm).
        viewBinding.addTransactionButton.setOnClickListener {
            findNavController().navigate(R.id.transactionFormFragment, null, NavAnimations.push)
        }
        viewBinding.createBudgetAction.setOnClickListener {
            findNavController().navigate(R.id.budgetFormFragment, null, NavAnimations.push)
        }
        // Pas d'action de suppression depuis cet aperçu (voir item_budget.xml, réutilisé
        // tel quel avec BudgetAdapter.ViewHolder pour ne pas dupliquer son rendu).
        viewBinding.budgetPreview.deleteButton.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state -> render(state) } }
                launch { viewModel.syncNowState.collect { state -> renderSyncButton(viewBinding, state) } }
                launch { viewModel.syncIndicatorState.collect { state -> renderSyncBadge(viewBinding, state) } }
                launch { viewModel.syncEvents.collect { event -> handleSyncEvent(viewBinding, event) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopSyncRotation()
        binding = null
    }

    /** Désactive `syncButton` pendant l'envoi (voir [DashboardViewModel.syncNow], garde de
     *  ré-entrance côté [com.arzikina.ne.presentation.components.SyncButtonController]) et anime sa
     *  rotation — voir le cahier des charges "États du bouton" (C. Synchronisation en cours). */
    private fun renderSyncButton(binding: FragmentDashboardBinding, state: SyncNowUiState) {
        binding.syncButton.isEnabled = !state.isSyncing
        if (state.isSyncing) startSyncRotation(binding) else stopSyncRotation()
    }

    private fun startSyncRotation(binding: FragmentDashboardBinding) {
        if (syncRotationAnimator?.isRunning == true) return
        syncRotationAnimator = ObjectAnimator.ofFloat(binding.syncButton, View.ROTATION, 0f, 360f).apply {
            duration = 1_000L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }

    private fun stopSyncRotation() {
        syncRotationAnimator?.cancel()
        syncRotationAnimator = null
        binding?.syncButton?.rotation = 0f
    }

    /** Pastille rouge de comptage (voir le cahier des charges "Badge") : masquée si aucune session
     *  serveur n'est active ([SyncIndicatorLevel.HIDDEN]) ou si rien n'est en attente — jamais
     *  affichée à `0`. */
    private fun renderSyncBadge(binding: FragmentDashboardBinding, state: SyncIndicatorUiState) {
        val pendingCount = if (state.level == SyncIndicatorLevel.HIDDEN) 0 else state.pendingCount
        if (pendingCount > 0) {
            binding.syncBadge.text = pendingCount.toString()
            binding.syncBadge.visibility = View.VISIBLE
        } else {
            binding.syncBadge.visibility = View.GONE
        }
    }

    /** Même message que `SettingsFragment.handleEvent` (voir `settings_sync_now_*`) : réutilisés
     *  tels quels plutôt que dupliqués pour ce second bouton "Synchroniser maintenant". */
    private fun handleSyncEvent(binding: FragmentDashboardBinding, event: SyncButtonEvent) {
        val message = when (event) {
            is SyncButtonEvent.SyncFinished -> when {
                event.pushResult.pushed == 0 && event.pullResult.received == 0 ->
                    getString(R.string.settings_sync_now_nothing_pending)
                else -> getString(
                    R.string.settings_sync_now_result,
                    event.pushResult.succeeded,
                    event.pushResult.failed,
                    event.pullResult.applied
                )
            }
            is SyncButtonEvent.SyncError -> getString(R.string.settings_sync_now_error)
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun render(state: AppResult<DashboardUiState>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return
        val uiState = state.data

        latestBalances = uiState.balances
        renderBalanceText()
        renderIncomeExpense(uiState.monthlyIncome, uiState.monthlyExpense, uiState.budgetGap)
        renderFeaturedBudget(uiState.featuredBudget)
        renderUserHeader(uiState.userFullName, uiState.userProfilePhotoUri)
        binding.cardNumberText.text = getString(R.string.dashboard_card_number_format, uiState.cardNumberLastDigits)
        binding.cardHolderNameText.text = uiState.userFullName.uppercase(Locale.FRENCH)

        val hasTransactions = uiState.recentTransactions.isNotEmpty()
        binding.recentTransactionsList.setVisible(hasTransactions)
        binding.recentTransactionsEmpty.setVisible(!hasTransactions)
        recentTransactionsAdapter.submitList(uiState.recentTransactions)

        renderUtilities(uiState.pendingRecurringCount)
    }

    /** Reconstruit [UtilityCatalog.all] avec la pastille de comptage à jour sur l'entrée
     * "Transactions planifiées" (voir [DashboardUiState.pendingRecurringCount]) — les 3 autres
     * entrées gardent [com.arzikina.ne.presentation.utilities.UtilityItem.badgeCount] à `null`
     * (masqué), voir sa doc. */
    private fun renderUtilities(pendingRecurringCount: Int) {
        val items = UtilityCatalog.all().map { item ->
            if (item.destinationId == R.id.recurringTransactionsFragment) {
                item.copy(badgeCount = pendingRecurringCount)
            } else {
                item
            }
        }
        utilitiesAdapter.submitItems(items)
    }

    /**
     * [item] est `null` si aucun budget n'existe encore (voir
     * [DashboardViewModel.featuredBudget]) : on affiche alors une invite de
     * création plutôt qu'une carte vide.
     */
    private fun renderFeaturedBudget(item: BudgetUiItem?) {
        val binding = binding ?: return
        binding.budgetPreviewCard.setVisible(item != null)
        binding.budgetEmptyState.setVisible(item == null)
        if (item != null) {
            BudgetAdapter.ViewHolder(binding.budgetPreview).bind(
                item = item,
                onClick = { findNavController().navigate(R.id.budgetFragment, null, NavAnimations.push) },
                onDeleteClick = {}
            )
        }
    }

    /**
     * En-tête (avatar + "Salut !" + nom). `userAvatarImage` (la photo) et `userAvatarPlaceholder`
     * (l'icône de repli) sont deux vues séparées de taille FIXE (voir fragment_dashboard.xml,
     * `userAvatarContainer`) — seule leur visibilité bascule ici, jamais leurs
     * dimensions/padding/tint. Corrige un bug où la photo s'affichait trop petite au premier rendu
     * puis correctement après une interaction : l'ancienne version réutilisait la même
     * ShapeableImageView pour la photo ET le placeholder en modifiant son padding/tint par code
     * selon l'état, rendant sa taille effective dépendante du moment où ce code s'exécutait par
     * rapport au rendu (même correctif que
     * [com.arzikina.ne.presentation.profile.ProfileFragment.renderPhoto] et
     * [com.arzikina.ne.presentation.settings.SettingsFragment.render]).
     */
    private fun renderUserHeader(fullName: String, photoUri: String?) {
        val binding = binding ?: return
        binding.userFullNameText.text = fullName
        if (photoUri != null) {
            binding.userAvatarPlaceholder.visibility = View.GONE
            binding.userAvatarImage.load(photoUri)
        } else {
            binding.userAvatarPlaceholder.visibility = View.VISIBLE
            binding.userAvatarImage.setImageDrawable(null)
        }
    }

    /**
     * Alimente le mini graphique en barres et le texte Revenu/Dépense/Différence/Écart Budget.
     *
     * Limite documentée : ne prend en compte que la première devise de chaque
     * liste (comme le graphique n'affiche qu'une seule paire de barres) — si
     * l'utilisateur détient des comptes en plusieurs devises, seule la
     * première est représentée ici. Le texte [formatAmounts], lui, continue
     * d'afficher toutes les devises (une par ligne) pour rester correct dans
     * ce cas, au prix d'un léger désaccord visuel avec le graphique.
     *
     * [budgetGap] est calculé séparément par [DashboardViewModel] (déjà dans une seule devise —
     * voir sa doc) : affiché tel quel via [Money.format], pas recalculé ici. Couleur dynamique
     * (vert/rouge selon le signe, mêmes couleurs que Revenu/Dépense juste au-dessus) — voir
     * cahier des charges "Écart Budget" : permet de voir en un coup d'œil si le solde total
     * dépasse ou non la somme des montants restants des budgets actifs.
     */
    private fun renderIncomeExpense(income: List<CurrencyAmount>, expense: List<CurrencyAmount>, budgetGap: CurrencyAmount) {
        val binding = binding ?: return
        binding.incomeValue.text = formatAmounts(income)
        binding.expenseValue.text = formatAmounts(expense)

        val incomeAmount = income.firstOrNull()?.amountMinor ?: 0L
        val expenseAmount = expense.firstOrNull()?.amountMinor ?: 0L
        binding.incomeExpenseChart.income = incomeAmount
        binding.incomeExpenseChart.expense = expenseAmount

        val currencyCode = income.firstOrNull()?.currencyCode
            ?: expense.firstOrNull()?.currencyCode
            ?: Constants.DEFAULT_CURRENCY_CODE
        val difference = incomeAmount - expenseAmount
        binding.differenceValue.text = Money.format(CurrencyAmount(currencyCode, difference))
        binding.differenceValue.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (difference < 0L) R.color.expense_red else R.color.arzikina_on_balance_card
            )
        )

        binding.budgetGapValue.text = Money.format(budgetGap)
        binding.budgetGapValue.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (budgetGap.amountMinor < 0L) R.color.expense_red else R.color.income_green
            )
        )
    }

    /**
     * Affiche [latestBalances] ou un texte masqué selon [isBalanceHidden], et met
     * à jour l'icône/le texte accessible du bouton en conséquence. Séparée de
     * [render] pour pouvoir être rappelée seule depuis le clic sur l'œil, sans
     * attendre une nouvelle émission de [DashboardViewModel.uiState].
     */
    private fun renderBalanceText() {
        val binding = binding ?: return
        binding.balanceValue.text = if (isBalanceHidden) {
            getString(R.string.dashboard_balance_masked)
        } else {
            formatAmounts(latestBalances)
        }
        binding.toggleBalanceVisibility.setImageResource(
            if (isBalanceHidden) R.drawable.ic_visibility_off_24 else R.drawable.ic_visibility_24
        )
        binding.toggleBalanceVisibility.contentDescription = getString(
            if (isBalanceHidden) R.string.dashboard_balance_show_action else R.string.dashboard_balance_hide_action
        )
    }

    /** Une ligne par devise détenue (voir [DashboardUiState]) ; "—" si aucun compte encore. */
    private fun formatAmounts(amounts: List<CurrencyAmount>): String =
        if (amounts.isEmpty()) "—" else amounts.joinToString("\n") { Money.format(it) }

    private fun View.setVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }

    private companion object {
        /**
         * Base du dégradé de la carte Solde (voir [AccountCardGradient]) — même
         * valeur que `@color/arzikina_primary`, dupliquée ici en `Long` plutôt que
         * lue depuis les ressources : cette carte représente le solde TOTAL, tous
         * comptes confondus, donc une couleur fixe de l'app plutôt que celle d'un
         * compte réel (voir commentaire sur balanceCard dans fragment_dashboard.xml).
         */
        const val BALANCE_CARD_COLOR = 0xFF42B998L
    }
}
