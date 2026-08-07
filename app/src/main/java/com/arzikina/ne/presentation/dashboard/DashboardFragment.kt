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
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentDashboardBinding
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.presentation.budget.BudgetAdapter
import com.arzikina.ne.presentation.budget.BudgetUiItem
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.Money
import coil3.load
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentDashboardBinding.bind(view)
        binding = viewBinding

        viewBinding.recentTransactionsList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentTransactionsAdapter
            // Ligne fine entre chaque transaction (voir drawable/divider_on_balance_card.xml) ;
            // pas de divider après le dernier élément (comportement par défaut de DividerItemDecoration).
            addItemDecoration(
                DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL).apply {
                    setDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.divider_on_balance_card)!!)
                }
            )
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

        viewBinding.categoriesShortcut.setOnClickListener {
            findNavController().navigate(R.id.categoriesFragment)
        }
        viewBinding.balanceCard.setOnClickListener {
            findNavController().navigate(R.id.accountsFragment)
        }
        viewBinding.toggleBalanceVisibility.setOnClickListener {
            isBalanceHidden = !isBalanceHidden
            renderBalanceText()
        }
        viewBinding.budgetSeeAll.setOnClickListener {
            findNavController().navigate(R.id.budgetFragment)
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
}
