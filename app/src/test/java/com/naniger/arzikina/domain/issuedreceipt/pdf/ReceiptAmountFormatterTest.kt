package com.naniger.arzikina.domain.issuedreceipt.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptAmountFormatterTest {
    private val nb = ' '

    @Test fun groupsThousandsWithoutDecimals() {
        assertEquals("10${nb}000${nb}XOF", ReceiptAmountFormatter.format(1_000_000, "XOF", false))
        assertEquals("0${nb}XOF", ReceiptAmountFormatter.format(0, "XOF", false))
        assertEquals("999", ReceiptAmountFormatter.number(99_900, false))
        assertEquals("1${nb}000", ReceiptAmountFormatter.number(100_000, false))
    }

    @Test fun showsTwoDecimalsWhenAsked() {
        assertEquals("1${nb}500,50${nb}EUR", ReceiptAmountFormatter.format(150_050, "EUR", true))
        assertEquals("0,05", ReceiptAmountFormatter.number(5, true))
        assertEquals("10,00", ReceiptAmountFormatter.number(1000, true))
    }

    @Test fun quantities() {
        assertEquals("1", ReceiptAmountFormatter.quantity(1000))
        assertEquals("2,5", ReceiptAmountFormatter.quantity(2500))
        assertEquals("1,25", ReceiptAmountFormatter.quantity(1250))
        assertEquals("0,001", ReceiptAmountFormatter.quantity(1))
        assertEquals("1${nb}000", ReceiptAmountFormatter.quantity(1_000_000))
    }

    @Test fun percents() {
        assertEquals("18${nb}%", ReceiptAmountFormatter.percent(1800))
        assertEquals("7,5${nb}%", ReceiptAmountFormatter.percent(750))
        assertEquals("0,05${nb}%", ReceiptAmountFormatter.percent(5))
        assertEquals("0${nb}%", ReceiptAmountFormatter.percent(0))
    }
}
