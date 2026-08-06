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
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.AndroidEntryPoint
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

    private fun render(state: AppResult<List<BudgetUiItem>>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return

        val hasBudgets = state.data.isNotEmpty()
        binding.budgetsList.visibility = if (hasBudgets) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasBudgets) View.GONE else View.VISIBLE
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
        findNavController().navigate(R.id.budgetFormFragment, bundleOf("budgetId" to budgetId))
    }
}
