package com.naniger.arzikina.presentation.budget

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentBudgetBinding
import com.naniger.arzikina.presentation.components.NavAnimations
import com.naniger.arzikina.util.AppResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Liste des budgets avec leur progression sur la période en cours.
 * Reconstruite en XML/Views (voir instructions projet) ; [BudgetViewModel]
 * est inchangé.
 *
 * [BudgetAdapter] : le MÊME PostCard que l'aperçu des budgets du Dashboard (`item_budget.xml` dans
 * la même carte, voir `item_budget_card.xml`), pour une apparence identique entre les deux écrans.
 * Toucher la carte ouvre le formulaire (`navigateToForm`), où se trouve aussi la suppression
 * (menu Supprimer, avec confirmation) : la carte n'a pas de bouton corbeille.
 */
@AndroidEntryPoint
class BudgetFragment : Fragment(R.layout.fragment_budget) {

    private val viewModel: BudgetViewModel by viewModels()
    private var binding: FragmentBudgetBinding? = null
    private val adapter = BudgetAdapter(
        onClick = { item -> navigateToForm(item.budget.id) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentBudgetBinding.bind(view)
        binding = viewBinding

        viewBinding.budgetsList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.budgetsList.adapter = adapter
        // Pas d'animateur d'item : son fondu « changement » ferait clignoter chaque carte à chaque
        // nouvelle valeur émise (progression recalculée après une transaction), même correctif que
        // AccountsFragment.
        viewBinding.budgetsList.itemAnimator = null
        viewBinding.addBudgetButton.setOnClickListener { navigateToForm(budgetId = 0L) }
        setUpStatusFilter(viewBinding)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.uiState,
                    viewModel.statusFilter
                ) { state, filter -> state to filter }
                    .collect { (state, filter) -> render(state, filter) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpStatusFilter(binding: FragmentBudgetBinding) {
        binding.statusFilterGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val filter = when (checkedId) {
                R.id.statusFilterUpcoming -> BudgetStatusFilterOption.UPCOMING
                R.id.statusFilterOngoing -> BudgetStatusFilterOption.ONGOING
                R.id.statusFilterCompleted -> BudgetStatusFilterOption.COMPLETED
                else -> BudgetStatusFilterOption.ALL
            }
            viewModel.onStatusFilterChange(filter)
        }
    }

    private fun render(state: AppResult<List<BudgetUiItem>>, filter: BudgetStatusFilterOption) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return

        val hasBudgets = state.data.isNotEmpty()
        binding.budgetsList.visibility = if (hasBudgets) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasBudgets) View.GONE else View.VISIBLE
        // Filtre actif et liste vide : message distinct de l'état vide "aucun budget du tout",
        // pour ne pas laisser penser à l'utilisateur qu'il n'a jamais créé de budget.
        binding.emptyState.text = getString(
            if (!hasBudgets && filter != BudgetStatusFilterOption.ALL) {
                R.string.budgets_empty_message_filtered
            } else {
                R.string.budgets_empty_message
            }
        )
        adapter.submitList(state.data)
    }


    private fun navigateToForm(budgetId: Long) {
        findNavController().navigate(R.id.budgetFormFragment, BudgetFormFragmentArgs(budgetId = budgetId).toBundle(), NavAnimations.push)
    }
}
