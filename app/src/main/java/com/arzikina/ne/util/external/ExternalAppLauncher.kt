package com.arzikina.ne.util.external

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application Android externe détectée via [android.content.pm.PackageManager] — [icon] est
 * `null` si le système n'a pas pu la fournir (rare, mais possible sur certains OEM), auquel cas
 * l'appelant doit se rabattre sur une icône générique (voir section sécurité/UX, "icône Mobile
 * Money générique" pour une application non installée — même logique de repli).
 */
data class ExternalAppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

/**
 * Point d'accès UNIQUE au `PackageManager` pour la fonctionnalité "Lancer l'application Mobile
 * Money depuis un compte" (voir cahier des charges, section 15 : "éviter de mettre toute la
 * logique PackageManager directement dans le Fragment") — centralise la détection, la
 * vérification d'installation, la récupération du nom/icône lisibles et le lancement, avec
 * gestion d'erreurs systématique (jamais de `Exception` non rattrapée jusqu'à l'appelant).
 *
 * DÉLIBÉRÉMENT hors du domaine (pas d'interface dans `domain/repository`, contrairement à
 * [com.arzikina.ne.domain.repository.BiometricAuthenticator]) : cette classe ne fait qu'exposer
 * des types Android irréductibles ([Drawable] pour l'icône, [Intent] en interne) — une
 * abstraction de domaine autour d'elle obligerait soit à faire fuiter ces types dans le domaine,
 * soit à construire une couche de traduction (icône en `ByteArray`, etc.) qui n'apporterait rien
 * dans une application mono-plateforme Android. Même statut que `data/security/CardCipher` ou
 * `util/SystemBars` : un utilitaire technique, pas une capacité métier substituable. Voir
 * `domain/model/Account.mobileMoneyPackageName`, qui lui reste une simple chaîne — la SEULE
 * donnée que la couche domaine connaît de cette fonctionnalité.
 *
 * Injecté avec `@ApplicationContext` (jamais une `Activity`, voir la même règle documentée sur
 * [com.arzikina.ne.domain.repository.BiometricAuthenticator]) : aucune méthode ici n'a besoin
 * d'un hôte précis, ce qui la rend sûre à injecter directement dans un ViewModel (contrairement à
 * `BiometricAuthenticator.authenticate`, qui exige une injection par champ dans le Fragment).
 * [launch] utilise `FLAG_ACTIVITY_NEW_TASK` pour cette même raison (démarrage depuis un Contexte
 * d'application, pas une Activity) — comportement standard et suffisant pour "ouvrir une autre
 * application depuis la mienne" (même mécanisme qu'un partage/deep-link), sans les complications
 * de gestion de cycle de vie qu'exigerait le passage d'une `Activity`.
 *
 * Nécessite le bloc `<queries>` déclaré dans `AndroidManifest.xml` (intent `MAIN`/`LAUNCHER`) —
 * voir sa doc pour le raisonnement (visibilité des packages, Android 11+).
 */
@Singleton
class ExternalAppLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val packageManager get() = context.packageManager

    /** `true` si [packageName] correspond à une application installée (peu importe si elle a une
     * activité de lancement — voir [launch] pour ce cas distinct, section "Cas particuliers"). */
    fun isInstalled(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return runCatching { packageManager.getApplicationInfo(packageName, 0) }.isSuccess
    }

    /** `null` si [packageName] n'est pas installé (voir [isInstalled]) — jamais d'exception
     * propagée à l'appelant même en cas d'erreur du `PackageManager`. */
    fun getAppInfo(packageName: String): ExternalAppInfo? {
        if (packageName.isBlank()) return null
        val appInfo = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull() ?: return null
        val label = runCatching { packageManager.getApplicationLabel(appInfo).toString() }.getOrDefault(packageName)
        val icon = runCatching { packageManager.getApplicationIcon(appInfo) }.getOrNull()
        return ExternalAppInfo(packageName, label, icon)
    }

    /**
     * Toutes les applications installées avec une icône de lancement (voir le `<queries>` du
     * manifeste — c'est exactement le même filtre qui rend ces applications VISIBLES à ce
     * `PackageManager`). Volontairement SANS liste de packages Mobile Money codée en dur (voir
     * cahier des charges, section 9) : le tri/filtrage éventuel par mot-clé appartient à l'écran
     * de sélection (couche presentation), pas à ce composant technique.
     *
     * Arzikina s'exclut elle-même (aucune raison de se proposer comme "application Mobile Money"
     * de l'un de ses propres comptes) ; `distinctBy` élimine les doublons qu'une application avec
     * PLUSIEURS activités `MAIN`/`LAUNCHER` produirait sinon.
     */
    fun listLaunchableApps(): List<ExternalAppInfo> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = runCatching { packageManager.queryIntentActivities(launcherIntent, 0) }.getOrDefault(emptyList())
        return resolveInfos
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) return@mapNotNull null
                val label = runCatching { resolveInfo.loadLabel(packageManager).toString() }.getOrDefault(packageName)
                val icon = runCatching { resolveInfo.loadIcon(packageManager) }.getOrNull()
                ExternalAppInfo(packageName, label, icon)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * `false` sans jamais planter (voir section sécurité, "ne jamais exécuter directement une
     * chaîne arbitraire comme une commande système" — [packageName] ne sert QU'à interroger le
     * `PackageManager`/construire un [Intent] explicite via les API officielles, jamais exécuté
     * autrement) si :
     * - [packageName] est vide ;
     * - l'application n'est pas installée ([android.content.pm.PackageManager.getLaunchIntentForPackage]
     *   renvoie alors `null` directement) ;
     * - l'application est installée mais SANS activité de lancement (même cas, `null` — voir
     *   cahier des charges, "Application installée mais sans activité de lancement") ;
     * - le démarrage échoue pour toute autre raison système (`SecurityException`,
     *   `ActivityNotFoundException`...).
     */
    fun launch(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(launchIntent) }.isSuccess
    }
}
