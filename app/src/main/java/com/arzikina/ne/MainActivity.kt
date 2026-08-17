package com.arzikina.ne

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.lifecycle.lifecycleScope
import com.arzikina.ne.databinding.ActivityMainBinding
import com.arzikina.ne.domain.model.ThemeMode
import com.arzikina.ne.domain.repository.BiometricAuthenticator
import com.arzikina.ne.domain.repository.RecurringTransactionRepository
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.UserPreferencesRepository
import com.arzikina.ne.presentation.components.NavAnimations
import com.arzikina.ne.presentation.security.BiometricLockFragment
import com.arzikina.ne.presentation.utilities.recurring.RecurringOccurrenceQueueDialogFragment
import com.arzikina.ne.util.SystemBars
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    @Inject
    lateinit var recurringTransactionRepository: RecurringTransactionRepository

    @Inject
    lateinit var biometricAuthenticator: BiometricAuthenticator

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    /** Voir le listener posé dans [onCreate] : garantit une génération unique par processus,
     * jamais par-dessus [R.id.biometricLockFragment] (voir sa doc pour le risque évité). */
    private var hasGeneratedMissingRecurringOccurrences = false

    /** Voir [onStop]/[checkBiometricReentryLock] : horodatage du dernier passage en arrière-plan
     * RÉEL (pas une simple rotation d'écran), `null` tant qu'aucun ne s'est encore produit — ce qui
     * garde le tout premier [onStart] (lancement à froid) sans effet, déjà couvert par
     * [resolveStartDestination]. */
    private var backgroundedAtElapsedRealtime: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyStoredThemeMode()
        SystemBars.configure(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setUpKeyboardAwareScrolling()

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

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
            // bottomNavGlassBorder (voir activity_main.xml) toujours alignée sur bottomNavigation :
            // sans ça, sa contrainte `bottom_toTopOf` s'effondrerait au bas de l'écran quand la
            // Bottom Navigation passe à GONE (comportement standard ConstraintLayout pour une
            // contrainte vers une vue GONE), affichant une lueur décorative flottante et injustifiée
            // sur les écrans Connexion/Inscription.
            val bottomNavVisibility = if (destination.id in AUTH_DESTINATION_IDS) View.GONE else View.VISIBLE
            binding.bottomNavigation.visibility = bottomNavVisibility
            binding.bottomNavGlassBorder.visibility = bottomNavVisibility
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
            // littéralement l'un des 4 onglets (voir TAB_DESTINATION_IDS) : un écran secondaire
            // (formulaire, détail, Budget/Catégories/Paramètres/Transactions depuis "Autre"/le
            // Dashboard...) laisse volontairement l'onglet déjà sélectionné en surbrillance, sans
            // qu'aucun ne corresponde à sa propre destination (voir aussi
            // accountsFromDashboardFragment dans nav_graph.xml, qui exploite déjà ce principe
            // pour le raccourci du Dashboard).
            if (destination.id in TAB_DESTINATION_IDS) {
                binding.bottomNavigation.menu.findItem(destination.id)?.isChecked = true
            }
            // Déclenché depuis CE listener (jamais inconditionnellement en fin de onCreate) :
            // `addOnDestinationChangedListener` est rappelé immédiatement pour la destination
            // courante dès son enregistrement, PUIS à chaque changement — c'est ce qui
            // permet de générer les occurrences (et d'afficher leur dialogue de validation)
            // seulement une fois le Dashboard réellement atteint, jamais par-dessus
            // `biometricLockFragment`. Sans ce garde-fou, le dialogue s'affichait par-dessus
            // l'écran de verrouillage AVANT toute vérification biométrique (montants/catégories
            // visibles et transactions validables sans authentification) — un contournement total
            // du verrou. `hasGeneratedMissingRecurringOccurrences` évite de relancer la génération
            // à chaque retour sur l'onglet Accueil (voir [navigateToTab]).
            if (destination.id == R.id.dashboardFragment && !hasGeneratedMissingRecurringOccurrences) {
                hasGeneratedMissingRecurringOccurrences = true
                generateMissingRecurringOccurrences()
            }
        }
    }

    /**
     * Revérification biométrique au retour au premier plan (deuxième volet du cahier des charges,
     * en complément du verrou à l'ouverture posé par [resolveStartDestination]) — voir
     * [checkBiometricReentryLock] pour la logique complète. Une seule Activity dans toute l'app
     * (voir la doc de classe) : PAS besoin de `ProcessLifecycleOwner`/`lifecycle-process` — le
     * cycle de vie de CETTE Activity suffit à lui seul à détecter "l'app quitte/revient au premier
     * plan", ce qu'`ProcessLifecycleOwner` n'apporterait ici que comme indirection supplémentaire
     * (il existe précisément pour distinguer ce cas dans une app MULTI-Activity, non pertinent ici).
     */
    override fun onStart() {
        super.onStart()
        checkBiometricReentryLock()
    }

    /**
     * `isChangingConfigurations` : une simple rotation d'écran fait aussi transiter par
     * `onStop`/`onStart` sans que l'app ait réellement quitté le premier plan — l'exclure évite de
     * redemander une empreinte à chaque rotation. `backgroundedAtElapsedRealtime` n'est donc posé
     * QUE pour un arrêt "réel" (app mise en arrière-plan, écran verrouillé par le système, etc.).
     */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            backgroundedAtElapsedRealtime = SystemClock.elapsedRealtime()
        }
    }

    /**
     * Empile `biometricLockFragment` (mode `isResumeCheck`, voir sa doc) PAR-DESSUS l'écran courant
     * si TOUTES ces conditions sont réunies :
     * - l'app a réellement été mise en arrière-plan (voir [onStop]) et a passé au moins
     *   [BIOMETRIC_REENTRY_GRACE_PERIOD_MILLIS] hors du premier plan — ce délai de grâce évite de
     *   redemander une empreinte pour un aller-retour très bref (ex. sélecteur de photo de
     *   `ProfileFragment`, export de fichier de `BackupFragment` : ces flux font brièvement quitter
     *   MainActivity via une autre Activity système, sans que l'utilisateur ait "vraiment" quitté
     *   Arzikina) ;
     * - une session existe, le réglage est actif, et la biométrie est disponible MAINTENANT (mêmes
     *   trois conditions que [resolveStartDestination], vérifiées à nouveau ici car elles ont pu
     *   changer depuis le lancement) ;
     * - la destination courante n'est pas déjà un écran hors-session (voir [AUTH_DESTINATION_IDS]) —
     *   inutile de verrouiller un écran qui ne montre déjà aucune donnée protégée.
     *
     * `backgroundedAtElapsedRealtime` est remis à `null` dès l'entrée, AVANT la coroutine
     * asynchrone : un second `onStart` déclenché pendant que cette vérification est encore en cours
     * (cas limite) ne doit pas relancer une deuxième vérification concurrente.
     */
    private fun checkBiometricReentryLock() {
        val backgroundedAt = backgroundedAtElapsedRealtime ?: return
        backgroundedAtElapsedRealtime = null
        val elapsedSinceBackground = SystemClock.elapsedRealtime() - backgroundedAt
        if (elapsedSinceBackground < BIOMETRIC_REENTRY_GRACE_PERIOD_MILLIS) return
        if (navController.currentDestination?.id in AUTH_DESTINATION_IDS) return

        lifecycleScope.launch {
            val hasSession = sessionManager.getCurrentUserIdOnce() != null
            if (!hasSession) return@launch
            val biometricLockEnabled = userPreferencesRepository.observePreferences().first().biometricLockEnabled
            if (!biometricLockEnabled) return@launch
            if (!biometricAuthenticator.isAvailable()) return@launch
            // Revérifié : la coroutine ci-dessus a pu laisser le temps à l'utilisateur de naviguer
            // (ex. se déconnecter) avant que ces `suspend fun` ne renvoient leur résultat.
            if (navController.currentDestination?.id in AUTH_DESTINATION_IDS) return@launch

            navController.navigate(
                R.id.biometricLockFragment,
                bundleOf(BiometricLockFragment.ARG_IS_RESUME_CHECK to true),
                NavAnimations.push
            )
        }
    }

    /**
     * Transactions récurrentes/planifiées (voir cahier des charges, section "Détection
     * automatique") : génère les occurrences `PENDING` des règles actives arrivées à échéance
     * (échéance du jour ET échéances manquées, voir `RecurringTransactionRepository.generateMissingOccurrences`),
     * puis affiche le dialogue de validation ([RecurringOccurrenceQueueDialogFragment]) s'il en
     * résulte au moins une occurrence à traiter.
     *
     * Appelée UNE SEULE FOIS par lancement, au premier accès réel au Dashboard (voir le listener
     * dans [onCreate], `hasGeneratedMissingRecurringOccurrences`) — jamais avant, en particulier
     * jamais par-dessus [R.id.biometricLockFragment] : le dialogue affiche des montants/catégories
     * et permet de valider des transactions, ce qui contournerait totalement le verrou biométrique
     * s'il apparaissait avant que l'utilisateur l'ait franchi.
     */
    private fun generateMissingRecurringOccurrences() {
        lifecycleScope.launch {
            recurringTransactionRepository.generateMissingOccurrences()
            val hasPendingOccurrences = recurringTransactionRepository.observePendingOccurrences().first().isNotEmpty()
            if (hasPendingOccurrences) {
                showRecurringOccurrenceQueueIfNeeded()
            }
        }
    }

    /**
     * Garde-fou anti-doublon : n'affiche le dialogue que s'il n'est pas déjà présent dans
     * [supportFragmentManager]. Une simple rotation d'écran ne repasse jamais ici (cette fonction
     * n'est appelée qu'une fois par [onCreate], voir [generateMissingRecurringOccurrences]) — ce
     * garde-fou couvre uniquement le cas où [onCreate] serait relancé (ex. l'Activity a été détruite
     * puis recréée par le système en arrière-plan) alors qu'un dialogue montré avant cette
     * destruction a déjà été restauré automatiquement par [supportFragmentManager].
     */
    private fun showRecurringOccurrenceQueueIfNeeded() {
        if (supportFragmentManager.findFragmentByTag(RECURRING_QUEUE_DIALOG_TAG) == null) {
            RecurringOccurrenceQueueDialogFragment().show(supportFragmentManager, RECURRING_QUEUE_DIALOG_TAG)
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
     *
     * [NavAnimations.tabSwitch] (fondu enchaîné, sans glissement — voir sa doc) est passé au
     * `navigate(tabId, ...)` : c'est CETTE destination qui porte l'animation, dans les deux sens
     * — à l'aller (`enterAnim`/`exitAnim`) comme au retour vers Accueil déclenché par
     * `popBackStack` juste au-dessus (`popEnterAnim`/`popExitAnim`), Navigation Component
     * appliquant systématiquement les animations déclarées lors du `navigate()` d'origine d'une
     * destination, y compris quand elle est ensuite dépilée. Le retour direct à Accueil
     * (`tabId == dashboardFragment`, sans appel à `navigate`) n'a pas besoin d'animation dédiée :
     * il ne fait que rejouer le `popExitAnim`/`popEnterAnim` déjà déclaré par le dernier onglet
     * quitté.
     */
    private fun navigateToTab(navController: NavController, tabId: Int) {
        if (navController.currentDestination?.id == tabId) return
        navController.popBackStack(R.id.dashboardFragment, false)
        if (tabId != R.id.dashboardFragment) {
            navController.navigate(tabId, null, NavAnimations.tabSwitch)
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
     * Lecture bloquante ponctuelle de la session locale ET du réglage de verrouillage biométrique
     * (DataStore, source locale, quasi instantanée — même justification que [applyStoredThemeMode]
     * ci-dessous) : AVANT le premier affichage, pour ne jamais montrer le Dashboard puis rediriger
     * vers le verrou (ou l'inverse) une fois l'app déjà visible.
     *
     * Trois cas :
     * - pas de session → `loginFragment`, comme avant (le réglage biométrique n'a aucun sens sans
     *   session à protéger) ;
     * - session + verrou activé ET biométrie disponible MAINTENANT sur l'appareil → `biometricLockFragment`
     *   (voir sa doc) ;
     * - session + (verrou désactivé OU biométrie indisponible) → `dashboardFragment` directement,
     *   comme avant. Le cas "activé mais indisponible" est volontaire : si l'utilisateur a
     *   désenrôlé ses empreintes ou désactivé le capteur dans les réglages système DEPUIS
     *   qu'il a activé ce réglage dans Arzikina, bloquer l'accès à l'app serait un verrou dont on
     *   ne peut plus jamais sortir sans passer par "Se déconnecter" — préférer laisser passer
     *   plutôt que de dépendre uniquement de cette échappatoire à chaque lancement.
     */
    private fun resolveStartDestination(): Int = runBlocking {
        val hasSession = sessionManager.getCurrentUserIdOnce() != null
        if (!hasSession) return@runBlocking R.id.loginFragment

        val biometricLockEnabled = userPreferencesRepository.observePreferences().first().biometricLockEnabled
        val biometricLockRequired = biometricLockEnabled && biometricAuthenticator.isAvailable()
        if (biometricLockRequired) R.id.biometricLockFragment else R.id.dashboardFragment
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
        /** Tag `FragmentManager` du dialogue de validation (voir [showRecurringOccurrenceQueueIfNeeded]). */
        const val RECURRING_QUEUE_DIALOG_TAG = "recurring_occurrence_queue"

        /** Voir [checkBiometricReentryLock] : délai de grâce sous lequel un aller-retour hors de
         * l'app (sélecteur de photo/fichier système, etc.) ne redemande PAS d'empreinte. */
        const val BIOMETRIC_REENTRY_GRACE_PERIOD_MILLIS = 30_000L

        /** Destinations sans Bottom Navigation ni onglet correspondant (voir plus haut).
         * `biometricLockFragment` y figure pour la même raison que les 3 écrans d'authentification :
         * un écran plein cadre, sans contexte d'onglet pertinent. */
        val AUTH_DESTINATION_IDS = setOf(
            R.id.loginFragment,
            R.id.registerFragment,
            R.id.forgotPasswordFragment,
            R.id.biometricLockFragment
        )

        /** Les 4 onglets, mêmes id que menu/bottom_nav_menu.xml (voir sa doc et nav_graph.xml).
         * transactionsFragment n'en fait plus partie (voir sa doc dans bottom_nav_menu.xml) :
         * atteint désormais depuis le Dashboard, sans jamais recocher d'onglet. */
        val TAB_DESTINATION_IDS = setOf(
            R.id.dashboardFragment,
            R.id.accountsFragment,
            R.id.statisticsFragment,
            R.id.moreFragment
        )
    }
}
