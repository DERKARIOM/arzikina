package com.naniger.arzikina.presentation.settings

import androidx.annotation.StringRes
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.ThemeMode

/** Même principe que `presentation/accounts/AccountTypeDisplay.kt` : la couche domaine
 * ([ThemeMode]) ne connaît aucune ressource Android, cette conversion reste côté presentation. */
@StringRes
fun ThemeMode.displayTextRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}
