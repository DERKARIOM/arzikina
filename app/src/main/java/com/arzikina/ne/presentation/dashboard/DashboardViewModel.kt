package com.arzikina.ne.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Budget
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.model.User
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.AuthRepository
import com.arzikina.ne.domain.repository.BudgetRepository
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.RecurringTransactionRepository
import com.arzikina.ne.domain.repository.SessionManager
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.presentation.accounts.computeCurrentBalances
import com.arzikina.ne.presentation.budget.BudgetUiItem
import com.arzikina.ne.presentation.components.SyncButtonController
import com.arzikina.ne.presentation.components.SyncButtonEvent
import com.arzikina.ne.presentation.components.SyncIndicatorUiState
import com.arzikina.ne.presentation.components.SyncNowUiState
import com.arzikina.ne.presentation.transactions.TransactionUiItem
import com.arzikina.ne.presentation.transactions.feeTransactionIds
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.BudgetPace
import com.arzikina.ne.util.BudgetPeriodStatus
import com.arzikina.ne.util.BudgetProgress
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.PersonalStatistics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** Nombre de transactions récentes affichées sur le tableau de bord. */
private const val RECENT_TRANSACTIONS_LIMIT = 5

/**
 * État affiché par [DashboardScreen].
 *
 * [balances]/[monthlyIncome]/[monthlyExpense] sont des listes plutôt que des
 * [Long] uniques : voir [CurrencyAmount] pour le raisonnement (pas de
 * conversion de change, donc pas d'addition entre devises différentes).
 */
data class DashboardUiState(
    val balances: List<CurrencyAmount>,
    val monthlyIncome: List<CurrencyAmount>,
    val monthlyExpense: List<CurrencyAmount>,
    /**
     * "Écart Budget" (voir cahier des charges du même nom, reproduit à l'identique depuis
     * `arzikina-web-sync/src/services/finance.ts`) = solde total personnel (même valeur que
     * [balances], voir [DashboardViewModel.computeBalances]) − somme des montants RESTANTS
     * ([BudgetProgress.Result.spentMinor] soustrait de [Budget.limitAmount]) des budgets dont la
     * période est actuellement EN COURS ([BudgetPace.periodStatus] == [BudgetPeriodStatus.ONGOING],
     * dates fixes ou récurrent — voir [DashboardViewModel.activeBudgetsRemainingMinor]).
     *
     * Toujours renseigné (jamais `null`), y compris sans aucun budget actif : la somme des restants
     * vaut alors `0`, donc l'écart est égal au solde total — même comportement que
     * `activeBudgetsRemainingTotal` côté Web, qui retombe sur `0` via son accumulateur `reduce`.
     *
     * Une seule devise (voir [CurrencyAmount]) : même convention que [DashboardFragment.renderIncomeExpense]
     * pour `differenceValue` — la première devise de [balances] (repli [Constants.DEFAULT_CURRENCY_CODE]
     * si l'utilisateur n'a encore aucun compte), les budgets d'une autre devise n'entrent pas dans la
     * somme des restants (pas de conversion de change dans ce projet, voir [CurrencyAmount]).
     */
    val budgetGap: CurrencyAmount,
    val recentTransactions: List<TransactionUiItem>,
    /**
     * Le budget le plus "urgent" (progression la plus élevée, dépassement
     * inclus) parmi tous les budgets actifs, ou `null` si aucun budget n'existe
     * encore. Un seul budget est mis en avant sur le tableau de bord ; la
     * liste complète reste dans l'onglet Budget (voir [BudgetUiItem]).
     */
    val featuredBudget: BudgetUiItem?,
    /**
     * Nom complet et photo de profil de l'utilisateur connecté, pour l'en-tête
     * du tableau de bord (voir DashboardFragment). `null`/vide tant que
     * l'utilisateur n'a pas encore été chargé — ne devrait normalement pas
     * arriver en pratique puisque le Dashboard n'est atteignable qu'après
     * connexion (voir MainActivity.resolveStartDestination).
     */
    val userFullName: String,
    val userProfilePhotoUri: String?,
    /**
     * 4 chiffres décoratifs pour la carte "Solde total" façon carte VISA
     * virtuelle (voir fragment_dashboard.xml, maquette carte). Dérivés de
     * [com.arzikina.ne.domain.model.User.id] (voir [DashboardViewModel]) —
     * jamais un vrai numéro de carte bancaire, purement esthétique et stable
     * tant que l'utilisateur ne change pas.
     */
    val cardNumberLastDigits: String,
    /**
     * Nombre d'occurrences `PENDING` de transactions planifiées (voir cahier des charges, section
     * "Dashboard" — pastille de comptage sur le bloc Utilitaires). MÊME flux que "À traiter" de
     * `RecurringTransactionsViewModel`/le dialogue de validation (voir la doc de
     * `RecurringTransactionRepository.observePendingOccurrences`, qui anticipait déjà explicitement
     * cet usage) : jamais recalculé séparément, juste sa taille.
     */
    val pendingRecurringCount: Int
)

