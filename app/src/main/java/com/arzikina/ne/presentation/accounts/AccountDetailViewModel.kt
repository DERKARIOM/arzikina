package com.arzikina.ne.presentation.accounts

import android.graphics.drawable.Drawable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.AccountType
import com.arzikina.ne.domain.model.CardSecrets
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.AuthRepository
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.presentation.transactions.TransactionDaySection
import com.arzikina.ne.presentation.transactions.TransactionUiItem
import com.arzikina.ne.presentation.transactions.computeRunningBalances
import com.arzikina.ne.presentation.transactions.feeTransactionIds
import com.arzikina.ne.presentation.transactions.groupByDay
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.external.ExternalAppLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * État affiché par l'écran "Détail du compte". [sections] est trié du jour
 * le plus récent au plus ancien (voir [groupByDay]).
 *
 * @param cardHolderName voir [AccountUiItem.cardHolderName] (même
 * raisonnement : nom de l'utilisateur connecté, sans effet hors
 * [com.arzikina.ne.domain.model.AccountType.CREDIT_CARD]).
 */
data class AccountDetailUiState(
    val account: Account,
    val currentBalance: Long,
    val sections: List<TransactionDaySection>,
    val cardHolderName: String = ""
)

/**
 * État de la carte "application Mobile Money" (voir cahier des charges, section 13 : "le bouton
 * doit uniquement apparaître si le compte est de type Mobile Money"). [NotApplicable] pour tout
 * autre type — la carte reste alors masquée, voir [AccountDetailFragment.renderMobileMoneyAppCard].
 *
 * Résolu EN DIRECT via [ExternalAppLauncher] (jamais persisté) : voir
 * [AccountDetailViewModel.refreshMobileMoneyAppState], appelée à la fois quand le compte change
 * (édition) ET quand l'écran redevient visible ([AccountDetailFragment.onResume]) — l'état
 * d'installation d'une application externe peut changer à tout moment pendant qu'Arzikina n'a pas
 * le focus, sans qu'aucune donnée de CE compte n'ait bougé.
 */
sealed interface MobileMoneyAppUiState {
    data object NotApplicable : MobileMoneyAppUiState
    data object NotConfigured : MobileMoneyAppUiState
    data class Installed(val packageName: String, val label: String, val icon: Drawable?) : MobileMoneyAppUiState
    data class NotInstalled(val packageName: String) : MobileMoneyAppUiState
}

/** Événement ponctuel (voir [AccountDetailViewModel.openMobileMoneyApp]) — un échec de lancement
 * malgré un état [MobileMoneyAppUiState.Installed] reste possible (voir cahier des charges,
 * "Application installée mais sans activité de lancement") : cas limite affiné à l'étape suivante
 * du plan (dialogues dédiés), un simple signal suffit pour l'instant. */
