package com.naniger.arzikina.util

import android.content.Context
import androidx.core.os.ConfigurationCompat
import com.naniger.arzikina.domain.model.AppLanguage
import java.util.Locale

/**
 * Locale à utiliser pour FORMATER du texte (dates, noms de mois, tailles de fichier…) dans la
 * langue réellement affichée par l'interface.
 *
 * Pourquoi pas simplement `Locale.getDefault()` ou la locale de la configuration : quand le
 * téléphone est dans une langue qu'Arzikina ne propose pas (ex. haoussa), l'interface s'affiche en
 * français (repli de `res/values/`), mais la configuration reste en haoussa. Formater les dates avec
 * cette locale mélangerait des mois en haoussa dans une interface française.
 *
 * Règle, identique à la résolution des ressources d'Android : première locale de la configuration
 * dont la langue est supportée (on garde sa région, ex. `en-NG`), sinon [AppLanguage.DEFAULT].
 */
fun Context.appLocale(): Locale {
    val locales = ConfigurationCompat.getLocales(resources.configuration)
    return resolveAppLocale((0 until locales.size()).mapNotNull { locales[it] })
}

/** Cœur testable de [appLocale], sans dépendance Android. */
internal fun resolveAppLocale(configurationLocales: List<Locale>): Locale =
    configurationLocales.firstOrNull { AppLanguage.fromLanguageTag(it.toLanguageTag()) != null }
        ?: Locale.forLanguageTag(requireNotNull(AppLanguage.DEFAULT.languageTag))
