package com.naniger.arzikina.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Voir [resolveAppLocale] et [AppDateFormats] : chantier i18n, étape 3. */
class AppLocaleTest {

    @Test
    fun `une langue supportee est gardee avec sa region`() {
        assertEquals("en-NG", resolveAppLocale(listOf(Locale.forLanguageTag("en-NG"))).toLanguageTag())
        assertEquals("fr-NE", resolveAppLocale(listOf(Locale.forLanguageTag("fr-NE"))).toLanguageTag())
    }

    @Test
    fun `une langue non supportee retombe sur le francais comme l interface`() {
        assertEquals("fr", resolveAppLocale(listOf(Locale.forLanguageTag("ha-NG"))).toLanguageTag())
        assertEquals("fr", resolveAppLocale(emptyList()).toLanguageTag())
    }

    @Test
    fun `la premiere langue supportee de la liste l emporte`() {
        val locales = listOf(Locale.forLanguageTag("ha-NG"), Locale.forLanguageTag("en-GB"))
        assertEquals("en-GB", resolveAppLocale(locales).toLanguageTag())
    }

    @Test
    fun `la date numerique est identique dans toutes les langues`() {
        assertEquals("03/04/2026", LocalDate.of(2026, 4, 3).format(AppDateFormats.NUMERIC_DATE))
    }

    @Test
    fun `seuls les noms de mois changent selon la langue`() {
        val date = LocalDate.of(2026, 9, 23)
        val pattern = "d MMMM yyyy" // même motif que AppDateFormats.longDate
        assertEquals("23 septembre 2026", date.format(DateTimeFormatter.ofPattern(pattern, Locale.FRENCH)))
        assertEquals("23 September 2026", date.format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)))
    }
}
