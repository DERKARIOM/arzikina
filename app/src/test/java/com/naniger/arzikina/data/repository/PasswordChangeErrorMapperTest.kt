package com.naniger.arzikina.data.repository

import com.naniger.arzikina.domain.model.PasswordChangeError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Traduction des réponses d'erreur de `set_password.php` (voir [PasswordChangeErrorMapper]). */
class PasswordChangeErrorMapperTest {

    private fun body(code: String) = """{"error":"$code","message":"x"}"""

    @Test
    fun `un mot de passe trop court ou trop long est refuse`() {
        assertEquals(PasswordChangeError.InvalidNewPassword, PasswordChangeErrorMapper.map(400, body("password_too_short"), false))
        assertEquals(PasswordChangeError.InvalidNewPassword, PasswordChangeErrorMapper.map(400, body("password_too_long"), true))
    }

    @Test
    fun `un compte Google different est distingue d'un jeton invalide`() {
        assertEquals(PasswordChangeError.GoogleIdentityMismatch, PasswordChangeErrorMapper.map(403, body("google_identity_mismatch"), true))
        assertEquals(PasswordChangeError.InvalidGoogleToken, PasswordChangeErrorMapper.map(401, body("invalid_google_token"), true))
    }

    @Test
    fun `un 401 avec le mot de passe actuel signifie mot de passe incorrect`() {
        assertEquals(PasswordChangeError.IncorrectCurrentPassword, PasswordChangeErrorMapper.map(401, body("invalid_credentials"), false))
    }

    @Test
    fun `un 401 de session n'est jamais pris pour un mot de passe ou un jeton Google faux`() {
        assertEquals(PasswordChangeError.NoActiveSession, PasswordChangeErrorMapper.map(401, body("invalid_token"), false))
        assertEquals(PasswordChangeError.NoActiveSession, PasswordChangeErrorMapper.map(401, body("missing_token"), true))
    }

    @Test
    fun `une erreur google_email_not_verified est un jeton Google refuse`() {
        assertEquals(PasswordChangeError.InvalidGoogleToken, PasswordChangeErrorMapper.map(401, body("google_email_not_verified"), true))
    }

    @Test
    fun `les autres erreurs sont des erreurs serveur et conservent le corps`() {
        val error = PasswordChangeErrorMapper.map(500, body("server_error"), false)
        assertTrue(error is PasswordChangeError.ServerError)
        assertEquals(body("server_error"), (error as PasswordChangeError.ServerError).message)
    }
}
