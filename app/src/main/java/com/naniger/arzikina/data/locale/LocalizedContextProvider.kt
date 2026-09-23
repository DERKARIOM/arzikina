package com.naniger.arzikina.data.locale

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.naniger.arzikina.domain.repository.AppLanguageRepository
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fournit un [Context] dont les ressources sont dans la langue choisie par l'utilisateur, pour le
 * code qui s'exécute HORS d'une Activity (BroadcastReceiver, Worker, notifications).
 *
 * Pourquoi c'est nécessaire : sur Android 8 à 12, AppCompat n'applique la langue choisie qu'aux
 * Activities. Le `Context` reçu par un BroadcastReceiver reste dans la langue du téléphone : sans
 * ce wrapper, un rappel d'automatisation serait en anglais alors que l'app est réglée en français
 * (ou l'inverse). Sur Android 13+, le système applique déjà la langue à tout le processus :
 * [localize] renvoie alors le contexte tel quel.
 */
@Singleton
class LocalizedContextProvider @Inject constructor(
    private val appLanguageRepository: AppLanguageRepository
) {

    fun localize(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val languageTag = appLanguageRepository.getSelectedLanguage().languageTag ?: return base
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(languageTag))
        }
        return base.createConfigurationContext(configuration)
    }
}
