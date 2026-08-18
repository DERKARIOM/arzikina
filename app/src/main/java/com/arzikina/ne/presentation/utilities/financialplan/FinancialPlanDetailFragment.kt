package com.arzikina.ne.presentation.utilities.financialplan

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
import com.arzikina.ne.databinding.FragmentFinancialPlanDetailBinding
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.FinancialPlanItem
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.presentation.components.NavAnimations
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.Money
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Détail d'une planification, atteint en cliquant sur une carte de [FinancialPlansFragment] — voir
 * [FinancialPlanDetailViewModel]. Même squelette que
 * [com.arzikina.ne.presentation.utilities.loans.LoanDetailFragment] (Toolbar avec menu Modifier/
 * Supprimer + un seul RecyclerView en-tête/lignes + FAB).
 *
 * "Modifier" (menu) ouvre [FinancialPlanFormFragment] (édition de la planification elle-même :
 * nom/montant/icône/couleur). "+ Ajouter une dépense" ouvre [FinancialPlanItemFormFragment] en
 * création ; cliquer sur une ligne de dépense prévue l'ouvre en ÉDITION (`itemId` non nul, voir
 * Étape 5 — catégorie/description/date/priorité/état).
 */
@AndroidEntryPoint
class FinancialPlanDetailFragment : Fragment(R.layout.fragment_financial_plan_detail) {

    private val viewModel: FinancialPlanDetailViewModel by viewModels()
    private var binding: FragmentFinancialPlanDetailBinding? = null
    private val adapter = FinancialPlanDetailAdapter(
        onClickItem = { item -> navigateToItemForm(itemId = item.id) },
        onDeleteItem = { item -> confirmDeleteItem(item) }
    )

    /** Voir `LoanDetailFragment.hasNavigatedAwayOnError` pour le même raisonnement. */
    private var hasNavigatedAwayOnError = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentFinancialPlanDetailBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        viewBinding.detailList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.detailList.adapter = adapter
        viewBinding.addItemButton.setOnClickListener { navigateToItemForm(itemId = 0L) }

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

    private fun setUpToolbar(binding: FragmentFinancialPlanDetailBinding) {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.inflateMenu(R.menu.financial_plan_detail_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit_plan -> {
                    navigateToEditForm()
                    true
                }
                R.id.action_delete_plan -> {
                    confirmDelete()
                    true
                }
                else -> false
            }
        }
    }

    private fun render(state: AppResult<FinancialPlanDetailUiState>) {
        val binding = binding ?: return
        // La planification affichée a été supprimée depuis un autre écran (voir la doc de
        // FinancialPlanDetailViewModel.uiState) : revient en arrière plutôt que de rester figé.
        if (state is AppResult.Error) {
            if (!hasNavigatedAwayOnError) {
                hasNavigatedAwayOnError = true
                Snackbar.make(binding.root, R.string.financial_plan_detail_not_found_message, Snackbar.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
            return
        }
        if (state !is AppResult.Success) return
        val uiState = state.data

        val rows = buildList {
            add(FinancialPlanDetailListRow.Header(uiState))
            addAll(uiState.items.map { item -> FinancialPlanDetailListRow.ItemRow(item) })
        }
        adapter.submitList(rows)
    }

    private fun confirmDelete() {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.financial_plans_delete_title),
            message = getString(R.string.financial_plans_delete_message),
            onConfirm = {
                viewModel.deletePlan()
                findNavController().navigateUp()
            }
        )
    }

    private fun confirmDeleteItem(item: FinancialPlanItem) {
        val amountLabel = Money.format(CurrencyAmount(Constants.DEFAULT_CURRENCY_CODE, item.amount))
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.financial_plan_item_delete_title),
            message = getString(R.string.financial_plan_item_delete_message, item.name, amountLabel),
            onConfirm = { viewModel.deleteItem(item.id) }
        )
    }

    private fun navigateToEditForm() {
        findNavController().navigate(R.id.financialPlanFormFragment, bundleOf("planId" to viewModel.planId), NavAnimations.push)
    }

    private fun navigateToItemForm(itemId: Long) {
        findNavController().navigate(
            R.id.financialPlanItemFormFragment,
            bundleOf("planId" to viewModel.planId, "itemId" to itemId),
            NavAnimations.push
        )
    }
}
