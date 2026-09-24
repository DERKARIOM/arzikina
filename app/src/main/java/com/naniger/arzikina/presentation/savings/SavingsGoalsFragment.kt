package com.naniger.arzikina.presentation.savings

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
import com.naniger.arzikina.databinding.FragmentSavingsGoalsBinding
import com.naniger.arzikina.presentation.components.ConfirmDialogs
import com.naniger.arzikina.presentation.components.NavAnimations
import com.naniger.arzikina.util.AppResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * « Mes objectifs d'épargne » (Utilitaires → Épargne). Voir la maquette « Arsikina — Objectifs
 * d'épargne » : synthèse, objectifs en cours puis atteints, état vide, bouton « Nouvel objectif ».
 *
 * Toucher une carte ouvre le panneau de versement « Ajouter / Retirer »
 * ([SavingsContributionBottomSheet]) ; le menu ⋮ garde Modifier / Supprimer.
 */
@AndroidEntryPoint
class SavingsGoalsFragment : Fragment(R.layout.fragment_savings_goals) {

    private val viewModel: SavingsGoalsViewModel by viewModels()
    private var binding: FragmentSavingsGoalsBinding? = null
    private val adapter = SavingsGoalsAdapter(
        onGoalClick = { item -> openContribution(item.goal.id) },
        onEditClick = { item -> navigateToForm(item.goal.id) },
        onDeleteClick = { item -> confirmDelete(item) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentSavingsGoalsBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        viewBinding.goalsList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.goalsList.adapter = adapter
        viewBinding.addGoalButton.setOnClickListener { navigateToForm(goalId = 0L) }
        viewBinding.emptyCreateButton.setOnClickListener { navigateToForm(goalId = 0L) }

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

    private fun render(state: AppResult<List<SavingsGoalsRow>>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return

        val hasGoals = state.data.isNotEmpty()
        binding.goalsList.visibility = if (hasGoals) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasGoals) View.GONE else View.VISIBLE
        // L'état vide a déjà son propre bouton « Créer un objectif » : pas de doublon avec le FAB.
        binding.addGoalButton.visibility = if (hasGoals) View.VISIBLE else View.GONE
        adapter.submitList(state.data)
    }

    private fun navigateToForm(goalId: Long) {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.savingsGoalsFragment) return
        navController.navigate(
            R.id.savingsGoalFormFragment,
            SavingsGoalFormFragmentArgs(savingsGoalId = goalId).toBundle(),
            NavAnimations.push
        )
    }

    /** La vérification de la destination courante évite un double tap qui ouvrirait deux panneaux
     *  (ou planterait la navigation, la carte n'étant plus sur l'écran courant). */
    private fun openContribution(goalId: Long) {
        val navController = findNavController()
        if (navController.currentDestination?.id != R.id.savingsGoalsFragment) return
        navController.navigate(
            R.id.savingsContributionBottomSheet,
            SavingsContributionBottomSheetArgs(savingsGoalId = goalId).toBundle()
        )
    }

    private fun confirmDelete(item: SavingsGoalUiItem) {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.savings_goals_delete_title),
            message = getString(R.string.savings_goals_delete_message, item.goal.name),
            onConfirm = { viewModel.deleteSavingsGoal(item.goal.id) }
        )
    }
}
