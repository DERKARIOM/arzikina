package com.naniger.arzikina.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Règle métier « comptes par défaut selon le mode » : scénarios 1 à 4 du cahier des charges. */
class DefaultAccountPlanTest {

    private fun types(mode: UsageMode, existing: Set<AccountType> = emptySet()) =
        DefaultAccountPlan.missingKeys(mode, existing).map(DefaultAccountPlan::accountTypeOf)

    @Test
    fun `scenario 1 - boutique cree un compte Boutique et aucun compte Especes`() {
        assertEquals(listOf(AccountType.BOUTIQUE), types(UsageMode.SHOP))
    }

    @Test
    fun `scenario 2 - finances personnelles cree un compte Especes et aucun compte Boutique`() {
        assertEquals(listOf(AccountType.CASH), types(UsageMode.PERSONAL))
    }

    @Test
    fun `scenario 3 - les deux creent un compte Boutique et un compte Especes distincts`() {
        val keys = DefaultAccountPlan.missingKeys(UsageMode.BOTH, emptySet())
        assertEquals(listOf(DefaultAccountKey.SHOP, DefaultAccountKey.CASH), keys)
        assertEquals(2, keys.map(DefaultAccountPlan::accountTypeOf).toSet().size)
        assertEquals(2, keys.map { it.canonicalName }.toSet().size)
    }

    @Test
    fun `aucun mode ne cree les anciens comptes Banque Mobile Money Epargne ou Wallet`() {
        val legacy = setOf(DefaultAccountKey.BANK, DefaultAccountKey.MOBILE_MONEY, DefaultAccountKey.SAVINGS, DefaultAccountKey.WALLET)
        UsageMode.entries.forEach { mode ->
            assertTrue(DefaultAccountPlan.keysFor(mode).none { it in legacy })
        }
    }

    @Test
    fun `scenario 4 - un second appel ne cree rien (double appui, reprise apres coupure)`() {
        UsageMode.entries.forEach { mode ->
            val created = DefaultAccountPlan.keysFor(mode).map(DefaultAccountPlan::accountTypeOf).toSet()
            assertTrue("$mode doit etre idempotent", DefaultAccountPlan.missingKeys(mode, created).isEmpty())
        }
    }

    @Test
    fun `une reprise partielle ne cree que le compte manquant`() {
        assertEquals(
            listOf(DefaultAccountKey.CASH),
            DefaultAccountPlan.missingKeys(UsageMode.BOTH, setOf(AccountType.BOUTIQUE))
        )
        assertEquals(
            listOf(DefaultAccountKey.SHOP),
            DefaultAccountPlan.missingKeys(UsageMode.BOTH, setOf(AccountType.CASH))
        )
    }

    @Test
    fun `le compte Boutique est exclu des statistiques personnelles seulement en mode BOTH`() {
        assertTrue(DefaultAccountPlan.excludesShopFromStatistics(UsageMode.BOTH))
        assertFalse(DefaultAccountPlan.excludesShopFromStatistics(UsageMode.SHOP))
        assertFalse(DefaultAccountPlan.excludesShopFromStatistics(UsageMode.PERSONAL))
    }

    @Test
    fun `un compte renomme ou d un autre type ne declenche pas de doublon ni de blocage`() {
        // Un compte Espèces existant (même renommé) suffit pour PERSONAL ; il n'empêche pas de créer la Boutique.
        assertTrue(DefaultAccountPlan.missingKeys(UsageMode.PERSONAL, setOf(AccountType.CASH)).isEmpty())
        assertEquals(
            listOf(DefaultAccountKey.SHOP),
            DefaultAccountPlan.missingKeys(UsageMode.SHOP, setOf(AccountType.CASH, AccountType.BANK))
        )
    }
}
