package com.naniger.arzikina.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Scénarios 5 (reconnexion / synchronisation) et 6 (non-régression) du cahier des charges, plus la reprise. */
class OnboardingSeedingPolicyTest {

    private fun decide(
        isRegistration: Boolean = false,
        registrationMode: UsageMode? = null,
        sessionMode: UsageMode? = null,
        hasServerAccounts: Boolean = true
    ) = OnboardingSeedingPolicy.decide(isRegistration, registrationMode, sessionMode, hasServerAccounts)

    @Test
    fun `inscription avec un mode cree les comptes de ce mode`() {
        UsageMode.entries.forEach { mode ->
            assertEquals(SeedDecision.ForMode(mode), decide(isRegistration = true, registrationMode = mode, hasServerAccounts = false))
        }
    }

    @Test
    fun `inscription sans mode ne cree aucun compte (jamais de comptes inventes)`() {
        assertEquals(SeedDecision.None, decide(isRegistration = true))
    }

    @Test
    fun `scenario 5 - reconnexion sur un appareil ou le serveur a deja des comptes ne cree rien`() {
        UsageMode.entries.forEach { mode ->
            assertEquals(SeedDecision.None, decide(sessionMode = mode, hasServerAccounts = true))
        }
    }

    @Test
    fun `reprise - utilisateur cree sur le serveur mais jamais configure recoit ses comptes`() {
        assertEquals(
            SeedDecision.ForMode(UsageMode.BOTH),
            decide(sessionMode = UsageMode.BOTH, hasServerAccounts = false)
        )
    }

    @Test
    fun `scenario 6 - utilisateur existant sans mode n est jamais configure a la connexion`() {
        assertEquals(SeedDecision.None, decide(sessionMode = null, hasServerAccounts = true))
        assertEquals(SeedDecision.None, decide(sessionMode = null, hasServerAccounts = false))
    }

    @Test
    fun `le mode choisi a l inscription prime sur celui du serveur`() {
        assertEquals(UsageMode.SHOP, OnboardingSeedingPolicy.effectiveMode(UsageMode.SHOP, UsageMode.PERSONAL))
        assertEquals(UsageMode.PERSONAL, OnboardingSeedingPolicy.effectiveMode(null, UsageMode.PERSONAL))
        assertNull(OnboardingSeedingPolicy.effectiveMode(null, null))
    }
}
