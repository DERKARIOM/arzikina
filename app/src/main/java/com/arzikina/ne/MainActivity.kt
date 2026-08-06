package com.arzikina.ne

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.arzikina.ne.databinding.ActivityMainBinding
import com.arzikina.ne.domain.model.ThemeMode
import com.arzikina.ne.domain.repository.UserPreferencesRepository
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

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyStoredThemeMode()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        binding.bottomNavigation.setupWithNavController(navHostFragment.navController)
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
}
