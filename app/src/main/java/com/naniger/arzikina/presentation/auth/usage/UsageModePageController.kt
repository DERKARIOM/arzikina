package com.naniger.arzikina.presentation.auth.usage

import android.view.View
import com.google.android.material.color.MaterialColors
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.ItemUsageModeCardBinding
import com.naniger.arzikina.databinding.ViewUsageModePageBinding
import com.naniger.arzikina.domain.model.UsageMode

/**
 * Pilote la page « Comment souhaitez-vous utiliser Arsikina ? » ([ViewUsageModePageBinding]), partagée par
 * l'inscription classique et l'écran Google. SANS état propre : l'appelant (ViewModel) détient le choix et appelle
 * [render] ; ce contrôleur ne fait qu'afficher et relayer les actions. Une seule carte peut être sélectionnée,
 * et « Continuer » reste inactif tant qu'aucun choix valide n'a été fait.
 */
class UsageModePageController(
    private val binding: ViewUsageModePageBinding,
    private val onModeSelected: (UsageMode) -> Unit,
    private val onContinue: () -> Unit,
    /** `null` : pas de flèche retour (écran Google, où l'on ne peut pas abandonner en cours de route). */
    onBack: (() -> Unit)?
) {

    private val cards: Map<UsageMode, ItemUsageModeCardBinding> = mapOf(
        UsageMode.SHOP to binding.cardShop,
        UsageMode.PERSONAL to binding.cardPersonal,
        UsageMode.BOTH to binding.cardBoth
    )

    init {
        cards.forEach { (mode, card) ->
            card.usageCardTitle.setText(mode.titleRes)
            card.usageCardDescription.setText(mode.descriptionRes)
            card.usageIconFirst.setImageResource(mode.firstIconRes)
            mode.secondIconRes?.let {
                card.usageIconSecond.setImageResource(it)
                card.usageIconSecond.visibility = View.VISIBLE
            }
            card.usageCard.setOnClickListener { onModeSelected(mode) }
        }
        binding.usageModeContinueButton.setOnClickListener { onContinue() }
        if (onBack != null) {
            binding.usageModeToolbar.setNavigationOnClickListener { onBack() }
        } else {
            binding.usageModeToolbar.navigationIcon = null
        }
    }

    /** [selected] : choix courant (ou `null`) ; [busy] : traitement en cours, plus aucune interaction possible. */
    fun render(selected: UsageMode?, busy: Boolean) {
        val selectedStroke = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)
        val idleStroke = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOutlineVariant)
        val density = binding.root.resources.displayMetrics.density
        cards.forEach { (mode, card) ->
            val isSelected = mode == selected
            card.usageCard.isChecked = isSelected
            card.usageCard.isEnabled = !busy
            card.usageCard.strokeColor = if (isSelected) selectedStroke else idleStroke
            card.usageCard.strokeWidth = ((if (isSelected) SELECTED_STROKE_DP else IDLE_STROKE_DP) * density).toInt()
        }
        binding.usageModeContinueButton.isEnabled = selected != null && !busy
        binding.usageModeContinueButton.text = if (busy) "" else binding.root.context.getString(R.string.usage_mode_continue)
        binding.usageModeProgress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.usageModeToolbar.navigationIcon?.alpha = if (busy) DISABLED_ALPHA else FULL_ALPHA
    }

    private companion object {
        const val SELECTED_STROKE_DP = 2
        const val IDLE_STROKE_DP = 1
        const val DISABLED_ALPHA = 80
        const val FULL_ALPHA = 255
    }
}
