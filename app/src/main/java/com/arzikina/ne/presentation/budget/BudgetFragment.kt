package com.arzikina.ne.presentation.budget

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
import com.arzikina.ne.databinding.FragmentBudgetBinding
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.presentation.components.NavAnimations
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Liste des budgets avec leur progression sur la période en cours.
 * Reconstruite en XML/Views (voir instructions projet) ; [BudgetViewModel]
 * est inchangé.
 */
@AndroidEntryPoint
class BudgetFragment : Fragment(R.layout.fragment_budget) {

    private val viewModel: BudgetViewModel by viewModels()
    private var binding: FragmentBudgetBinding? = null
    private val adapter = BudgetAdapter(
        onClick = { item -> navigateToForm(item.budget.id) },
        onDeleteClick = { item -> confirmDelete(item) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentBudgetBinding.bind(view)
        binding = viewBinding

        viewBinding.budgetsList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.budgetsList.adapter = adapter
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

    private fun confirmDelete(item: BudgetUiItem) {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.budgets_delete_title),
            message = getString(R.string.budgets_delete_message),
            onConfirm = { viewModel.deleteBudget(item.budget.id) }
        )
    }

    private fun navigateToForm(budgetId: Long) {
        findNavController().navigate(R.id.budgetFormFragment, bundleOf("budgetId" to budgetId), NavAnimations.push)
    }
}