/** Regroupe les 5 flux du Dashboard pour éviter de dépasser la limite de 5 arguments de `combine`
 * — voir [DashboardViewModel.uiState], qui combine ensuite ce résultat avec
 * `RecurringTransactionRepository.observePendingOccurrences`. */
private data class DashboardBaseData(
    val accounts: List<Account>,
    val transactions: List<Transaction>,
    val categories: List<Category>,
    val budgets: List<Budget>,
    val user: User?
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    categoryRepository: CategoryRepository,
    budgetRepository: BudgetRepository,
    authRepository: AuthRepository,
    sessionManager: SessionManager,
    recurringTransactionRepository: RecurringTransactionRepository,
    private val syncButtonController: SyncButtonController
) : ViewModel() {

    /** Voir [SyncButtonController] : même logique que le bouton "Synchroniser maintenant" de
     *  l'écran Paramètres (voir `SettingsViewModel`), réutilisée telle quelle pour le bouton de
     *  synchronisation du Dashboard (voir `fragment_dashboard.xml`, `syncButton`). */
    val syncNowState: StateFlow<SyncNowUiState> = syncButtonController.syncNowState

    /** Voir [SyncButtonController.indicatorState] : [SyncIndicatorUiState.pendingCount] alimente
     *  le badge de `syncButton` (voir `DashboardFragment.renderSyncButton`) — masqué quand `0` ou
     *  quand aucune session serveur n'est active ([com.arzikina.ne.presentation.components.SyncIndicatorLevel.HIDDEN]). */
    val syncIndicatorState: StateFlow<SyncIndicatorUiState> = syncButtonController.indicatorState(viewModelScope)

    val syncEvents: SharedFlow<SyncButtonEvent> = syncButtonController.events

    /** Voir [SyncButtonController.syncNow] pour le détail (ordre push/pull, garde de ré-entrance). */
    fun syncNow() = syncButtonController.syncNow(viewModelScope)

    val uiState: StateFlow<AppResult<DashboardUiState>> = combine(
        combine(
            accountRepository.observeAccounts(),
            transactionRepository.observeTransactions(),
            categoryRepository.observeCategories(),
            budgetRepository.observeBudgets(),
            sessionManager.observeCurrentUserId().flatMapLatest { userId ->
                if (userId == null) flowOf(null) else authRepository.observeUser(userId)
            }
        ) { accounts, transactions, categories, budgets, user ->
            DashboardBaseData(accounts, transactions, categories, budgets, user)
        },
        recurringTransactionRepository.observePendingOccurrences()
    ) { base, pendingOccurrences ->
        val (accounts, transactions, categories, budgets, user) = base
        val accountsById = accounts.associateBy { it.id }
        val categoriesById = categories.associateBy { it.id }
        // Périmètre "statistiques personnelles" (voir PersonalStatistics) : SEUL endroit du
        // Dashboard qui teste `isExcludedFromStatistics`, indirectement via ce filtre centralisé.
        val personalScope = PersonalStatistics.scope(accounts, transactions)

        val currentMonth = YearMonth.now()
        val monthlyPersonalTransactions = personalScope.transactions.filter { transaction ->
            YearMonth.from(transaction.dateAsZonedDateTime()) == currentMonth
        }

        val balances = computeBalances(accounts, transactions, personalScope.accounts)

        DashboardUiState(
            balances = balances,
            monthlyIncome = sumByAccountCurrency(monthlyPersonalTransactions, TransactionType.INCOME, accountsById),
            monthlyExpense = sumByAccountCurrency(monthlyPersonalTransactions, TransactionType.EXPENSE, accountsById),
            budgetGap = computeBudgetGap(balances, budgets, personalScope.transactions, accountsById),
            // Flux d'activité brut, PAS une statistique agrégée : continue d'afficher les
            // transactions de TOUS les comptes, exclus ou non (voir cahier des charges). Une
            // transaction de frais liée n'apparaît en revanche jamais comme sa propre ligne (voir
            // feeTransactionIds) — exclue AVANT take() pour ne jamais réduire ces 5 lignes à moins
            // de 5 vraies transactions.
            recentTransactions = run {
                val transactionsById = transactions.associateBy { it.id }
                val feeTransactionIds = transactions.feeTransactionIds()
                transactions
                    .filter { it.id !in feeTransactionIds }
                    .take(RECENT_TRANSACTIONS_LIMIT)
                    .map { transaction ->
                        TransactionUiItem(
                            transaction = transaction,
                            account = accountsById[transaction.accountId],
                            // categoryId est `null` pour un transfert (voir TransactionType.TRANSFER).
                            category = transaction.categoryId?.let { categoriesById[it] },
                            feeAmount = transaction.feeTransactionId?.let { transactionsById[it]?.amount }
                        )
                    }
            },
            featuredBudget = featuredBudget(budgets, categoriesById, personalScope.transactions, accountsById),
            userFullName = user?.fullName.orEmpty(),
            userProfilePhotoUri = user?.profilePhotoUri,
            cardNumberLastDigits = cardNumberLastDigits(user?.id),
            pendingRecurringCount = pendingOccurrences.size
        )
    }
        .map<DashboardUiState, AppResult<DashboardUiState>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    /**
     * Solde total PERSONNEL (comptes exclus des statistiques non comptés), regroupé par devise
     * (voir [CurrencyAmount]).
     *
     * Le solde de CHAQUE compte est calculé via [computeCurrentBalances] sur la totalité des
     * comptes/transactions ([allAccounts]/[allTransactions], jamais filtrés) : un virement
     * touchant un compte exclu doit rester exact des DEUX côtés (débit et crédit), y compris pour
     * le compte inclus concerné. Seule la SOMME finale ne parcourt que [includedAccounts] (voir
     * [PersonalStatistics]) — filtrer les comptes à additionner plutôt que les transactions en
     * amont évite de fausser le solde réel d'un compte inclus qui aurait reçu un virement d'un
     * compte exclu (ou l'inverse).
     */
    private fun computeBalances(
        allAccounts: List<Account>,
        allTransactions: List<Transaction>,
        includedAccounts: List<Account>
    ): List<CurrencyAmount> {
        val balancesByAccount = computeCurrentBalances(allAccounts, allTransactions)
        return includedAccounts
            .groupBy { it.currencyCode }
            .map { (currencyCode, accountsInCurrency) ->
                val total = accountsInCurrency.sumOf { account -> balancesByAccount[account.id] ?: account.initialBalance }
                CurrencyAmount(currencyCode, total)
            }
    }

    /**
     * "Écart Budget" — voir la doc complète de [DashboardUiState.budgetGap]. Choisit la devise
     * exactement comme [DashboardFragment.renderIncomeExpense] choisit celle de `differenceValue`
     * (première devise de [balances], repli [Constants.DEFAULT_CURRENCY_CODE]) pour rester cohérent
     * avec le reste de cette carte plutôt que d'introduire une troisième convention de choix de
     * devise sur le même écran.
     */
    private fun computeBudgetGap(
        balances: List<CurrencyAmount>,
        budgets: List<Budget>,
        personalTransactions: List<Transaction>,
        accountsById: Map<Long, Account>
    ): CurrencyAmount {
        val currencyCode = balances.firstOrNull()?.currencyCode ?: Constants.DEFAULT_CURRENCY_CODE
        val totalMinor = balances.firstOrNull()?.amountMinor ?: 0L
        val remainingMinor = activeBudgetsRemainingMinor(budgets, personalTransactions, accountsById, currencyCode)
        return CurrencyAmount(currencyCode, totalMinor - remainingMinor)
    }

    /**
     * Somme des montants RESTANTS ([Budget.limitAmount] − [BudgetProgress.Result.spentMinor]) des
     * budgets dont la période est actuellement EN COURS — réutilise [BudgetProgress.compute] (même
     * "spent" que [featuredBudget]/l'écran Budget, aucun second calcul divergent) et
     * [BudgetPace.of] pour le statut de période, seule fonction du projet qui résout correctement
     * les bornes d'un budget RÉCURRENT (pas seulement à dates fixes, contrairement à
     * [BudgetPeriodStatus.of] qui renvoie `null` dans ce cas) — équivalent exact de
     * `budgetPeriodStatus`/`resolveBudgetPeriodBounds` côté Web (`services/finance.ts`).
     *
     * `today` calculé UNE SEULE fois par appelant et transmis aux deux fonctions (au lieu de
     * laisser chacune appeler `LocalDate.now()` séparément) : évite tout risque, même infime, de
     * changement de jour civil entre les deux appels.
     *
     * Budgets filtrés sur [currencyCode] AVANT sommation : ce filtre n'a pas d'équivalent côté Web
     * (qui ne gère qu'une devise globale) — nécessaire côté Android, qui est multi-devises, pour ne
     * jamais additionner des montants de devises différentes (voir la doc de [CurrencyAmount]).
     */
    private fun activeBudgetsRemainingMinor(
        budgets: List<Budget>,
        personalTransactions: List<Transaction>,
        accountsById: Map<Long, Account>,
        currencyCode: String
    ): Long {
        val today = LocalDate.now()
        return budgets
            .asSequence()
            .filter { it.currencyCode == currencyCode }
            .map { budget -> budget to BudgetProgress.compute(budget, personalTransactions, accountsById, today) }
            .filter { (budget, result) ->
                BudgetPace.of(budget, result.spentMinor, today).periodStatus == BudgetPeriodStatus.ONGOING
            }
            .sumOf { (budget, result) -> budget.limitAmount - result.spentMinor }
    }

    private fun sumByAccountCurrency(
        transactions: List<Transaction>,
        type: TransactionType,
        accountsById: Map<Long, Account>
    ): List<CurrencyAmount> =
        transactions
            .filter { it.type == type }
            .mapNotNull { transaction -> accountsById[transaction.accountId]?.let { it.currencyCode to transaction.amount } }
            .groupBy({ it.first }, { it.second })
            .map { (currencyCode, amounts) -> CurrencyAmount(currencyCode, amounts.sum()) }

    /**
     * Le budget le plus urgent = celui dont [BudgetProgress.Result.progress]
     * est le plus élevé (dépassement inclus) ; `null` s'il n'y a aucun budget.
     * Même règle de calcul que [com.arzikina.ne.presentation.budget.BudgetViewModel],
     * voir [BudgetProgress].
     */
    private fun featuredBudget(
        budgets: List<Budget>,
        categoriesById: Map<Long, Category>,
        transactions: List<Transaction>,
        accountsById: Map<Long, Account>
    ): BudgetUiItem? = budgets
        .map { budget ->
            val result = BudgetProgress.compute(budget, transactions, accountsById)
            BudgetUiItem(
                budget = budget,
                category = categoriesById[budget.categoryId],
                spentMinor = result.spentMinor,
                progress = result.progress
            )
        }
        .maxByOrNull { it.progress }

    /**
     * 4 chiffres décoratifs (voir [DashboardUiState.cardNumberLastDigits]) —
     * multiplie [userId] par un nombre premier avant de tronquer à 4 chiffres
     * pour éviter un motif trop reconnaissable (ex. "0001", "0002"... pour les
     * tout premiers utilisateurs) tout en restant stable pour un même utilisateur.
     */
    private fun cardNumberLastDigits(userId: Long?): String {
        val id = userId ?: return "0000"
        return ((id * 7_919L) % 10_000L).toString().padStart(4, '0')
    }

    private fun Transaction.dateAsZonedDateTime() =
        Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault())
}
