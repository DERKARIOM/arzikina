package com.naniger.arzikina.presentation.savings

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.BottomSheetSavingsContributionBinding
import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.domain.model.SavingsGoal
import com.naniger.arzikina.util.ContributionPreview
import com.naniger.arzikina.util.ContributionType
import com.naniger.arzikina.util.Money
import com.naniger.arzikina.util.MoneyInputFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Panneau « Ajouter / Retirer » d'un objectif d'épargne, ouvert au toucher d'une carte dans
 * [SavingsGoalsFragment] (destination `<dialog>` du graphe, argument `savingsGoalId`).
 *
 * Toute la logique (aperçu, blocage sous 0, détection de l'objectif atteint) est dans
 * [SavingsContributionViewModel] et `SavingsContribution` ; ce Fragment ne fait qu'afficher l'état.
 * La liste se met à jour seule (elle observe Room) : aucun résultat à renvoyer.
 */
@AndroidEntryPoint
class SavingsContributionBottomSheet : BottomSheetDialogFragment(R.layout.bottom_sheet_savings_contribution) {

    private val viewModel: SavingsContributionViewModel by viewModels()
    private var binding: BottomSheetSavingsContributionBinding? = null

    /** Devise des raccourcis déjà créés : ils ne sont reconstruits que si elle change. */
    private var quickAmountsCurrency: String? = null
    private var isCelebrationShown = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = BottomSheetSavingsContributionBinding.bind(view)
        binding = viewBinding

        // Panneau court : ouvert en entier d'emblée, sans palier intermédiaire.
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        viewBinding.typeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.onTypeChange(
                if (checkedId == R.id.withdrawButton) ContributionType.WITHDRAWAL else ContributionType.DEPOSIT
            )
        }
        MoneyInputFormatter.attach(viewBinding.amountInput) { formatted -> viewModel.onAmountChange(formatted) }
        viewBinding.cancelButton.setOnClickListener { dismiss() }
        viewBinding.confirmButton.setOnClickListener { viewModel.confirm() }
        viewBinding.celebrationDoneButton.setOnClickListener { dismiss() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { state -> render(state) } }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            SavingsContributionEvent.Close -> dismiss()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        quickAmountsCurrency = null
        isCelebrationShown = false
    }

    private fun render(state: SavingsContributionState) {
        val binding = binding ?: return
        val goal = state.goal ?: return

        val celebration = state.celebration
        if (celebration != null) {
            showCelebration(binding, celebration)
            return
        }

        binding.goalName.text = goal.name
        binding.savedSummary.text = getString(
            R.string.savings_contribution_saved_of,
            Money.format(CurrencyAmount(goal.currencyCode, goal.currentAmount)),
            Money.format(CurrencyAmount(goal.currencyCode, goal.targetAmount))
        )
        binding.amountLayout.suffixText = Money.symbolOf(goal.currencyCode)
        binding.withdrawButton.isEnabled = state.canWithdraw

        renderQuickAmounts(binding, goal, state)
        renderPreview(binding, goal, state)
        binding.confirmButton.isEnabled = state.canConfirm
    }

    private fun renderQuickAmounts(
        binding: BottomSheetSavingsContributionBinding,
        goal: SavingsGoal,
        state: SavingsContributionState
    ) {
        if (quickAmountsCurrency != goal.currencyCode) {
            quickAmountsCurrency = goal.currencyCode
            binding.quickAmountsGroup.removeAllViews()
            val inflater = LayoutInflater.from(binding.quickAmountsGroup.context)
            state.quickAmounts.forEach { amount ->
                val chip = inflater.inflate(R.layout.item_savings_quick_amount_chip, binding.quickAmountsGroup, false) as Chip
                chip.text = Money.formatAmount(amount)
                chip.tag = amount
                chip.setOnClickListener {
                    val text = Money.formatForInput(amount)
                    binding.amountInput.setText(text)
                    binding.amountInput.setSelection(text.length)
                }
                binding.quickAmountsGroup.addView(chip)
            }
        }
        for (index in 0 until binding.quickAmountsGroup.childCount) {
            val chip = binding.quickAmountsGroup.getChildAt(index) as Chip
            chip.isChecked = chip.tag == state.selectedQuickAmount
        }
    }

    private fun renderPreview(
        binding: BottomSheetSavingsContributionBinding,
        goal: SavingsGoal,
        state: SavingsContributionState
    ) {
        val preview = state.preview
        binding.amountLayout.error = when (preview) {
            ContributionPreview.InvalidAmount -> getString(R.string.error_invalid_amount)
            is ContributionPreview.ExceedsSaved -> getString(
                R.string.savings_contribution_exceeds_saved,
                Money.format(CurrencyAmount(goal.currencyCode, preview.savedAmount))
            )
            else -> state.errorRes?.let(::getString)
        }

        val valid = preview as? ContributionPreview.Valid
        binding.previewBox.visibility = if (valid != null) View.VISIBLE else View.GONE
        if (valid == null) return

        binding.previewLabel.setText(
            if (state.type == ContributionType.WITHDRAWAL) {
                R.string.savings_contribution_preview_withdraw
            } else {
                R.string.savings_contribution_preview_deposit
            }
        )
        binding.previewValue.text = getString(
            R.string.savings_contribution_preview_value,
            Money.format(CurrencyAmount(goal.currencyCode, valid.newAmount)),
            valid.newProgressPercent
        )
        binding.previewProgress.setProgressCompat(valid.newProgressPercent, true)
        binding.reachesTargetText.visibility = if (valid.reachesTarget) View.VISIBLE else View.GONE
    }

    /** Bascule vers « Objectif atteint » : une seule fois animée, puis simplement réaffichée. */
    private fun showCelebration(binding: BottomSheetSavingsContributionBinding, celebration: SavingsCelebration) {
        binding.celebrationMessage.text = getString(
            R.string.savings_celebration_message,
            Money.format(celebration.savedAmount),
            celebration.goalName
        )
        binding.contributionContent.visibility = View.GONE
        binding.celebrationContent.visibility = View.VISIBLE
        if (isCelebrationShown) return
        isCelebrationShown = true

        binding.celebrationContent.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        binding.celebrationIcon.apply {
            scaleX = 0.4f
            scaleY = 0.4f
            alpha = 0f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(CELEBRATION_ANIMATION_MS)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
    }

    private companion object {
        const val CELEBRATION_ANIMATION_MS = 450L
    }
}
