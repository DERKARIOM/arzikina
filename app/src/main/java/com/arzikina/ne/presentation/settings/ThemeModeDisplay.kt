package com.arzikina.ne.presentation.settings

import androidx.annotation.StringRes
import com.arzikina.ne.R
import com.arzikina.ne.domain.model.ThemeMode

/** Même principe que `presentation/accounts/AccountTypeDisplay.kt` : la couche domaine
 * ([ThemeMode]) ne connaît aucune ressource Android, cette conversion reste côté presentation. */
@StringRes
fun ThemeMode.displayTextRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}
