package com.naniger.arzikina

import com.naniger.arzikina.domain.update.AppUpdateSnapshot
import com.naniger.arzikina.domain.update.AppUpdateSnapshot.Availability
import com.naniger.arzikina.domain.update.AppUpdateSnapshot.InstallState
import com.naniger.arzikina.domain.update.InAppUpdateConfig
import com.naniger.arzikina.domain.update.InAppUpdateDecision
import com.naniger.arzikina.domain.update.InAppUpdatePolicy
import com.naniger.arzikina.domain.update.UpdatePromptDismissal
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Règles de décision des mises à jour Google Play (voir `domain/update/InAppUpdatePolicy.kt`).
 *
 * Placé à la racine du package de test (et non sous `domain/update/`) : l'outil de synchronisation
 * de fichiers utilisé pendant le développement ne descend pas au-delà de 7 niveaux de dossiers.
 */
class InAppUpdatePolicyTest {

    private val policy = InAppUpdatePolicy(InAppUpdateConfig())
    private val now = 1_800_000_000_000L
    private val day = TimeUnit.DAYS.toMillis(1)

    private fun snapshot(
        availability: Availability = Availability.AVAILABLE,
        installStatus: InstallState = InstallState.NONE,
        versionCode: Int = 5,
        priority: Int = 0,
        stalenessDays: Int? = null,
        flexibleAllowed: Boolean = true,
        immediateAllowed: Boolean = true
    ) = AppUpdateSnapshot(
        availability = availability,
        installStatus = installStatus,
        availableVersionCode = versionCode,
        priority = priority,
        stalenessDays = stalenessDays,
        isFlexibleAllowed = flexibleAllowed,
        isImmediateAllowed = immediateAllowed
    )

    private fun decide(
        snapshot: AppUpdateSnapshot,
        dismissal: UpdatePromptDismissal? = null,
        session: InAppUpdatePolicy.SessionFlags = InAppUpdatePolicy.SessionFlags(),
        policy: InAppUpdatePolicy = this.policy
    ) = policy.decide(snapshot, dismissal, now, session)

    @Test
    fun `aucune mise a jour disponible - rien`() {
        assertEquals(InAppUpdateDecision.None, decide(snapshot(availability = Availability.NOT_AVAILABLE)))
        assertEquals(InAppUpdateDecision.None, decide(snapshot(availability = Availability.UNKNOWN)))
    }

    @Test
    fun `priorite normale - proposition flexible`() {
        assertEquals(InAppUpdateDecision.OfferFlexible(5), decide(snapshot(priority = 0)))
        assertEquals(InAppUpdateDecision.OfferFlexible(5), decide(snapshot(priority = 3)))
    }

    @Test
    fun `priorite elevee - flux immediate`() {
        assertEquals(InAppUpdateDecision.StartImmediate, decide(snapshot(priority = 4)))
        assertEquals(InAppUpdateDecision.StartImmediate, decide(snapshot(priority = 5)))
    }

    @Test
    fun `priorite elevee mais immediate refuse par Play - repli flexible`() {
        assertEquals(
            InAppUpdateDecision.OfferFlexible(5),
            decide(snapshot(priority = 5, immediateAllowed = false))
        )
    }

    @Test
    fun `immediate annule pendant ce lancement - pas de relance en boucle`() {
        val session = InAppUpdatePolicy.SessionFlags(immediateDeclined = true)
        assertEquals(InAppUpdateDecision.None, decide(snapshot(priority = 5), session = session))
    }

    @Test
    fun `aucun type autorise par Play - rien`() {
        assertEquals(
            InAppUpdateDecision.None,
            decide(snapshot(flexibleAllowed = false, immediateAllowed = false))
        )
        assertEquals(InAppUpdateDecision.None, decide(snapshot(priority = 1, flexibleAllowed = false)))
    }

