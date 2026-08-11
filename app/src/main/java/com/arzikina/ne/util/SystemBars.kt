package com.arzikina.ne.util

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView

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

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightNavigationBars = isLightTheme(activity)
        updateStatusBarIconAppearance(activity, forceLightIcons = false)
    }

    private fun isLightTheme(activity: Activity): Boolean =
        activity.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES

    /**
     * Icônes foncées sur fond clair, claires sur fond sombre — sauf si
     * [forceLightIcons] est vrai, cas du Dashboard : son en-tête a un fond
     * brun/ambré FIXE (voir colors.xml, `arzikina_dashboard_header_*`),
     * volontairement identique en thème clair et sombre, et qui s'étend
     * sous la barre de statut désormais transparente (voir
     * `fragment_dashboard.xml`, `dashboardHeaderBackground`) — les icônes y
     * restent donc TOUJOURS claires, quel que soit le thème de l'app.
     * À rappeler à chaque changement de destination (voir MainActivity) et
     * à chaque changement de thème (un `recreate()` de l'Activity rappelle
     * `configure()`, qui repasse ici avec `forceLightIcons = false`).
     */
    fun updateStatusBarIconAppearance(activity: Activity, forceLightIcons: Boolean) {
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = if (forceLightIcons) false else isLightTheme(activity)
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
     *
     * [topInsetView] absorbe AUSSI l'inset du bas, mais seulement quand
     * [isBottomNavHidden] répond `true` (écrans sans Bottom Navigation
     * visible, ex. Connexion/Inscription : [topInsetView] touche alors
     * directement le bas de l'écran et doit faire ce que la barre, masquée,
     * ne peut plus faire).
     *
     * [isTopInsetTransparent], lui, désactive le padding du HAUT (cas du
     * Dashboard, voir [updateStatusBarIconAppearance]) : l'écran gère alors
     * lui-même cet inset en interne (voir `DashboardFragment`, qui pousse
     * uniquement son en-tête avatar/nom sous la barre, tout en laissant son
     * fond dégradé s'étendre derrière) plutôt que le conteneur de
     * navigation partagé — sans quoi TOUS les écrans perdraient leur
     * padding du haut, pas seulement le Dashboard.
     *
     * Les deux lambdas sont ré-évaluées à CHAQUE distribution d'insets, pas
     * figées une fois pour toutes : voir MainActivity, qui force cette
     * redistribution via `requestApplyInsets` à chaque changement de
     * destination.
     *
     * IMPORTANT : un seul listener d'insets peut être actif à la fois sur
     * une même vue (`ViewCompat.setOnApplyWindowInsetsListener` REMPLACE
     * tout listener déjà posé sur cette vue, il ne s'additionne pas).
     * [topInsetView] et [bottomInsetView] doivent donc rester deux vues
     * distinctes, et aucun autre appel ne doit reposer un listener sur l'une
     * d'elles ailleurs dans le code — sans quoi le padding posé ici serait
     * silencieusement annulé (bug réel corrigé : le padding du haut n'était
     * plus jamais appliqué, sur aucun écran, parce qu'un second listener
     * posé juste après sur la même vue écrasait celui-ci).
     */
    fun applyInsets(
        topInsetView: View,
        bottomInsetView: View,
        isBottomNavHidden: () -> Boolean,
        isTopInsetTransparent: () -> Boolean
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(topInsetView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = if (isTopInsetTransparent()) 0 else systemBars.top,
                bottom = if (isBottomNavHidden()) systemBars.bottom else 0
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottomInsetView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    /**
     * Applique un padding bas DYNAMIQUE, égal à la hauteur du clavier virtuel (inset IME), sur
     * [scrollView] — afin que son dernier contenu (et le bouton d'action principal, désormais
     * TOUJOURS placé À L'INTÉRIEUR du scroll dans tous les formulaires de l'app, voir
     * `fragment_loan_form.xml`/`fragment_loan_payment_form.xml` pour les deux derniers écrans
     * harmonisés en ce sens) reste atteignable au-dessus du clavier plutôt que masqué derrière.
     *
     * Appelée automatiquement, pour CHAQUE écran, par [com.arzikina.ne.MainActivity] (voir
     * `registerFragmentLifecycleCallbacks`, qui détecte lui-même le [NestedScrollView] de chaque
     * Fragment affiché) : aucun Fragment n'a besoin d'appeler cette fonction lui-même, ni
     * aujourd'hui ni pour un futur écran — c'est la correction globale demandée (voir instructions
     * projet, "ne pas devoir ajouter manuellement une correction différente dans chaque Fragment").
     *
     * Nécessaire MALGRÉ `android:windowSoftInputMode="adjustResize"` (voir AndroidManifest.xml) :
     * une fois l'app en edge-to-edge (voir [configure]), Android cesse de redimensionner
     * automatiquement le contenu selon le clavier, quelle que soit la valeur de cet attribut
     * (comportement documenté officiellement) — seule la consommation manuelle de
     * `WindowInsetsCompat.Type.ime()` fonctionne encore, exactement ce que fait cette fonction.
     *
     * [scrollView] reçoit son propre listener d'insets, INDÉPENDANT de celui posé par
     * [applyInsets] sur le conteneur de navigation partagé (voir sa doc, "un seul listener actif
     * par vue") : ce sont deux vues distinctes, donc aucun risque d'écrasement mutuel.
     * Volontairement limité à `Type.ime()` (jamais `systemBars()`) : le bas de l'écran est déjà
     * pris en charge ailleurs (padding du conteneur de navigation si la Bottom Navigation est
     * masquée, ou par la Bottom Navigation elle-même sinon, voir [applyInsets]) — additionner aussi
     * `systemBars()` ici doublerait cet espacement.
     *
     * Fait aussi défiler automatiquement le champ actuellement focus au-dessus du clavier dès que
     * celui-ci apparaît (`requestRectangleOnScreen`), plutôt que de compter uniquement sur le
     * défilement automatique intégré de [NestedScrollView] au moment du focus initial.
     */
    fun applyImeAwarePadding(scrollView: NestedScrollView) {
        val initialPaddingBottom = scrollView.paddingBottom
        scrollView.clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.updatePadding(bottom = initialPaddingBottom + imeBottom)
            if (imeBottom > 0) {
                view.post {
                    view.findFocus()?.let { focusedView ->
                        focusedView.requestRectangleOnScreen(Rect(0, 0, focusedView.width, focusedView.height), false)
                    }
                }
            }
            insets
        }
    }
}
