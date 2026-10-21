package com.naniger.arzikina.presentation.utilities.issuedreceipts.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class IssuedReceiptHistoryFiltersTest {

    private val zone = ZoneId.of("Africa/Niamey") // UTC+1, sans heure d'été
    private fun localStart(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `filtre vide inactif et requete nettoyee`() {
        assertFalse(IssuedReceiptHistoryFilters().isActive)
        assertFalse(IssuedReceiptHistoryFilters(query = "   ").isActive)
        assertEquals("abc", IssuedReceiptHistoryFilters(query = "  abc ").toDomain().query)
        assertTrue(IssuedReceiptHistoryFilters(query = "a").isActive)
    }

    @Test
    fun `jour du picker UTC devient debut de jour local`() {
        val picker = LocalDate.of(2026, 10, 20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(localStart(LocalDate.of(2026, 10, 20)), HistoryDateBounds.startOfDayFromPicker(picker, zone))
        assertEquals(picker, HistoryDateBounds.toPickerSelection(localStart(LocalDate.of(2026, 10, 20)), zone))
    }

    @Test
    fun `fin de jour est la derniere milliseconde`() {
        val start = localStart(LocalDate.of(2026, 10, 20))
        assertEquals(localStart(LocalDate.of(2026, 10, 21)) - 1, HistoryDateBounds.endOfDay(start + 5_000, zone))
    }

    @Test
    fun `une date de fin anterieure au depart est ramenee`() {
        val f = IssuedReceiptHistoryFilters()
            .withTo(localStart(LocalDate.of(2026, 10, 10)), zone)
            .withFrom(localStart(LocalDate.of(2026, 10, 20)), zone)
        assertEquals(localStart(LocalDate.of(2026, 10, 20)), f.fromMillis)
        assertEquals(localStart(LocalDate.of(2026, 10, 21)) - 1, f.toMillis)
    }

    @Test
    fun `un depart posterieur a la fin est ramene`() {
        val f = IssuedReceiptHistoryFilters()
            .withFrom(localStart(LocalDate.of(2026, 10, 20)), zone)
            .withTo(localStart(LocalDate.of(2026, 10, 10)), zone)
        assertEquals(localStart(LocalDate.of(2026, 10, 10)), f.fromMillis)
        assertEquals(localStart(LocalDate.of(2026, 10, 11)) - 1, f.toMillis)
    }

    @Test
    fun `effacer les dates garde la recherche`() {
        val f = IssuedReceiptHistoryFilters(query = "x").withFrom(localStart(LocalDate.of(2026, 1, 1)), zone).clearDates()
        assertNull(f.fromMillis); assertNull(f.toMillis); assertEquals("x", f.query)
    }
}
