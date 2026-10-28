package com.naniger.arzikina.domain.model

/** Mode d'utilisation FAISANT FOI côté serveur après un choix, et « le serveur détient déjà des comptes ». */
data class UsageModeSelection(val mode: UsageMode, val hasServerAccounts: Boolean)

/** Résultat de [com.naniger.arzikina.domain.repository.UnifiedAuthRepository.completeUsageModeSelection]. */
sealed class UsageModeSelectionResult {
    data object Success : UsageModeSelectionResult()
    data class Failure(val error: UnifiedAuthError) : UsageModeSelectionResult()
}
