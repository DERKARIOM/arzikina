package com.naniger.arzikina.presentation.profile

import app.cash.turbine.test
import com.naniger.arzikina.MainDispatcherRule
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.PasswordChangeError
import com.naniger.arzikina.domain.model.PasswordChangeResult
import com.naniger.arzikina.domain.model.PasswordProof
import com.naniger.arzikina.domain.repository.UnifiedAuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** Vérifie [ChangePasswordViewModel] : les deux preuves (mot de passe actuel / Google) et la traduction des échecs. */
class ChangePasswordViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    private val repository: UnifiedAuthRepository = mockk()

    private fun createViewModel(mode: ChangePasswordMode = ChangePasswordMode.CURRENT_PASSWORD) =
        ChangePasswordViewModel(repository).also {
            it.onModeChange(mode)
            it.onNewPasswordChange("nouveau-mdp")
            it.onConfirmPasswordChange("nouveau-mdp")
        }

    @Test
    fun `sans mot de passe actuel, rien n'est envoye`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(R.string.register_error_required_field, viewModel.formState.value.currentPasswordError)
        coVerify(exactly = 0) { repository.changePassword(any(), any()) }
    }

    @Test
    fun `avec le mot de passe actuel, le depot recoit cette preuve et l'ecran se ferme`() = runTest(testDispatcher) {
        coEvery { repository.changePassword(PasswordProof.CurrentPassword("ancien-mdp"), "nouveau-mdp") } returns PasswordChangeResult.Success
        val viewModel = createViewModel()
        viewModel.onCurrentPasswordChange("ancien-mdp")

        viewModel.events.test {
            viewModel.submit()
            advanceUntilIdle()
            assertEquals(ChangePasswordEvent.Changed, awaitItem())
        }
        assertFalse(viewModel.formState.value.isSubmitting)
    }

    @Test
    fun `en mode Google, aucun mot de passe actuel n'est exige et Google est demande avant tout envoi`() = runTest(testDispatcher) {
        val viewModel = createViewModel(ChangePasswordMode.GOOGLE)

        viewModel.events.test {
            viewModel.submit()
            advanceUntilIdle()
            assertEquals(ChangePasswordEvent.RequestGoogleConfirmation, awaitItem())
        }
        coVerify(exactly = 0) { repository.changePassword(any(), any()) }
    }

    @Test
    fun `en mode Google, le jeton obtenu est envoye comme preuve`() = runTest(testDispatcher) {
        coEvery { repository.changePassword(PasswordProof.GoogleIdToken("jeton"), "nouveau-mdp") } returns PasswordChangeResult.Success
        val viewModel = createViewModel(ChangePasswordMode.GOOGLE)

        viewModel.events.test {
            viewModel.onGoogleIdToken("jeton")
            advanceUntilIdle()
            assertEquals(ChangePasswordEvent.Changed, awaitItem())
        }
    }

    @Test
    fun `un mot de passe actuel incorrect est signale sur son champ`() = runTest(testDispatcher) {
        coEvery { repository.changePassword(any(), any()) } returns
            PasswordChangeResult.Failure(PasswordChangeError.IncorrectCurrentPassword)
        val viewModel = createViewModel()
        viewModel.onCurrentPasswordChange("faux")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(R.string.profile_error_current_password_incorrect, viewModel.formState.value.currentPasswordError)
    }

    @Test
    fun `un autre compte Google est signale par un message`() = runTest(testDispatcher) {
        coEvery { repository.changePassword(any(), any()) } returns
            PasswordChangeResult.Failure(PasswordChangeError.GoogleIdentityMismatch)
        val viewModel = createViewModel(ChangePasswordMode.GOOGLE)

        viewModel.events.test {
            viewModel.onGoogleIdToken("jeton")
            advanceUntilIdle()
            assertEquals(ChangePasswordEvent.ShowError(R.string.change_password_error_google_mismatch), awaitItem())
        }
    }

    @Test
    fun `un mot de passe de plus de 128 octets est refuse avant l'envoi`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onCurrentPasswordChange("ancien-mdp")
        val tooLong = "é".repeat(65) // 130 octets en UTF-8
        viewModel.onNewPasswordChange(tooLong)
        viewModel.onConfirmPasswordChange(tooLong)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(R.string.change_password_error_password_too_long, viewModel.formState.value.newPasswordError)
        coVerify(exactly = 0) { repository.changePassword(any(), any()) }
    }
}
