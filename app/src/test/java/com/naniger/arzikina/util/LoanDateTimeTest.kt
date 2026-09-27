package com.naniger.arzikina.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Date + heure d'un prêt/emprunt (voir [LoanDateTime]). */
class LoanDateTimeTest {

    private val niamey = ZoneId.of("Africa/Niamey") // UTC+1, sans heure d'été
    private val paris = ZoneId.of("Europe/Paris")
    private val newYork = ZoneId.of("America/New_York")

    private fun millis(dateTime: String, zone: ZoneId) = LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `TEST 1 et 2 - date et heure fusionnees en un seul instant local`() {
        val day = millis("2026-09-23T00:00", niamey)
        val loan = LoanDateTime.withTime(day, 18, 30, niamey)
        assertEquals(millis("2026-09-23T18:30", niamey), loan)
        assertEquals(LocalTime.of(18, 30), LoanDateTime.toLocalTime(loan, niamey))
        assertEquals(LocalDate.of(2026, 9, 23), LoanDateTime.toLocalDate(loan, niamey))
    }

    @Test
    fun `TEST 3 - changer l heure garde la date, changer la date garde l heure`() {
        val loan = millis("2026-09-23T18:30", niamey)
        assertEquals(millis("2026-09-23T20:00", niamey), LoanDateTime.withTime(loan, 20, 0, niamey))
        assertEquals(millis("2026-09-25T18:30", niamey), LoanDateTime.withDate(loan, LocalDate.of(2026, 9, 25), niamey))
    }

    @Test
    fun `TEST 6 - plusieurs operations le meme jour triees chronologiquement`() {
        val day = millis("2026-09-23T00:00", niamey)
        val times = listOf(18 to 30, 8 to 0, 23 to 0, 12 to 0).map { (h, m) -> LoanDateTime.withTime(day, h, m, niamey) }
        assertEquals(
            listOf("08:00", "12:00", "18:30", "23:00"),
            times.sorted().map { LoanDateTime.toLocalTime(it, niamey).toString() }
        )
    }

    @Test
    fun `TEST 7 - ancienne donnee sans heure (minuit technique) - aucune heure affichee`() {
        assertFalse(LoanDateTime.hasExplicitTime(millis("2026-09-23T00:00", niamey), niamey))
        assertTrue(LoanDateTime.hasExplicitTime(millis("2026-09-23T18:30", niamey), niamey))
        // Ancienne valeur par défaut « maintenant » (secondes/millisecondes non nulles) : heure réelle.
        assertTrue(LoanDateTime.hasExplicitTime(millis("2026-09-23T14:23:17.456", niamey), niamey))
    }

    @Test
    fun `TEST 8 - l heure saisie reste celle du fuseau de l appareil, sans decalage`() {
        for (zone in listOf(niamey, paris, newYork, ZoneId.of("UTC"))) {
            val loan = LoanDateTime.withTime(millis("2026-09-23T00:00", zone), 18, 30, zone)
            assertEquals("$zone", LocalTime.of(18, 30), LoanDateTime.toLocalTime(loan, zone))
        }
        // Le même instant relu à Niamey et Paris (même décalage en septembre, UTC+1/UTC+2 → 1 h
        // d'écart) : c'est un instant ABSOLU, comme Transaction.date — jamais re-décalé en stockage.
        val niameyLoan = millis("2026-09-23T18:30", niamey)
        assertEquals(LocalTime.of(19, 30), LoanDateTime.toLocalTime(niameyLoan, paris))
    }

    @Test
    fun `nowToMinute - secondes et millisecondes a zero`() {
        val now = millis("2026-09-23T14:23:17.456", niamey)
        assertEquals(millis("2026-09-23T14:23", niamey), LoanDateTime.nowToMinute(now, niamey))
    }

    @Test
    fun `comparaison par jour - l heure ne change pas les regles d echeance`() {
        val startEvening = millis("2026-09-23T18:30", niamey)
        assertFalse(LoanDateTime.isBeforeDay(startEvening, millis("2026-09-23T00:00", niamey), niamey))
        assertTrue(LoanDateTime.isBeforeDay(startEvening, millis("2026-09-24T00:00", niamey), niamey))
        assertFalse(LoanDateTime.isBeforeDay(millis("2026-09-23T08:00", niamey), startEvening, niamey))
    }

    @Test(expected = DateTimeException::class)
    fun `heure invalide 25h90 refusee`() {
        LoanDateTime.withTime(millis("2026-09-23T00:00", niamey), 25, 90, niamey)
    }
}
