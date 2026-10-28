package com.naniger.arzikina.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le maximum se mesure en octets, comme `strlen` côté serveur (`password_policy.php`). */
class PasswordLimitsTest {

    @Test
    fun `128 octets sont acceptes, 129 refuses`() {
        assertTrue(PasswordLimits.isWithinMax("a".repeat(128)))
        assertFalse(PasswordLimits.isWithinMax("a".repeat(129)))
    }

    @Test
    fun `les caracteres accentues comptent double`() {
        assertTrue(PasswordLimits.isWithinMax("é".repeat(64)))
        assertFalse(PasswordLimits.isWithinMax("é".repeat(65)))
    }
}
