package com.naniger.arzikina.domain.issuedreceipt.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextWrapperTest {
    private val style = PdfTextStyle(10f, PdfFont.REGULAR, 0)
    private val measurer = PdfTextMeasurer { text, s -> text.length * s.sizePt * 0.5f } // 5 pt par caractère

    @Test fun wrapsAtSpaces() {
        assertEquals(listOf("aaaa bbbb", "cccc"), PdfTextWrapper.wrap("aaaa bbbb cccc", 50f, style, measurer))
    }

    @Test fun keepsExplicitLineBreaksAndBlankLines() {
        assertEquals(listOf("a", "", "b"), PdfTextWrapper.wrap("a\n\nb", 100f, style, measurer))
        assertEquals(listOf("a", "b"), PdfTextWrapper.wrap("a\r\nb", 100f, style, measurer))
    }

    @Test fun breaksWordsLongerThanTheLine() {
        val lines = PdfTextWrapper.wrap("x".repeat(25), 50f, style, measurer)
        assertTrue(lines.all { measurer.width(it, style) <= 50f })
        assertEquals("x".repeat(25), lines.joinToString(""))
    }

    @Test fun emptyTextGivesOneEmptyLine() {
        assertEquals(listOf(""), PdfTextWrapper.wrap("", 50f, style, measurer))
    }
}
