package com.arzikina.ne.presentation.dashboard

import android.os.Bundle
import android.view.View
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
import com.arzikina.ne.presentation.utilities.UtilityCatalog
import com.arzikina.ne.presentation.utilities.UtilityTileAdapter
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.Money
import coil3.load
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

    /** [UtilityCatalog.all] en intégralité pour l'instant (voir sa doc : le Dashboard affichera
     * une sélection restreinte plutôt que la totalité une fois le catalogue plus grand). Même
     * instance d'adapter réutilisée pour toute la durée de vie de la vue, comme
     * [recentTransactionsAdapter] ci-dessus. */
    private val utilitiesAdapter = UtilityTileAdapter(UtilityCatalog.all()) { item ->
        findNavController().navigate(item.destinationId)
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

        viewBinding.categoriesShortcut.setOnClickListener {
            findNavController().navigate(R.id.categoriesFragment)
        }
        viewBinding.utilitiesList.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        viewBinding.utilitiesList.adapter = utilitiesAdapter
        viewBinding.utilitiesSeeAll.setOnClickListener {
            findNavController().navigate(R.id.allUtilitiesFragment)
        }
        viewBinding.balanceCard.setOnClickListener {
            // accountsFromDashboardFragment (pas accountsFragment) : voir sa doc dans
            // nav_graph.xml — évite que ce raccourci fasse basculer la sélection de la
            // Bottom Navigation sur l'onglet "Compte" alors qu'on reste dans le contexte
            // "Accueil".
            findNavController().navigate(R.id.accountsFromDashboardFragment)
        }
        viewBinding.toggleBalanceVisibility.setOnClickListener {
            isBalanceHidden = !isBalanceHidden
            renderBalanceText()
        }
        viewBinding.budgetSeeAll.setOnClickListener {
            findNavController().navigate(R.id.budgetFragment)
        }
        // transactionsFragment n'est plus un onglet (voir bottom_nav_menu.xml) : seul point
        // d'entrée désormais, sans faire recocher aucun onglet de la Bottom Navigation (voir
        // MainActivity.TAB_DESTINATION_IDS).
        viewBinding.recentTransactionsSeeAll.setOnClickListener {
            findNavController().navigate(R.id.transactionsFragment)
        }
        // Réutilise transactionFormFragment tel quel (même destination que le FAB de l'écran
        // Transactions) : aucune nouvelle logique d'ajout.
        viewBinding.recentTransactionsEmptyAction.setOnClickListener {
            findNavController().navigate(R.id.transactionFormFragment)
        }
        // FAB "+" — même destination, même comportement que addTransactionButton sur
        // fragment_transactions.xml (voir TransactionsFragment.navigateToForm).
        viewBinding.addTransactionButton.setOnClickListener {
            findNavController().navigate(R.id.transactionFormFragment)
        }
        viewBinding.createBudgetAction.setOnClickListener {
            findNavController().navigate(R.id.budgetFormFragment)
        }
        // Pas d'action de suppression depuis cet aperçu (voir item_budget.xml, réutilisé
        // tel quel avec BudgetAdapter.ViewHolder pour ne pas dupliquer son rendu).
        viewBinding.budgetPreview.deleteButton.visibility = View.GONE

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

    private fun render(state: AppResult<DashboardUiState>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return
        val uiState = state.data

        latestBalances = uiState.balances
        renderBalanceText()
        renderIncomeExpense(uiState.monthlyIncome, uiState.monthlyExpense)
        renderFeaturedBudget(uiState.featuredBudget)
        renderUserHeader(uiState.userFullName, uiState.userProfilePhotoUri)
        binding.cardNumberText.text = getString(R.string.dashboard_card_number_format, uiState.cardNumberLastDigits)
        binding.cardHolderNameText.text = uiState.userFullName.uppercase(Locale.FRENCH)

        val hasTransactions = uiState.recentTransactions.isNotEmpty()
        binding.recentTransactionsList.setVisible(hasTransactions)
        binding.recentTransactionsEmpty.setVisible(!hasTransactions)
        recentTransactionsAdapter.submitList(uiState.recentTransactions)
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
                onClick = { findNavController().navigate(R.id.budgetFragment) },
                onDeleteClick = {}
            )
        }
    }

    /**
     * En-tête (avatar + "Salut !" + nom) : [photoUri] `null` conserve le
     * placeholder [R.drawable.ic_person_24] déjà posé dans le layout (même
     * pattern que RegisterFragment/ProfileFragment, voir bg_avatar_circle).
     */
    private fun renderUserHeader(fullName: String, photoUri: String?) {
        val binding = binding ?: return
        binding.userFullNameText.text = fullName
        if (photoUri != null) {
            binding.userAvatarImage.load(photoUri)
        }
    }

    /**
     * Alimente le mini graphique en barres et le texte Revenu/Dépense/Différence.
     *
     * Limite documentée : ne prend en compte que la première devise de chaque
     * liste (comme le graphique n'affiche qu'une seule paire de barres) — si
     * l'utilisateur détient des comptes en plusieurs devises, seule la
     * première est représentée ici. Le texte [formatAmounts], lui, continue
     * d'afficher toutes les devises (une par ligne) pour rester correct dans
     * ce cas, au prix d'un léger désaccord visuel avec le graphique.
     */
    private fun renderIncomeExpense(income: List<CurrencyAmount>, expense: List<CurrencyAmount>) {
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
