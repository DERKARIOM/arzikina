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
import com.arzikina.ne.databinding.FragmentFinancialPlansBinding
import com.arzikina.ne.presentation.components.NavAnimations
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Liste des planifications financières (voir cahier des charges "Planification financière",
 * sections 3/4) — une carte par planification (disponible/prévu/reste + progression, voir
 * PostCard dégradé de item_financial_plan.xml). Même squelette que
 * [com.arzikina.ne.presentation.budget.BudgetFragment] (Toolbar + RecyclerView + FAB + état vide).
 *
 * Clic sur une carte ouvre [FinancialPlanDetailFragment] (Étape 4) — l'édition (nom/montant/icône/
 * couleur, voir [FinancialPlanFormFragment]) ET la suppression restent accessibles depuis le menu
 * "Modifier"/"Supprimer" de cet écran de détail, même principe que
 * [com.arzikina.ne.presentation.accounts.AccountsFragment] → `AccountDetailFragment`. PLUS de
 * bouton de suppression rapide sur chaque carte (redesign PostCard sur capture de référence,
 * retiré de item_financial_plan.xml — absent de la capture).
 *
 * INDÉPENDANTE de "Automatisation" (ex-"Planification", transactions récurrentes) : aucune donnée
 * ni logique partagée, voir [FinancialPlansViewModel].
 */
@AndroidEntryPoint
class FinancialPlansFragment : Fragment(R.layout.fragment_financial_plans) {

    private val viewModel: FinancialPlansViewModel by viewModels()
    private var binding: FragmentFinancialPlansBinding? = null
    private val adapter = FinancialPlansAdapter(
        onClick = { item -> navigateToDetail(item.plan.id) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentFinancialPlansBinding.bind(view)
        binding = viewBinding

        viewBinding.plansList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.plansList.adapter = adapter
        viewBinding.addPlanButton.setOnClickListener { navigateToForm(planId = 0L) }

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

    private fun render(state: AppResult<List<FinancialPlanUiItem>>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return

        val hasPlans = state.data.isNotEmpty()
        binding.plansList.visibility = if (hasPlans) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasPlans) View.GONE else View.VISIBLE
        adapter.submitList(state.data)
    }

    private fun navigateToForm(planId: Long) {
        findNavController().navigate(R.id.financialPlanFormFragment, bundleOf("planId" to planId), NavAnimations.push)
    }

    private fun navigateToDetail(planId: Long) {
        findNavController().navigate(R.id.financialPlanDetailFragment, bundleOf("planId" to planId), NavAnimations.push)
    }
}