    @Test
    fun `telechargement termine - proposer Redemarrer meme apres Plus tard`() {
        val dismissal = UpdatePromptDismissal(versionCode = 5, dismissedAtMillis = now - 1_000)
        val downloaded = snapshot(
            availability = Availability.DEVELOPER_TRIGGERED_IN_PROGRESS,
            installStatus = InstallState.DOWNLOADED
        )
        assertEquals(InAppUpdateDecision.PromptInstallDownloaded, decide(downloaded, dismissal))
    }

    @Test
    fun `immediate interrompu - reprise`() {
        val interrupted = snapshot(availability = Availability.DEVELOPER_TRIGGERED_IN_PROGRESS)
        assertEquals(InAppUpdateDecision.ResumeImmediate, decide(interrupted))
    }

    @Test
    fun `telechargement flexible en cours - rien`() {
        for (state in listOf(InstallState.PENDING, InstallState.DOWNLOADING, InstallState.INSTALLING)) {
            val inProgress = snapshot(availability = Availability.DEVELOPER_TRIGGERED_IN_PROGRESS, installStatus = state)
            assertEquals(InAppUpdateDecision.None, decide(inProgress))
            assertEquals(InAppUpdateDecision.None, decide(snapshot(installStatus = state)))
        }
    }

    @Test
    fun `Plus tard recent pour la meme version - silence`() {
        val dismissal = UpdatePromptDismissal(versionCode = 5, dismissedAtMillis = now - day)
        assertEquals(InAppUpdateDecision.None, decide(snapshot(), dismissal))
    }

    @Test
    fun `Plus tard expire - nouvelle proposition`() {
        val dismissal = UpdatePromptDismissal(versionCode = 5, dismissedAtMillis = now - 3 * day)
        assertEquals(InAppUpdateDecision.OfferFlexible(5), decide(snapshot(), dismissal))
    }

    @Test
    fun `Plus tard sur une version precedente - la nouvelle version est proposee`() {
        val dismissal = UpdatePromptDismissal(versionCode = 4, dismissedAtMillis = now - 1_000)
        assertEquals(InAppUpdateDecision.OfferFlexible(5), decide(snapshot(versionCode = 5), dismissal))
    }

    @Test
    fun `Plus tard ne bloque jamais une mise a jour urgente`() {
        val dismissal = UpdatePromptDismissal(versionCode = 5, dismissedAtMillis = now - 1_000)
        assertEquals(InAppUpdateDecision.StartImmediate, decide(snapshot(priority = 4), dismissal))
    }

    @Test
    fun `horloge reculee - la sourdine reste active`() {
        val dismissal = UpdatePromptDismissal(versionCode = 5, dismissedAtMillis = now + day)
        assertEquals(InAppUpdateDecision.None, decide(snapshot(), dismissal))
    }

    @Test
    fun `proposition deja traitee pendant ce lancement - pas de doublon`() {
        val session = InAppUpdatePolicy.SessionFlags(flexibleHandled = true)
        assertEquals(InAppUpdateDecision.None, decide(snapshot(), session = session))
    }

    @Test
    fun `seuils configurables`() {
        val strict = InAppUpdatePolicy(
            InAppUpdateConfig(
                immediatePriorityThreshold = 5,
                immediateStalenessDays = 30,
                flexibleMinPriority = 2,
                flexibleMinStalenessDays = 2
            )
        )
        assertEquals(InAppUpdateDecision.None, decide(snapshot(priority = 1, stalenessDays = 10), policy = strict))
        assertEquals(InAppUpdateDecision.None, decide(snapshot(priority = 2, stalenessDays = 1), policy = strict))
        assertEquals(InAppUpdateDecision.OfferFlexible(5), decide(snapshot(priority = 4, stalenessDays = 2), policy = strict))
        assertEquals(InAppUpdateDecision.StartImmediate, decide(snapshot(priority = 0, stalenessDays = 30), policy = strict))
    }
}
