package com.naniger.arzikina.domain.issuedreceipt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptNumberTest {

    @Test
    fun `format pads the sequence to three digits`() {
        assertEquals("Recu-2026-001", ReceiptNumber.format(2026, 1))
        assertEquals("Recu-2026-042", ReceiptNumber.format(2026, 42))
        assertEquals("Recu-2026-1234", ReceiptNumber.format(2026, 1234))
    }

    @Test
    fun `next starts at 001 for a new year`() {
        assertEquals("Recu-2027-001", ReceiptNumber.next(2027, listOf("Recu-2026-009", "Recu-2026-010")))
        assertEquals("Recu-2026-001", ReceiptNumber.next(2026, emptyList()))
    }

    @Test
    fun `next uses the highest sequence plus one and never fills gaps`() {
        assertEquals("Recu-2026-008", ReceiptNumber.next(2026, listOf("Recu-2026-001", "Recu-2026-007", "Recu-2026-003")))
    }

    @Test
    fun `next ignores custom numbers that do not follow the generated format`() {
        assertEquals("Recu-2026-003", ReceiptNumber.next(2026, listOf("Recu-2026-002", "FAC-77", "Recu-2026-abc", "Recu-2026-")))
    }

    @Test
    fun `parseSequence only reads numbers of the requested year`() {
        assertEquals(12, ReceiptNumber.parseSequence("Recu-2026-012", 2026))
        assertNull(ReceiptNumber.parseSequence("Recu-2025-012", 2026))
        assertNull(ReceiptNumber.parseSequence("Recu-2026-12x", 2026))
        assertNull(ReceiptNumber.parseSequence("Recu-2026-1234567890", 2026))
    }

    @Test
    fun `isValid accepts safe numbers and rejects unsafe ones`() {
        assertTrue(ReceiptNumber.isValid("Recu-2026-001"))
        assertTrue(ReceiptNumber.isValid("FAC-2026.7"))
        assertFalse(ReceiptNumber.isValid("FAC/2026/7"))
        assertFalse(ReceiptNumber.isValid(""))
        assertFalse(ReceiptNumber.isValid("-abc"))
        assertFalse(ReceiptNumber.isValid("a b"))
        assertFalse(ReceiptNumber.isValid("a".repeat(ReceiptNumber.MAX_LENGTH + 1)))
        assertTrue(ReceiptNumber.isValid("a".repeat(ReceiptNumber.MAX_LENGTH)))
    }

    @Test
    fun `pdfFileName is a safe file name`() {
        assertEquals("Recu-2026-001.pdf", ReceiptNumber.pdfFileName("Recu-2026-001"))
        assertEquals("FAC-2026.7.pdf", ReceiptNumber.pdfFileName("FAC-2026.7"))
    }

    @Test
    fun `disambiguated appends the first free suffix`() {
        assertEquals("Recu-2026-004-2", ReceiptNumber.disambiguated("Recu-2026-004", listOf("Recu-2026-004")))
        assertEquals(
            "Recu-2026-004-3",
            ReceiptNumber.disambiguated("Recu-2026-004", listOf("Recu-2026-004", "recu-2026-004-2")),
        )
    }

    @Test
    fun `disambiguated stays valid and within the max length`() {
        val long = "a".repeat(ReceiptNumber.MAX_LENGTH)
        val result = ReceiptNumber.disambiguated(long, listOf(long))
        assertEquals(ReceiptNumber.MAX_LENGTH, result.length)
        assertTrue(ReceiptNumber.isValid(result))
        assertTrue(result.endsWith("-2"))
    }

    @Test
    fun `loses picks the most recent creation then the greatest syncId`() {
        fun claim(createdAt: Long, syncId: String, deleted: Boolean = false) =
            ReceiptNumber.Claim(deleted, createdAt, syncId)
        assertTrue(ReceiptNumber.loses(claim(200, "a"), claim(100, "z")))
        assertFalse(ReceiptNumber.loses(claim(100, "z"), claim(200, "a")))
        assertTrue(ReceiptNumber.loses(claim(100, "b"), claim(100, "a")))
        assertFalse(ReceiptNumber.loses(claim(100, "a"), claim(100, "b")))
    }

    @Test
    fun `a deleted receipt always loses against an active one`() {
        val old = ReceiptNumber.Claim(deleted = false, createdAt = 500, syncId = "z")
        val newDeleted = ReceiptNumber.Claim(deleted = true, createdAt = 100, syncId = "a")
        assertTrue(ReceiptNumber.loses(newDeleted, old))
        assertFalse(ReceiptNumber.loses(old, newDeleted))
    }

    @Test
    fun `the verdict is antisymmetric so every device designates the same loser`() {
        val a = ReceiptNumber.Claim(false, 100, "a")
        val b = ReceiptNumber.Claim(false, 100, "b")
        assertTrue(ReceiptNumber.loses(a, b) != ReceiptNumber.loses(b, a))
    }


    @Test
    fun `isAdjusted recognises only generated numbers with a disambiguation suffix`() {
        assertTrue(ReceiptNumber.isAdjusted("Recu-2026-005-2"))
        assertTrue(ReceiptNumber.isAdjusted(ReceiptNumber.disambiguated("Recu-2026-005", listOf("Recu-2026-005"))))
        assertTrue(!ReceiptNumber.isAdjusted("Recu-2026-005"))
        assertTrue(!ReceiptNumber.isAdjusted("FACT-12-2"))
        assertTrue(!ReceiptNumber.isAdjusted("Recu-26-005-2"))
    }
}
