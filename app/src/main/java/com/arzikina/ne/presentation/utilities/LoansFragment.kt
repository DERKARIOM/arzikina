package com.arzikina.ne.presentation.utilities

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
import com.arzikina.ne.databinding.FragmentLoansBinding
import com.arzikina.ne.presentation.components.NavAnimations
import com.arzikina.ne.presentation.utilities.loans.LoanFilters
import com.arzikina.ne.presentation.utilities.loans.LoanListItem
import com.arzikina.ne.presentation.utilities.loans.LoanStatusFilterOption
import com.arzikina.ne.presentation.utilities.loans.LoanTypeFilterOption
import com.arzikina.ne.presentation.utilities.loans.LoansAdapter
import com.arzikina.ne.presentation.utilities.loans.LoansListRow
import com.arzikina.ne.presentation.utilities.loans.LoansUiState
import com.arzikina.ne.presentation.utilities.loans.LoansViewModel
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Écran principal Prêts/Emprunts (voir maquette fournie). Remplace le placeholder "en cours de
 * développement" — id de destination `loansFragment` inchangé (voir nav_graph.xml).
 *
 * Ajout d'un prêt/emprunt (FAB / bouton de l'état vide) ouvre le formulaire (voir
 * `presentation/utilities/loans/LoanFormFragment`). Clic sur une carte ouvre l'écran de détail
 * (voir `presentation/utilities/loans/LoanDetailFragment`).
 *
 * Recherche + filtres (voir `LoansViewModel.LoanFilters`) : panneau masqué par défaut, basculé par
 * l'action "Filtres" de la Toolbar — même structure que `TransactionsFragment` (voir sa doc).
 * Recherche/panneau de filtres masqués tant qu'il n'existe aucun prêt/emprunt DU TOUT (voir
 * `hasAnyLoans` dans [render]), pour ne jamais présenter une barre de recherche sans rien à
 * chercher. Un filtrage qui ne retourne aucun résultat affiche `LoansListRow.NoResults` EN PLUS du
 * `Header` (voir `LoansAdapter`) plutôt que l'état vide illustré `emptyState` (réservé à l'absence
 * totale de prêts/emprunts) : les totaux "Total reçu"/"Total dû" restent ainsi visibles même quand
 * le filtre courant ne retourne rien.
 */
@AndroidEntryPoint
class LoansFragment : Fragment(R.layout.fragment_loans) {

