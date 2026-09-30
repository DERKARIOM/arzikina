package com.naniger.arzikina.domain.update

import java.util.concurrent.TimeUnit

/**
 * Réglages de la stratégie de mise à jour — SEUL endroit à modifier pour la rendre plus ou moins
 * insistante (voir [InAppUpdatePolicy]).
 *
 * @property immediatePriorityThreshold priorité Play (0..5) à partir de laquelle la mise à jour est
 *   considérée urgente → flux Immediate officiel. 4 = seuil recommandé par la documentation Google.
 * @property immediateStalenessDays si non `null`, une mise à jour ignorée depuis au moins ce nombre
 *   de jours devient elle aussi urgente. Désactivé par défaut : seule la priorité, décidée
 *   explicitement à la publication, peut bloquer l'utilisateur.
 * @property flexibleMinPriority priorité minimale pour proposer une mise à jour Flexible.
 * @property flexibleMinStalenessDays délai (jours) avant de proposer une mise à jour Flexible —
 *   0 = dès que Play la signale.
 * @property flexibleSnoozeMillis durée pendant laquelle un « Plus tard » fait taire la proposition
 *   pour la MÊME version (une version plus récente est proposée normalement).
 */
data class InAppUpdateConfig(
    val immediatePriorityThreshold: Int = 4,
    val immediateStalenessDays: Int? = null,
    val flexibleMinPriority: Int = 0,
    val flexibleMinStalenessDays: Int = 0,
    val flexibleSnoozeMillis: Long = TimeUnit.DAYS.toMillis(3)
)

/** Ce que l'app doit faire après une vérification (voir [InAppUpdatePolicy.decide]). */
sealed interface InAppUpdateDecision {
    /** Aucune mise à jour, déjà en cours, ou proposition mise en sourdine : ne rien afficher. */
    data object None : InAppUpdateDecision

    /** Mise à jour Flexible téléchargée mais pas installée : proposer « Redémarrer ». */
    data object PromptInstallDownloaded : InAppUpdateDecision

    /** Mise à jour Immediate interrompue (app quittée pendant l'installation) : la reprendre, comme
     * l'exige la documentation Google. */
    data object ResumeImmediate : InAppUpdateDecision

    /** Mise à jour urgente : lancer le flux Immediate officiel de Google Play. */
    data object StartImmediate : InAppUpdateDecision

    /** Mise à jour normale : proposer « Mettre à jour / Plus tard », puis flux Flexible. */
    data class OfferFlexible(val versionCode: Int) : InAppUpdateDecision
}

/**
 * Règles de décision, sans aucune dépendance Android ni Play (testées en JVM pur, voir
 * `InAppUpdatePolicyTest`). Ordre d'évaluation :
 * 1. téléchargement Flexible terminé → « Redémarrer » (toujours, sans sourdine : l'installation ne
 *    coûte qu'un redémarrage) ;
 * 2. mise à jour lancée par l'app déjà en cours → reprendre l'Immediate, ou rien si c'est un
 *    téléchargement Flexible qui avance ;
 * 3. pas de mise à jour disponible → rien ;
 * 4. urgente (priorité ≥ seuil) et Immediate autorisé par Play → Immediate. Si l'utilisateur l'a
 *    déjà annulée pendant ce lancement de l'app, on ne la relance PAS (pas de boucle agressive) :
 *    elle sera reproposée au prochain démarrage ;
 * 5. sinon Flexible, si Play l'autorise, si les seuils sont atteints, si rien n'a déjà été proposé
 *    pendant ce lancement, et hors période de sourdine d'un « Plus tard » pour cette version.
 */
class InAppUpdatePolicy(private val config: InAppUpdateConfig = InAppUpdateConfig()) {

    fun decide(
        snapshot: AppUpdateSnapshot,
        lastDismissal: UpdatePromptDismissal?,
        nowMillis: Long,
        session: SessionFlags = SessionFlags()
    ): InAppUpdateDecision {
        if (snapshot.installStatus == AppUpdateSnapshot.InstallState.DOWNLOADED) {
            return InAppUpdateDecision.PromptInstallDownloaded
        }
        if (snapshot.availability == AppUpdateSnapshot.Availability.DEVELOPER_TRIGGERED_IN_PROGRESS) {
            return if (snapshot.installStatus.isInFlight || !snapshot.isImmediateAllowed) {
                InAppUpdateDecision.None
            } else {
                InAppUpdateDecision.ResumeImmediate
            }
        }
        if (snapshot.availability != AppUpdateSnapshot.Availability.AVAILABLE) return InAppUpdateDecision.None
        if (snapshot.installStatus.isInFlight) return InAppUpdateDecision.None

        if (isUrgent(snapshot)) {
            if (session.immediateDeclined) return InAppUpdateDecision.None
            if (snapshot.isImmediateAllowed) return InAppUpdateDecision.StartImmediate
            // Immediate refusé par Play pour cette mise à jour : repli sur le Flexible ci-dessous.
        }

        if (!snapshot.isFlexibleAllowed) return InAppUpdateDecision.None
        if (session.flexibleHandled) return InAppUpdateDecision.None
        if (snapshot.priority < config.flexibleMinPriority) return InAppUpdateDecision.None
        if ((snapshot.stalenessDays ?: 0) < config.flexibleMinStalenessDays) return InAppUpdateDecision.None
        if (isSnoozed(snapshot, lastDismissal, nowMillis)) return InAppUpdateDecision.None

        return InAppUpdateDecision.OfferFlexible(snapshot.availableVersionCode)
    }

    private fun isUrgent(snapshot: AppUpdateSnapshot): Boolean {
        if (snapshot.priority >= config.immediatePriorityThreshold) return true
        val stalenessLimit = config.immediateStalenessDays ?: return false
        val staleness = snapshot.stalenessDays ?: return false
        return staleness >= stalenessLimit
    }

    private fun isSnoozed(
        snapshot: AppUpdateSnapshot,
        lastDismissal: UpdatePromptDismissal?,
        nowMillis: Long
    ): Boolean {
        if (lastDismissal == null) return false
        if (lastDismissal.versionCode != snapshot.availableVersionCode) return false
        val elapsed = nowMillis - lastDismissal.dismissedAtMillis
        // Horloge reculée (elapsed < 0) : on reste prudent et on considère la sourdine active.
        return elapsed < config.flexibleSnoozeMillis
    }

    /**
     * Ce qui s'est déjà passé pendant CE lancement de l'app (état en mémoire, remis à zéro au
     * prochain démarrage du processus — voir `InAppUpdateSessionState`).
     *
     * @property immediateDeclined l'utilisateur a annulé (ou Play a fait échouer) le flux Immediate.
     * @property flexibleHandled une proposition Flexible a déjà été acceptée, refusée ou lancée.
     */
    data class SessionFlags(
        val immediateDeclined: Boolean = false,
        val flexibleHandled: Boolean = false
    )
}
