package com.arzikina.ne

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.IntentCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.lifecycle.lifecycleScope
import com.arzikina.ne.data.receipts.ReceiptFileStorage
import com.arzikina.ne.databinding.ActivityMainBinding
import com.arzikina.ne.domain.model.ThemeMode
import com.arzikina.ne.domain.repository.BiometricAuthenticator
import com.arzikina.ne.domain.repository.ReceiptRepository
import com.arzikina.ne.domain.repository.RecurringTransactionRepository
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.UserPreferencesRepository
import com.arzikina.ne.presentation.components.NavAnimations
import com.arzikina.ne.presentation.security.BiometricLockFragment
import com.arzikina.ne.presentation.utilities.recurring.RecurringOccurrenceQueueDialogFragment
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.SystemBars
import com.arzikina.ne.util.external.ExternalAppLauncher
import com.google.android.material.snackbar.Snackbar
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

    @Inject
    lateinit var receiptRepository: ReceiptRepository

    @Inject
    lateinit var receiptFileStorage: ReceiptFileStorage

    @Inject
    lateinit var externalAppLauncher: ExternalAppLauncher

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    /** Voir le listener posé dans [onCreate] : garantit une génération unique par processus,
     * jamais par-dessus [R.id.biometricLockFragment] (voir sa doc pour le risque évité). */
    private var hasGeneratedMissingRecurringOccurrences = false

    /** Voir [handleIncomingReceiptShare]/[tryProcessPendingReceiptShare] : un PDF partagé en
     * attente d'import (URI encore lisible, métadonnées déjà résolues) — `null` tant qu'aucun
     * partage n'est en attente. Volontairement un simple champ en mémoire, PAS persisté : si le
     * processus est tué pendant l'attente (verrou biométrique non franchi, app en arrière-plan
     * longtemps...), ce partage est perdu — limitation acceptée, voir la doc de
     * [handleIncomingReceiptShare] pour le raisonnement complet. */
    private var pendingReceiptShare: PendingReceiptShare? = null

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
            // Gestion des reçus (voir [tryProcessPendingReceiptShare]) : retente un import en
            // attente à CHAQUE destination atteinte hors écran d'authentification/verrouillage —
            // pas seulement le Dashboard (contrairement au bloc ci-dessus), car un déverrouillage
            // "resume check" (voir BiometricLockFragment.isResumeCheck) révèle l'écran quitté par
            // l'utilisateur, pas forcément le Dashboard. No-op immédiat si aucun partage en attente.
            if (destination.id !in AUTH_DESTINATION_IDS) {
                tryProcessPendingReceiptShare()
            }
        }

        // Partage entrant à froid (app pas encore lancée, voir cahier des charges "Gestion des
        // reçus" section 1) : l'Intent de lancement est déjà celui du partage dans ce cas (PAS
        // besoin d'attendre onNewIntent, réservé aux partages reçus APP DÉJÀ EN COURS D'EXÉCUTION,
        // voir sa doc). Placé APRÈS l'inflation du graphe (juste au-dessus) : [tryProcessPendingReceiptShare]
        // a besoin de `navController.currentDestination`, déjà valide à ce stade.
        handleIncomingReceiptShare(intent)
    }

    /**
     * Reçoit un partage ACTION_SEND alors qu'Arzikina est déjà lancée (voir `AndroidManifest.xml`,
     * `android:launchMode="singleTask"` sur cette Activity — indispensable pour que ce callback soit
     * appelé plutôt qu'une seconde instance créée). `setIntent(intent)` AVANT tout traitement :
     * même règle documentée officiellement pour `onNewIntent` (`getIntent()` doit refléter le
     * dernier Intent reçu, notamment pour un éventuel redémarrage de l'Activity par le système par
     * la suite).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingReceiptShare(intent)
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
     * Point d'entrée UNIQUE pour un [Intent] potentiellement porteur d'un partage PDF (cahier des
     * charges "Gestion des reçus", section 1) — appelé aussi bien pour l'Intent de lancement à froid
     * ([onCreate]) que pour un partage reçu app déjà ouverte ([onNewIntent]). Ignore silencieusement
     * tout Intent qui n'est pas EXACTEMENT `ACTION_SEND`/`application/pdf` avec un flux exploitable
     * (ex. l'Intent `MAIN`/`LAUNCHER` habituel) : ce n'est jamais une erreur, seulement "rien à
     * importer cette fois".
     *
     * Ne copie/n'enregistre RIEN elle-même : résout uniquement les métadonnées immédiatement
     * disponibles (nom d'origine, application source si déterminable) et délègue tout le reste à
     * [tryProcessPendingReceiptShare], qui applique la garde biométrique (voir sa doc) avant
     * d'effectuer la copie réelle — potentiellement bien après cet appel si l'app est verrouillée.
     *
     * Résolution de l'application source via [Activity.getReferrer] (voir sa documentation
     * officielle) : reflète soit `Intent.EXTRA_REFERRER`/`EXTRA_REFERRER_NAME` posé explicitement
     * par l'application source, soit — à défaut — l'information de provenance suivie par le système
     * pour cet Intent. Non garanti par TOUTES les applications/lanceurs (cahier des charges section
     * 3) : `null` dans ce cas, jamais une provenance devinée autrement (ex. jamais une simple
     * supposition basée sur le nom du fichier).
     */
    private fun handleIncomingReceiptShare(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_SEND || intent.type != Constants.RECEIPT_MIME_TYPE) return
        val sourceUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: return

        // Aucun ajout au bloc <queries> du manifeste nécessaire ici (contrairement à
        // ExternalAppLauncher.listLaunchableApps) : une application qui vient de nous envoyer un
        // Intent (ce partage lui-même) devient automatiquement visible à notre PackageManager —
        // "visibilité implicite" documentée officiellement (Android 11+, filtrage de visibilité des
        // packages) — getApplicationInfo(sourcePackageName, ...) fonctionne donc sans déclaration
        // supplémentaire, quelle que soit l'application source.
        val sourcePackageName = referrer?.takeIf { it.scheme == REFERRER_APP_SCHEME }?.host
        val sourceAppInfo = sourcePackageName?.let { externalAppLauncher.getAppInfo(it) }
        val displayName = receiptFileStorage.queryDisplayName(sourceUri)
            ?: getString(R.string.receipt_share_default_file_name)

        pendingReceiptShare = PendingReceiptShare(
            uriString = sourceUri.toString(),
            displayName = displayName,
            // sourceAppInfo peut résoudre un libellé même si l'app source n'a pas correctement
            // valorisé le referrer côté package (repli sur le nom de package lui-même, voir
            // ExternalAppLauncher.getAppInfo) : jamais utilisé si sourcePackageName est déjà null.
            sourcePackage = sourcePackageName,
            sourceLabel = sourceAppInfo?.label
        )
        tryProcessPendingReceiptShare()
    }

    /**
     * Effectue l'import RÉEL de [pendingReceiptShare] si — et seulement si — l'application n'est
     * PAS actuellement sur un écran d'authentification/verrouillage (voir [AUTH_DESTINATION_IDS]) ET
     * qu'une session existe. `no-op` silencieux si [pendingReceiptShare] est déjà `null` (rien en
     * attente) : sûr à appeler depuis n'importe quel point de l'Activity sans condition préalable.
     *
     * Revérifié APRÈS les `suspend fun` (session) — même raisonnement que
     * [checkBiometricReentryLock] ("Revérifié : la coroutine ci-dessus a pu laisser le temps...") :
     * un verrou de retour au premier plan peut apparaître PENDANT cette coroutine (ex. un partage
     * reçu au tout premier instant où l'app repasse au premier plan, avant même que
     * [checkBiometricReentryLock] n'ait fini de décider s'il faut verrouiller). ⚠️ Fenêtre résiduelle
     * documentée : ce deuxième contrôle réduit ce risque sans l'éliminer totalement dans ce cas
     * précis (deux coroutines indépendantes sur `Dispatchers.Main`, sans verrou explicite entre
     * elles) — accepté car la seule conséquence visible serait, au pire, la confirmation Snackbar
     * d'un import déjà réussi affichée un bref instant AVANT que l'écran de verrouillage ne
     * s'affiche par-dessus : aucune donnée financière, aucune action possible depuis ce Snackbar,
     * contrairement au risque déjà corrigé pour le dialogue des transactions automatiques (montants/
     * catégories visibles ET validables). Signalé ici plutôt que silencieusement accepté.
     */
    private fun tryProcessPendingReceiptShare() {
        val pending = pendingReceiptShare ?: return
        if (navController.currentDestination?.id in AUTH_DESTINATION_IDS) return

        lifecycleScope.launch {
            val hasSession = sessionManager.getCurrentUserIdOnce() != null
            if (!hasSession) return@launch
            if (navController.currentDestination?.id in AUTH_DESTINATION_IDS) return@launch

            // Consommé AVANT l'écriture elle-même : un second appel concurrent (ex. l'immédiat de
            // handleIncomingReceiptShare ET le listener de destinationChanged déclenchés très près
            // l'un de l'autre) ne doit jamais importer deux fois le même partage.
            pendingReceiptShare = null

            val result = runCatching {
                receiptRepository.importReceipt(
                    sourceUri = pending.uriString,
                    displayName = pending.displayName,
                    mimeType = Constants.RECEIPT_MIME_TYPE,
                    sourceApp = pending.sourcePackage,
                    sourceName = pending.sourceLabel
                )
            }
            val messageRes = if (result.isSuccess) {
                R.string.receipt_share_imported_message
            } else {
                R.string.receipt_share_import_failed_message
            }
            Snackbar.make(binding.root, messageRes, Snackbar.LENGTH_LONG).show()
        }
    }

    /** Voir [handleIncomingReceiptShare] : métadonnées d'un partage PDF déjà résolues, en attente de
     * la garde biométrique de [tryProcessPendingReceiptShare]. [uriString] plutôt qu'un [Uri] direct
     * — cohérent avec `ReceiptRepository.importReceipt`, voir sa doc (aucun type Android dans le
     * domaine). */
    private data class PendingReceiptShare(
        val uriString: String,
        val displayName: String,
        val sourcePackage: String?,
        val sourceLabel: String?
    )

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

        /** Schéma utilisé par [Activity.getReferrer] pour désigner une application Android (voir sa
         * documentation officielle) — `android-app://<packageName>`. */
        const val REFERRER_APP_SCHEME = "android-app"

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
