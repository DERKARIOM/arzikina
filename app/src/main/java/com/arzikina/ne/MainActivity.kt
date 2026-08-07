package com.arzikina.ne

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.arzikina.ne.databinding.ActivityMainBinding
import com.arzikina.ne.domain.model.ThemeMode
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.UserPreferencesRepository
import com.arzikina.ne.util.SystemBars
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Point d'entrée unique de l'application : une seule Activity qui héberge
 * toute la navigation via [NavHostFragment] (voir `res/navigation/nav_graph.xml`)
 * et une [com.google.android.material.bottomnavigation.BottomNavigationView].
 *
 * Interface entièrement en Views + Material Components (pas de Jetpack
 * Compose, voir instructions projet) : cette Activity ne fait qu'assembler
 * le thème, la navigation et l'injection Hilt, sans logique métier.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var sessionManager: SessionManager

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyStoredThemeMode()
        SystemBars.configure(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

        // navController doit exister AVANT cet appel : isTopInsetTransparent
        // interroge navController.currentDestination à chaque distribution
        // d'insets (voir SystemBars.applyInsets).
        SystemBars.applyInsets(
            topInsetView = binding.navHostFragment,
            bottomInsetView = binding.bottomNavigation,
            isBottomNavHidden = { binding.bottomNavigation.visibility != View.VISIBLE },
            isTopInsetTransparent = { navController.currentDestination?.id == R.id.dashboardFragment }
        )

        // Graphe inflaté ici (plutôt que via app:navGraph en XML, voir
        // activity_main.xml) : c'est ce qui permet de choisir le
        // startDestination au runtime, selon qu'une session existe déjà.
        val startDestinationId = resolveStartDestination()
        navController.graph = navController.navInflater.inflate(R.navigation.nav_graph).apply {
            setStartDestination(startDestinationId)
        }
        SystemBars.updateStatusBarIconAppearance(this, forceLightIcons = startDestinationId == R.id.dashboardFragment)

        binding.bottomNavigation.setupWithNavController(navController)

        // Désactive la pastille d'indicateur actif dessinée par défaut par Material
        // (un aplat arrondi derrière l'icône) : la sélection ne doit se traduire que
        // par un changement de couleur de l'icône et du texte (voir
        // app:itemIconTint / app:itemTextColor dans activity_main.xml), sans aucune
        // forme derrière.
        binding.bottomNavigation.isItemActiveIndicatorEnabled = false

        // Connexion et Inscription n'ont pas leur place dans les onglets
        // principaux : pas d'item de menu correspondant, et visuellement
        // l'app doit s'y présenter comme un espace à part entière (voir
        // fragment_login.xml / fragment_register.xml, écrans plein cadre).
        // `requestApplyInsets` force le listener de SystemBars.applyInsets à
        // se recalculer immédiatement (les insets système eux-mêmes n'ont pas
        // changé, seule la visibilité de la Bottom Navigation a changé, ce
        // qui doit faire basculer isBottomNavHidden() : sans cet appel, le
        // listener ne serait pas rappelé).
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigation.visibility =
                if (destination.id in AUTH_DESTINATION_IDS) View.GONE else View.VISIBLE
            ViewCompat.requestApplyInsets(binding.root)
            // Icônes de la status bar claires en permanence sur le Dashboard
            // (fond brun/ambré fixe qui s'étend sous elle, voir
            // SystemBars.updateStatusBarIconAppearance), thème-dépendantes
            // partout ailleurs.
            SystemBars.updateStatusBarIconAppearance(
                this,
                forceLightIcons = destination.id == R.id.dashboardFragment
            )
        }
    }

    /**
     * Lecture bloquante ponctuelle de la session locale (DataStore, source
     * locale, quasi instantanée — même justification que
     * [applyStoredThemeMode] ci-dessous) : AVANT le premier affichage, pour
     * ne jamais montrer un Dashboard puis rediriger vers Connexion (ou
     * l'inverse) une fois l'app déjà visible.
     */
    private fun resolveStartDestination(): Int {
        val hasSession = runBlocking { sessionManager.getCurrentUserIdOnce() != null }
        return if (hasSession) R.id.dashboardFragment else R.id.loginFragment
    }

    /**
     * Applique la préférence de thème (Système/Clair/Sombre, voir Paramètres)
     * avant l'inflation des vues, pour éviter un changement visible après
     * affichage. Lecture bloquante ponctuelle de DataStore (source locale,
     * quasi instantanée) : pas de dépendance Compose ni d'état réactif ici,
     * contrairement à l'ancienne implémentation `ArzikinaTheme(darkTheme = ...)`.
     * L'écran Paramètres devra appeler `recreate()` après un changement pour
     * que le nouveau mode s'applique immédiatement à l'écran déjà affiché.
     */
    private fun applyStoredThemeMode() {
        val preferences = runBlocking { userPreferencesRepository.observePreferences().first() }
        val nightMode = when (preferences.themeMode) {
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    private companion object {
        /** Destinations sans Bottom Navigation ni onglet correspondant (voir plus haut). */
        val AUTH_DESTINATION_IDS = setOf(R.id.loginFragment, R.id.registerFragment, R.id.forgotPasswordFragment)
    }
}