    private val viewModel: LoansViewModel by viewModels()
    private var binding: FragmentLoansBinding? = null
    private val adapter = LoansAdapter(onLoanClick = { item -> navigateToLoanDetail(item) })

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentLoansBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        viewBinding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_toggle_filters -> {
                    toggleFiltersPanel(viewBinding)
                    true
                }
                R.id.action_view_statistics -> {
                    navigateToStatistics()
                    true
                }
                else -> false
            }
        }
        viewBinding.loansList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.loansList.adapter = adapter
        viewBinding.addLoanButton.setOnClickListener { navigateToLoanForm() }
        viewBinding.emptyAddLoanButton.setOnClickListener { navigateToLoanForm() }
        viewBinding.resetFiltersButton.setOnClickListener { viewModel.resetFilters() }
        setUpSearch(viewBinding)
        setUpChips(viewBinding)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state -> render(state) } }
                launch { viewModel.filters.collect { filters -> renderFilters(filters) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun toggleFiltersPanel(binding: FragmentLoansBinding) {
        binding.filtersPanel.visibility = if (binding.filtersPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun setUpSearch(binding: FragmentLoansBinding) {
        binding.searchInput.doAfterTextChanged { text -> viewModel.onQueryChange(text?.toString().orEmpty()) }
    }

    private fun setUpChips(binding: FragmentLoansBinding) {
        binding.typeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val type = when (checkedIds.firstOrNull()) {
                R.id.typeChipLent -> LoanTypeFilterOption.LENT
                R.id.typeChipBorrowed -> LoanTypeFilterOption.BORROWED
                else -> LoanTypeFilterOption.ALL
            }
            if (type != viewModel.filters.value.type) {
                viewModel.onTypeFilterChange(type)
            }
        }
        binding.statusChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val status = when (checkedIds.firstOrNull()) {
                R.id.statusChipOngoing -> LoanStatusFilterOption.ONGOING
                R.id.statusChipRepaid -> LoanStatusFilterOption.REPAID
                R.id.statusChipOverdue -> LoanStatusFilterOption.OVERDUE
                R.id.statusChipUpcoming -> LoanStatusFilterOption.UPCOMING
                else -> LoanStatusFilterOption.ALL
            }
            if (status != viewModel.filters.value.status) {
                viewModel.onStatusFilterChange(status)
            }
        }
    }

    private fun render(state: AppResult<LoansUiState>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return
        val uiState = state.data

        // Voir la doc de la classe : distingue "aucun prêt/emprunt du tout" de "la recherche/le
        // filtre actuel ne retourne rien" — deux états vides différents, un seul ne suffit pas.
        val hasAnyLoans = uiState.summary.totalCount > 0
        val hasFilteredResults = uiState.items.isNotEmpty()

        binding.searchLayout.visibility = if (hasAnyLoans) View.VISIBLE else View.GONE
        binding.toolbar.menu.findItem(R.id.action_toggle_filters)?.isVisible = hasAnyLoans
        // Une répartition/un classement vides n'auraient rien à montrer (voir LoanStatisticsFragment) :
        // même raisonnement que pour "Filtres" ci-dessus.
        binding.toolbar.menu.findItem(R.id.action_view_statistics)?.isVisible = hasAnyLoans
        if (!hasAnyLoans) binding.filtersPanel.visibility = View.GONE

        binding.addLoanButton.visibility = if (hasAnyLoans) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasAnyLoans) View.GONE else View.VISIBLE
        binding.loansList.visibility = if (hasAnyLoans) View.VISIBLE else View.GONE

        // Le Header (cartes de résumé) reste toujours en tête de liste tant qu'il existe au moins
        // un prêt/emprunt — y compris quand la recherche/le filtre courant n'en retourne aucun
        // (voir LoansListRow.NoResults et la doc de LoansUiState.summary, "vue d'ensemble stable").
        val rows = if (hasAnyLoans) {
            buildList {
                add(LoansListRow.Header(uiState.summary))
                if (hasFilteredResults) {
                    addAll(uiState.items.map { LoansListRow.LoanRow(it) })
                } else {
                    add(LoansListRow.NoResults)
                }
            }
        } else {
            emptyList()
        }
        adapter.submitList(rows)
    }

    private fun renderFilters(filters: LoanFilters) {
        val binding = binding ?: return
        if (binding.searchInput.text?.toString() != filters.query) {
            binding.searchInput.setText(filters.query)
        }

        val expectedTypeChip = when (filters.type) {
            LoanTypeFilterOption.ALL -> R.id.typeChipAll
            LoanTypeFilterOption.LENT -> R.id.typeChipLent
            LoanTypeFilterOption.BORROWED -> R.id.typeChipBorrowed
        }
        if (binding.typeChipGroup.checkedChipId != expectedTypeChip) {
            binding.typeChipGroup.check(expectedTypeChip)
        }

        val expectedStatusChip = when (filters.status) {
            LoanStatusFilterOption.ALL -> R.id.statusChipAll
            LoanStatusFilterOption.ONGOING -> R.id.statusChipOngoing
            LoanStatusFilterOption.REPAID -> R.id.statusChipRepaid
            LoanStatusFilterOption.OVERDUE -> R.id.statusChipOverdue
            LoanStatusFilterOption.UPCOMING -> R.id.statusChipUpcoming
        }
        if (binding.statusChipGroup.checkedChipId != expectedStatusChip) {
            binding.statusChipGroup.check(expectedStatusChip)
        }

        binding.resetFiltersButton.visibility = if (filters.hasActiveFilters) View.VISIBLE else View.GONE
    }

    private fun navigateToLoanForm() {
        findNavController().navigate(R.id.loanFormFragment, null, NavAnimations.fade)
    }

    private fun navigateToLoanDetail(item: LoanListItem) {
        findNavController().navigate(R.id.loanDetailFragment, bundleOf("loanId" to item.id), NavAnimations.fade)
    }

    private fun navigateToStatistics() {
        findNavController().navigate(R.id.loanStatisticsFragment, null, NavAnimations.fade)
    }
}