sealed interface AccountDetailEvent {
    data object MobileMoneyLaunchFailed : AccountDetailEvent
}

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    authRepository: AuthRepository,
    sessionManager: SessionManager,
    private val externalAppLauncher: ExternalAppLauncher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    val accountId: Long = savedStateHandle.get<Long>(ACCOUNT_ID_ARG) ?: 0L

    val uiState: StateFlow<AppResult<AccountDetailUiState>> = combine(
        accountRepository.observeAccounts(),
        transactionRepository.observeTransactions(),
        categoryRepository.observeCategories(),
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) flowOf(null) else authRepository.observeUser(userId)
        }
    ) { accounts, transactions, categories, user ->
        val account = accounts.find { it.id == accountId } ?: return@combine null
        val categoriesById = categories.associateBy { it.id }

        // Un transfert dont ce compte est la DESTINATION doit apparaître ici aussi (voir
        // TransactionType.TRANSFER) : accountId seul ne couvre que la jambe "source".
        // Inclut volontairement les transactions de frais de CE compte (voir feeTransactionIds
        // plus bas) : elles doivent compter dans le solde, seule leur LIGNE d'affichage est
        // filtrée ensuite.
        val accountTransactions = transactions.filter { it.accountId == accountId || it.transferAccountId == accountId }
        // `accountTransactions` suffit ici (pas besoin de la liste complète) : pour chaque
        // transfert filtré, computeCurrentBalances/computeRunningBalances savent déjà créditer
        // la jambe destination dès que transferAccountId == accountId (voir leur documentation).
        val currentBalance = computeCurrentBalances(listOf(account), accountTransactions)[account.id] ?: account.initialBalance
        val runningBalances = computeRunningBalances(accountTransactions, listOf(account))

        // Recherche du montant des frais liés sur TOUTE la liste (transactions, pas
        // accountTransactions) : le compte des frais d'une transaction de ce compte peut être un
        // AUTRE compte (voir feeTransactionIds).
        val transactionsById = transactions.associateBy { it.id }
        val feeTransactionIds = transactions.feeTransactionIds()
        val items = accountTransactions
            .filter { transaction -> transaction.id !in feeTransactionIds }
            .map { transaction ->
                val isTransferReceived = transaction.transferAccountId == accountId
                TransactionUiItem(
                    transaction = transaction,
                    account = account,
                    // categoryId est `null` pour un transfert (voir TransactionType.TRANSFER).
                    category = transaction.categoryId?.let { categoriesById[it] },
                    runningBalance = runningBalances[transaction.id to accountId],
                    isTransferReceived = isTransferReceived,
                    feeAmount = transaction.feeTransactionId?.let { transactionsById[it]?.amount }
                )
            }

        AccountDetailUiState(
            account = account,
            currentBalance = currentBalance,
            sections = items.groupByDay(),
            cardHolderName = user?.fullName.orEmpty()
        )
    }
        .map<AccountDetailUiState?, AppResult<AccountDetailUiState>> { state ->
            state?.let { AppResult.Success(it) } ?: AppResult.Error("Compte introuvable")
        }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    fun deleteAccount() {
        viewModelScope.launch {
            accountRepository.deleteAccount(accountId)
        }
    }

    /**
     * Numéro complet + CVV DÉCHIFFRÉS, `null` par défaut (masqué). Vit ici plutôt que dans le
     * Fragment (contrairement au choix initial de `DashboardFragment.isBalanceHidden`) : la
     * révélation exige un déchiffrement via [AccountRepository.revealCardSecrets], une opération
     * de la couche data qui n'a rien à faire dans une Fragment — Clean Architecture oblige.
     */
    private val _cardSecrets = MutableStateFlow<CardSecrets?>(null)
    val cardSecrets: StateFlow<CardSecrets?> = _cardSecrets.asStateFlow()

    private var autoHideJob: Job? = null

    /**
     * Déchiffre et affiche le secret. Ignoré si un compte classique appelle ceci par erreur : il
     * n'y a simplement jamais de secret enregistré pour lui, [AccountRepository.revealCardSecrets]
     * renverra `null`.
     *
     * Volontairement DÉPOURVU de toute vérification biométrique ici (contrairement à ce que son
     * ancien nom `toggleCardSecrets` — bascule masqué/révélé — laissait supposer) : le
     * `BiometricPrompt` a besoin d'une `FragmentActivity` (voir la doc de
     * [com.arzikina.ne.domain.repository.BiometricAuthenticator]) qu'un ViewModel ne doit jamais
     * retenir. C'est donc [com.arzikina.ne.presentation.accounts.AccountDetailFragment] qui
     * authentifie D'ABORD (biométrie disponible et réussie), puis appelle cette fonction
     * uniquement en cas de succès — jamais l'inverse. Le nom reflète maintenant qu'il ne fait plus
     * QUE révéler (la bascule vers le masquage, elle, ne nécessite aucune authentification et
     * reste gérée directement par le Fragment via [hideCardSecrets]).
     */
    fun revealCardSecrets() {
        autoHideJob?.cancel()
        viewModelScope.launch {
            _cardSecrets.value = accountRepository.revealCardSecrets(accountId)
            if (_cardSecrets.value != null) {
                autoHideJob = viewModelScope.launch {
                    delay(AUTO_HIDE_DELAY_MILLIS)
                    _cardSecrets.value = null
                }
            }
        }
    }

    /** Remasquage immédiat (bouton, [android.app.Activity.onPause] de l'écran, délai écoulé). */
    fun hideCardSecrets() {
        autoHideJob?.cancel()
        _cardSecrets.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // Le secret déchiffré ne doit jamais survivre au ViewModel (voir CardSecrets, "jamais
        // conservés au-delà de la session d'affichage courante").
        _cardSecrets.value = null
    }

    private val _mobileMoneyAppState = MutableStateFlow<MobileMoneyAppUiState>(MobileMoneyAppUiState.NotApplicable)
    val mobileMoneyAppState: StateFlow<MobileMoneyAppUiState> = _mobileMoneyAppState.asStateFlow()

    private val _events = MutableSharedFlow<AccountDetailEvent>()
    val events: SharedFlow<AccountDetailEvent> = _events.asSharedFlow()

    init {
        // Réagit à toute évolution du type/package (édition du compte) — voir aussi
        // [refreshMobileMoneyAppState], le second déclencheur (retour au premier plan de l'écran,
        // voir sa doc pour la raison des DEUX déclencheurs).
        viewModelScope.launch {
            uiState
                .mapNotNull { (it as? AppResult.Success)?.data?.account }
                .map { it.type to it.mobileMoneyPackageName }
                .distinctUntilChanged()
                .collect { (type, packageName) -> resolveMobileMoneyAppState(type, packageName) }
        }
    }

    /** Voir la doc de [MobileMoneyAppUiState] — appelée par
     * [AccountDetailFragment.onResume] : re-résout l'état d'installation SANS attendre un
     * changement du compte en base (l'application a pu être installée/désinstallée pendant
     * qu'Arzikina était en arrière-plan). Sans effet si l'écran n'a pas encore de compte chargé
     * (premier lancement — [init] s'en charge dès que [uiState] émet). */
    fun refreshMobileMoneyAppState() {
        val account = (uiState.value as? AppResult.Success)?.data?.account ?: return
        viewModelScope.launch { resolveMobileMoneyAppState(account.type, account.mobileMoneyPackageName) }
    }

    private suspend fun resolveMobileMoneyAppState(type: AccountType, packageName: String?) {
        if (type != AccountType.MOBILE_MONEY) {
            _mobileMoneyAppState.value = MobileMoneyAppUiState.NotApplicable
            return
        }
        if (packageName.isNullOrBlank()) {
            _mobileMoneyAppState.value = MobileMoneyAppUiState.NotConfigured
            return
        }
        val appInfo = withContext(ioDispatcher) { externalAppLauncher.getAppInfo(packageName) }
        _mobileMoneyAppState.value = if (appInfo != null) {
            MobileMoneyAppUiState.Installed(packageName, appInfo.label, appInfo.icon)
        } else {
            MobileMoneyAppUiState.NotInstalled(packageName)
        }
    }

    /** Ignoré si [mobileMoneyAppState] n'est pas [MobileMoneyAppUiState.Installed] (bouton
     * masqué/différent dans les autres états, voir [AccountDetailFragment] — ne devrait donc
     * jamais être appelée hors de ce cas, cette garde reste défensive). */
    fun openMobileMoneyApp() {
        val state = _mobileMoneyAppState.value as? MobileMoneyAppUiState.Installed ?: return
        viewModelScope.launch {
            val launched = withContext(ioDispatcher) { externalAppLauncher.launch(state.packageName) }
            if (!launched) {
                _events.emit(AccountDetailEvent.MobileMoneyLaunchFailed)
            }
        }
    }

    private companion object {
        const val ACCOUNT_ID_ARG = "accountId"

        /** Délai avant remasquage automatique des informations de la carte (section sécurité). */
        const val AUTO_HIDE_DELAY_MILLIS = 10_000L
    }
}
