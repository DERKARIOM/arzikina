package com.naniger.arzikina.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fou du contrat de [CategoryIcon] : ses noms sont stockés, synchronisés et sauvegardés, donc
 * un renommage ou une suppression casserait des données existantes (voir la documentation de l'énumération).
 */
class CategoryIconContractTest {

    private val originalNames = listOf(
        "FOOD", "TRANSPORT", "HEALTH", "SALARY", "SHOPPING", "GIFTS", "INTERNET", "WATER",
        "ELECTRICITY", "EDUCATION", "HOME", "OTHER", "LOAN", "FEE"
    )

    @Test
    fun originalIconsAreKeptInTheirOriginalOrder() {
        assertEquals(originalNames, CategoryIcon.entries.take(originalNames.size).map { it.name })
    }

    @Test
    fun namesAreSimpleUppercaseTokensSafeForSyncAndBackup() {
        val valid = Regex("^[A-Z][A-Z_]{0,31}$")
        CategoryIcon.entries.forEach { assertTrue("${it.name} n'est pas un nom d'icône valide", valid.matches(it.name)) }
    }

    @Test
    fun everyNameRoundTripsThroughValueOf() {
        CategoryIcon.entries.forEach { assertEquals(it, CategoryIcon.valueOf(it.name)) }
    }

    @Test
    fun unknownNameFallsBackToOtherLikeSyncAndBackupDo() {
        val resolved = runCatching { CategoryIcon.valueOf("ICON_FROM_THE_FUTURE") }.getOrDefault(CategoryIcon.OTHER)
        assertEquals(CategoryIcon.OTHER, resolved)
    }

    @Test
    fun pickerOffersAVarietyOfIcons() {
        assertTrue(CategoryIcon.entries.size >= 50)
    }
}
