package com.arzikina.ne

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
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
        setUpKeyboardAwareScrolling()

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

        setUpBottomNavigation(navController)

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
            // Ne surligne un item de la Bottom Navigation QUE si la destination courante EST
            // littéralement l'un des 5 onglets (voir TAB_DESTINATION_IDS) : un écran secondaire
            // (formulaire, détail, Budget/Catégories/Paramètres depuis "Autre"...) laisse
            // volontairement l'onglet déjà sélectionné en surbrillance, sans qu'aucun ne
            // corresponde à sa propre destination (voir aussi accountsFromDashboardFragment
            // dans nav_graph.xml, qui exploite déjà ce principe pour le raccourci du Dashboard).
            if (destination.id in TAB_DESTINATION_IDS) {
                binding.bottomNavigation.menu.findItem(destination.id)?.isChecked = true
            }
        }
    }

    /**
     * Câblage MANUEL de la Bottom Navigation (plutôt que
     * `BottomNavigationView.setupWithNavController`, voir git history) : ce dernier s'appuie sur
     * `popUpTo(startDestination, saveState = true)` + `restoreState = true` pour mémoriser la
     * position de chaque onglet quitté — mécanisme qui s'est révélé peu fiable ici (retour sur
     * l'onglet Accueil parfois bloqué après être passé par un autre onglet). Cette version
     * renonce délibérément à cette mémorisation par onglet : changer d'onglet revient TOUJOURS à
     * l'écran racine de cet onglet (jamais à un écran secondaire ou une position de défilement
     * laissés avant de le quitter), au bénéfice d'un comportement simple et garanti.
     */
    private fun setUpBottomNavigation(navController: NavController) {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            navigateToTab(navController, item.itemId)
            true
        }
    }

    /**
     * Vide systématiquement tout ce qui a été empilé au-dessus de l'onglet Accueil avant de
     * rejoindre l'onglet demandé — aucune dépendance à `saveState`/`restoreState`, uniquement
     * `popBackStack`/`navigate` classiques, dont le comportement est simple et prévisible.
     *
     * `R.id.dashboardFragment` en dur (PAS `navController.graph.startDestinationId`) : ce dernier
     * ne vaut `dashboardFragment` qu'à l'inflation du graphe (voir [resolveStartDestination]) — un
     * utilisateur qui se connecte EN COURS DE SESSION (voir `LoginFragment`/`RegisterFragment`,
     * qui naviguent vers le Dashboard sans jamais rappeler `setStartDestination`) laisserait cette
     * valeur bloquée sur `loginFragment` pour le reste du processus, ce qui ferait échouer
     * `popBackStack` (id absent de la pile) à chaque changement d'onglet. `dashboardFragment` EST
     * l'onglet Accueil, indépendamment de la façon dont l'utilisateur est arrivé sur l'app.
     */
    private fun navigateToTab(navController: NavController, tabId: Int) {
        if (navController.currentDestination?.id == tabId) return
        navController.popBackStack(R.id.dashboardFragment, false)
        if (tabId != R.id.dashboardFragment) {
            navController.navigate(tabId)
        }
    }

    /**
     * Correction GLOBALE du recouvrement par le clavier virtuel (voir instructions projet, "ne pas
     * devoir ajouter manuellement une correction différente dans chaque Fragment") : un seul hook,
     * posé ici une fois pour toutes, s'applique automatiquement à CHAQUE Fragment affiché — actuel
     * ou futur — sans qu'aucun d'eux n'ait à s'en soucier.
     *
     * `recursive = true` est indispensable : les Fragments de destination (`LoansFragment`,
     * `LoanFormFragment`, etc., voir `nav_graph.xml`) ne sont PAS des enfants directs de
     * [supportFragmentManager], mais du `childFragmentManager` interne du [NavHostFragment] —
     * sans ce paramètre, le callback ne se déclencherait que pour le `NavHostFragment` lui-même.
     *
     * Pour chaque Fragment dont la vue vient d'être créée, recherche son [NestedScrollView] (voir
     * [findNestedScrollView]) et lui applique [SystemBars.applyImeAwarePadding] — voir sa doc pour
     * le raisonnement complet. Les écrans sans [NestedScrollView] (listes/`RecyclerView` sans champ
     * de saisie, voir `fragment_accounts.xml`, `fragment_more.xml`...) ne sont pas concernés : la
     * recherche n'y trouve simplement rien, sans effet de bord.
     */
    private fun setUpKeyboardAwareScrolling() {
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: Bundle?
                ) {
                    findNestedScrollView(v)?.let { SystemBars.applyImeAwarePadding(it) }
                }
            },
            /* recursive = */ true
        )
    }

    /** Parcours en profondeur du premier [NestedScrollView] rencontré (racine de [view] elle-même,
     * voir `fragment_dashboard.xml`, ou l'un de ses descendants, cas de la quasi-totalité des
     * formulaires — voir `SystemBars.applyImeAwarePadding`). `null` si l'écran n'en contient
     * aucun. */
    private fun findNestedScrollView(view: View): NestedScrollView? {
        if (view is NestedScrollView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findNestedScrollView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
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

        /** Les 5 onglets, mêmes id que menu/bottom_nav_menu.xml (voir sa doc et nav_graph.xml). */
        val TAB_DESTINATION_IDS = setOf(
            R.id.dashboardFragment,
            R.id.accountsFragment,
            R.id.transactionsFragment,
            R.id.statisticsFragment,
            R.id.moreFragment
        )
    }
}
