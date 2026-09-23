package com.naniger.arzikina.presentation.settings

import androidx.annotation.StringRes
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.AppLanguage

/**
 * Même principe que [ThemeModeDisplay] : le domaine ([AppLanguage]) ne connaît aucune ressource
 * Android.
 *
 * Le nom d'une langue est toujours affiché DANS cette langue (« Français », « English »), quelle
 * que soit la langue de l'interface : c'est la convention d'Android et de tous les grands
 * services, pour qu'un utilisateur qui s'est trompé de langue retrouve toujours la sienne. Ces
 * chaînes sont donc marquées `translatable="false"` dans `strings.xml`.
 */
@StringRes
fun AppLanguage.displayNameRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.FRENCH -> R.string.language_name_french
    AppLanguage.ENGLISH -> R.string.language_name_english
}

/** Libellé d'une option du sélecteur (avec son drapeau), voir [showLanguagePicker]. */
@StringRes
fun AppLanguage.pickerOptionRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.settings_language_option_system
    AppLanguage.FRENCH -> R.string.settings_language_option_french
    AppLanguage.ENGLISH -> R.string.settings_language_option_english
}
