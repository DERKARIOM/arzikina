package com.naniger.arzikina.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indique si l'app a été installée par Google Play (y compris via le partage interne d'applications
 * ou une piste de test, qui passent eux aussi par le Play Store).
 *
 * In-App Updates ne fonctionne QUE pour une installation Play : pour un APK installé à la main,
 * depuis Android Studio ou un autre magasin, on ne contacte même pas Play (aucune fausse
 * proposition, aucun appel inutile au démarrage). La bibliothèque Play refuserait de toute façon
 * (`UPDATE_NOT_AVAILABLE` ou erreur `ERROR_APP_NOT_OWNED`) : ce contrôle n'est qu'un raccourci.
 *
 * Calculé une seule fois par processus : la source d'installation ne change pas en cours de route.
 */
@Singleton
class PlayStoreInstallSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val isInstalledFromPlayStore: Boolean by lazy { installerPackageName() == PLAY_STORE_PACKAGE }

    private fun installerPackageName(): String? = runCatching {
        val packageManager = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstallerPackageName(context.packageName)
        }
    }.getOrElse { error ->
        if (error is PackageManager.NameNotFoundException || error is IllegalArgumentException) null else throw error
    }

    private companion object {
        const val PLAY_STORE_PACKAGE = "com.android.vending"
    }
}
