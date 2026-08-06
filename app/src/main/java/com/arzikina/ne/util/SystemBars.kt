package com.arzikina.ne.util

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Gestion centralisée des barres système (barre de statut, barre de
 * navigation) pour que celles-ci, la [com.google.android.material.bottomnavigation.BottomNavigationView]
 * et l'arrière-plan de chaque écran forment une seule surface visuelle
 * continue, en clair comme en sombre — voir instructions projet ("interface
 * visuellement homogène").
 *
 * Principe : plutôt que de peindre les barres système dans une couleur et
 * de la maintenir synchronisée avec celle des écrans (fragile : deux
 * endroits à mettre à jour à chaque changement de thème/couleur), les
 * barres sont rendues transparentes (edge-to-edge). Le fond réellement
 * visible sous les barres est alors celui de l'Activity (`activity_main.xml`)
 * et de chaque écran (`fragment_*.xml`), tous deux `?attr/colorSurface` :
 * une seule couleur, une seule source de vérité, répercutée automatiquement
 * partout si le thème change.
 *
 * Edge-to-edge est activé explicitement ici (plutôt que de compter sur le
 * comportement forcé par défaut d'Android 15+) pour un rendu identique du
 * minSdk au targetSdk du projet.
 */
object SystemBars {

    /**
     * À appeler une fois dans `onCreate`, avant `setContentView` (aucune vue
     * requise : n'agit que sur la [android.view.Window]).
     */
    fun configure(activity: Activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)

        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT

        // Icônes foncées sur fond clair, claires sur fond sombre — recalculé à
        // chaque appel à partir du mode nuit courant. Un changement de thème
        // (voir Paramètres) déclenche un `recreate()` de l'Activity, qui
        // rappelle `configure()` avec la configuration à jour.
        val isLightTheme = activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = isLightTheme
        insetsController.isAppearanceLightNavigationBars = isLightTheme
    }

    /**
     * Décale le *contenu* de [topInsetView] sous la barre de statut et celui
     * de [bottomInsetView] au-dessus de la barre de navigation système, sans
     * réduire la taille de ces vues ni interrompre leur arrière-plan (qui
     * continue de s'étendre sous les barres, désormais transparentes) :
     * seul du padding est appliqué, jamais de marge ni de redimensionnement.
     *
     * Un seul appel dans `MainActivity` couvre tous les écrans (voir
     * `activity_main.xml`) : [topInsetView] est le conteneur de navigation
     * (host de tous les Fragments), [bottomInsetView] la Bottom Navigation.
     */
    fun applyInsets(topInsetView: View, bottomInsetView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(topInsetView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottomInsetView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }
}
