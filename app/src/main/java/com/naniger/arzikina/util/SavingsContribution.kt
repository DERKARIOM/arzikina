package com.naniger.arzikina.util

import com.naniger.arzikina.domain.model.SupportedCurrency

/** Sens d'un mouvement sur un objectif d'épargne (panneau « Ajouter / Retirer »). */
enum class ContributionType { DEPOSIT, WITHDRAWAL }

/**
 * Aperçu d'un versement ou d'un retrait avant validation. Voir [SavingsContribution.preview].
 */
sealed interface ContributionPreview {

    /** Champ montant encore vide : ni aperçu ni erreur. */
    data object Empty : ContributionPreview

    /** Montant illisible, nul ou démesuré. */
    data object InvalidAmount : ContributionPreview

    /** Retrait supérieur au montant déjà épargné : l'objectif ne descend jamais sous 0. */
    data class ExceedsSaved(val savedAmount: Long) : ContributionPreview

    /**
     * @param delta variation à appliquer (négative pour un retrait), en unité mineure.
     * @param reachesTarget `true` seulement si CE versement fait franchir la cible (objectif
     * encore en cours avant, atteint après) : c'est ce qui déclenche la célébration.
     */
    data class Valid(
        val delta: Long,
        val newAmount: Long,
        val newProgressPercent: Int,
        val reachesTarget: Boolean
    ) : ContributionPreview
}

/**
 * Règles métier du panneau de versement, en Kotlin pur (testées dans `SavingsContributionTest`).
 * Tous les montants sont en unité mineure (voir [Money.MINOR_UNITS_PER_MAJOR]).
 */
object SavingsContribution {

    /**
     * Plafond d'un seul mouvement : 1 000 milliards d'unités majeures. Bien au-delà de tout usage
     * réel, il protège surtout des débordements de `Long` sur une saisie aberrante.
     */
    const val MAX_AMOUNT: Long = 1_000_000_000_000L * Money.MINOR_UNITS_PER_MAJOR

    /**
     * Calcule le résultat d'un mouvement de [amount] sur un objectif qui a déjà [currentAmount]
     * épargnés sur [targetAmount].
     */
    fun preview(
        currentAmount: Long,
        targetAmount: Long,
        type: ContributionType,
        amount: Long
    ): ContributionPreview {
        if (amount <= 0L || amount > MAX_AMOUNT) return ContributionPreview.InvalidAmount

        val newAmount = when (type) {
            ContributionType.DEPOSIT -> currentAmount + amount
            ContributionType.WITHDRAWAL -> {
                if (amount > currentAmount) return ContributionPreview.ExceedsSaved(currentAmount)
                currentAmount - amount
            }
        }
        return ContributionPreview.Valid(
            delta = if (type == ContributionType.DEPOSIT) amount else -amount,
            newAmount = newAmount,
            newProgressPercent = SavingsGoalProgress.progressPercent(newAmount, targetAmount),
            reachesTarget = type == ContributionType.DEPOSIT &&
                !SavingsGoalProgress.isCompleted(currentAmount, targetAmount) &&
                SavingsGoalProgress.isCompleted(newAmount, targetAmount)
        )
    }

    /**
     * Raccourcis proposés sous le champ montant, adaptés à la devise de l'objectif : 25 000 F CFA
     * est un versement courant, 25 000 € ne l'est pas. Une devise inconnue reprend ceux du franc CFA,
     * devise par défaut de l'application.
     */
    fun quickAmounts(currencyCode: String): List<Long> {
        val majorAmounts = when (SupportedCurrency.entries.firstOrNull { it.code == currencyCode }) {
            SupportedCurrency.EUR, SupportedCurrency.USD -> listOf(10L, 20L, 50L)
            SupportedCurrency.GHS -> listOf(50L, 100L, 200L)
            SupportedCurrency.XOF, SupportedCurrency.NGN, null -> listOf(5_000L, 10_000L, 25_000L)
        }
        return majorAmounts.map { it * Money.MINOR_UNITS_PER_MAJOR }
    }
}
