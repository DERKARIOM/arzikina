package com.naniger.arzikina.data.locale

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.naniger.arzikina.domain.model.AppLanguage
import com.naniger.arzikina.domain.model.AppLanguageResolver
import com.naniger.arzikina.domain.repository.AppLanguageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applique et persiste la langue de l'interface avec l'API officielle « langue par application »
 * d'Android. Deux chemins, parce que le système ne fournit pas le même service selon la version :
 *
 * - **Android 13+ (API 33)** : le `LocaleManager` du système est la SOURCE DE VÉRITÉ. Il persiste
 *   lui-même le choix, l'applique à tout le processus (Activities, mais aussi notifications et
 *   workers) et permet à l'utilisateur de changer la langue d'Arzikina depuis les réglages Android
 *   (Applications → Arzikina → Langue, grâce à `res/xml/locales_config.xml`). On l'appelle
 *   directement : `AppCompatDelegate.setApplicationLocales` ne fait rien sur API 33+ tant
 *   qu'aucune Activity n'existe, ce qui est le cas dans [restoreOnStartup].
 * - **Android 8 à 12 (API 26–32)** : pas de service système. Le choix est persisté dans le
 *   DataStore de préférences (par appareil) et appliqué aux Activities via
 *   [AppCompatDelegate.setApplicationLocales]. On n'utilise volontairement PAS l'option
 *   `autoStoreLocales` d'AppCompat : sa valeur n'est lisible qu'une fois une Activity créée, alors
 *   que les notifications d'automatisation peuvent être émises processus « à froid » (voir
 *   [LocalizedContextProvider]). Notre propre copie est lue dès `Application.onCreate`.
 *
 * Le choix est TOUJOURS écrit dans le DataStore, y compris sur Android 13+ : cela permet de le
 * migrer vers le `LocaleManager` quand l'appareil passe d'Android 12 à 13 (voir [restoreOnStartup]).
 */
@Singleton
class AppLanguageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) : AppLanguageRepository {

    private object Keys {
        val APP_LANGUAGE = stringPreferencesKey("app_language")

        /** Posée au premier démarrage sous Android 13+ : la migration DataStore → LocaleManager
         *  ne doit se faire qu'une fois, sinon elle écraserait un changement fait ensuite depuis
         *  les réglages Android. */
        val FRAMEWORK_MIGRATION_DONE = booleanPreferencesKey("app_language_framework_migration_done")
    }

    /** Choix courant pour API < 33 (sur API 33+, [readFrameworkSelection] fait foi). Initialisé par
     *  [restoreOnStartup], donc disponible dès le démarrage du processus, même sans Activity. */
    @Volatile
    private var cachedSelection: AppLanguage = AppLanguage.SYSTEM

    override fun getSelectedLanguage(): AppLanguage =
        if (isFrameworkManaged()) readFrameworkSelection() else cachedSelection

    override fun getEffectiveLanguage(): AppLanguage =
        AppLanguageResolver.resolve(getSelectedLanguage(), systemLanguageTags())

    override suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[Keys.APP_LANGUAGE] = language.name }
        cachedSelection = language
        withContext(Dispatchers.Main.immediate) { apply(language) }
    }

    /**
     * Lecture BLOQUANTE volontaire (même choix que `ArzikinaApplication.applyStoredThemeMode`) : la
     * langue doit être connue avant la création de la première Activity, sinon le premier écran
     * s'afficherait dans la mauvaise langue puis serait reconstruit. Le fichier DataStore est
     * minuscule et déjà chargé par la lecture du thème qui précède : coût négligeable.
     */
    override fun restoreOnStartup() {
        val preferences = runBlocking { dataStore.data.first() }
        val stored = preferences[Keys.APP_LANGUAGE]
            ?.let { name -> AppLanguage.entries.firstOrNull { it.name == name } }
            ?: AppLanguage.SYSTEM

        if (isFrameworkManaged()) {
            if (preferences[Keys.FRAMEWORK_MIGRATION_DONE] != true) {
                if (stored != AppLanguage.SYSTEM && readFrameworkSelection() == AppLanguage.SYSTEM) {
                    apply(stored)
                }
                runBlocking { dataStore.edit { it[Keys.FRAMEWORK_MIGRATION_DONE] = true } }
            }
        } else {
            cachedSelection = stored
            if (stored != AppLanguage.SYSTEM) apply(stored)
        }
    }

    private fun apply(language: AppLanguage) {
        if (isFrameworkManaged()) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.forLanguageTags(language.languageTag.orEmpty())
        } else {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(language.languageTag.orEmpty())
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun readFrameworkSelection(): AppLanguage {
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
        if (locales == null || locales.isEmpty) return AppLanguage.SYSTEM
        return AppLanguage.fromLanguageTag(locales[0].toLanguageTag()) ?: AppLanguage.SYSTEM
    }

    /** Langues du TÉLÉPHONE (jamais celles imposées à l'app), par ordre de préférence. */
    private fun systemLanguageTags(): List<String> {
        val locales = LocaleManagerCompat.getSystemLocales(context)
        return (0 until locales.size()).mapNotNull { index -> locales[index]?.toLanguageTag() }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    private fun isFrameworkManaged(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
