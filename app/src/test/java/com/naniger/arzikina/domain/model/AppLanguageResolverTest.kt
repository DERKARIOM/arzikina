package com.naniger.arzikina.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Règles de détection et de priorité des langues (cahier des charges i18n, sections 3 et 5).
 * Kotlin pur : aucune dépendance Android, exécution en quelques millisecondes.
 */
class AppLanguageResolverTest {

    // --- Détection automatique (choix « Langue du système ») ---

    @Test
    fun `systeme en francais, quelle que soit la region, donne le francais`() {
        listOf("fr-FR", "fr-CA", "fr-NI", "fr-NE", "fr").forEach { tag ->
            assertEquals(tag, AppLanguage.FRENCH, AppLanguageResolver.resolve(AppLanguage.SYSTEM, listOf(tag)))
        }
    }

    @Test
    fun `systeme en anglais, quelle que soit la region, donne l anglais`() {
        listOf("en-US", "en-GB", "en-NG", "en").forEach { tag ->
            assertEquals(tag, AppLanguage.ENGLISH, AppLanguageResolver.resolve(AppLanguage.SYSTEM, listOf(tag)))
        }
    }

    @Test
    fun `systeme dans une langue non supportee retombe sur le francais`() {
        listOf("ar-SA", "ha-NG", "es-ES").forEach { tag ->
            assertEquals(tag, AppLanguage.FRENCH, AppLanguageResolver.resolve(AppLanguage.SYSTEM, listOf(tag)))
        }
    }

    @Test
    fun `aucune langue systeme connue retombe sur le francais`() {
        assertEquals(AppLanguage.FRENCH, AppLanguageResolver.resolve(AppLanguage.SYSTEM, emptyList()))
    }

    @Test
    fun `plusieurs langues systeme, la premiere supportee l emporte comme dans Android`() {
        assertEquals(
            AppLanguage.ENGLISH,
            AppLanguageResolver.resolve(AppLanguage.SYSTEM, listOf("ha-NG", "en-NG", "fr-NE"))
        )
        assertEquals(
            AppLanguage.FRENCH,
            AppLanguageResolver.resolve(AppLanguage.SYSTEM, listOf("fr-NE", "en-US"))
        )
    }

    // --- Priorité du choix manuel ---

    @Test
    fun `un choix manuel a toujours priorite sur la langue du systeme`() {
        assertEquals(AppLanguage.FRENCH, AppLanguageResolver.resolve(AppLanguage.FRENCH, listOf("en-US")))
        assertEquals(AppLanguage.ENGLISH, AppLanguageResolver.resolve(AppLanguage.ENGLISH, listOf("fr-FR")))
        assertEquals(AppLanguage.ENGLISH, AppLanguageResolver.resolve(AppLanguage.ENGLISH, listOf("ar-SA")))
    }

    @Test
    fun `le resultat n est jamais SYSTEM`() {
        AppLanguage.entries.forEach { selected ->
            val resolved = AppLanguageResolver.resolve(selected, listOf("ha-NG"))
            assertNotEquals("resolve($selected)", AppLanguage.SYSTEM, resolved)
        }
    }

    // --- Analyse des étiquettes de langue ---

    @Test
    fun `fromLanguageTag ignore la casse, la region et le separateur`() {
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromLanguageTag("FR"))
        assertEquals(AppLanguage.FRENCH, AppLanguage.fromLanguageTag("fr_NE"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag(" en-Latn-NG "))
    }

    @Test
    fun `fromLanguageTag renvoie null pour une langue non supportee ou vide`() {
        assertNull(AppLanguage.fromLanguageTag("ha-NG"))
        assertNull(AppLanguage.fromLanguageTag(""))
        assertNull(AppLanguage.fromLanguageTag(null))
    }

    @Test
    fun `la langue par defaut est le francais et SYSTEM n est pas une langue supportee`() {
        assertEquals(AppLanguage.FRENCH, AppLanguage.DEFAULT)
        assertEquals(listOf(AppLanguage.FRENCH, AppLanguage.ENGLISH), AppLanguage.supported)
    }
}
