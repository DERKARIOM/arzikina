package com.naniger.arzikina.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class IssuedReceiptLikePatternTest {

    @Test
    fun `empty query matches everything`() {
        assertEquals("%%", IssuedReceiptRepositoryImpl.likePattern("  "))
    }

    @Test
    fun `plain text is wrapped and trimmed`() {
        assertEquals("%Boubé%", IssuedReceiptRepositoryImpl.likePattern(" Boubé "))
    }

    @Test
    fun `LIKE wildcards typed by the user are escaped`() {
        assertEquals("%100\\%%", IssuedReceiptRepositoryImpl.likePattern("100%"))
        assertEquals("%a\\_b%", IssuedReceiptRepositoryImpl.likePattern("a_b"))
        assertEquals("%a\\\\b%", IssuedReceiptRepositoryImpl.likePattern("a\\b"))
    }
}
