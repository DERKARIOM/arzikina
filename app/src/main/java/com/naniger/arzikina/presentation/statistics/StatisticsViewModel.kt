package com.naniger.arzikina.presentation.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.Category
import com.naniger.arzikina.domain.model.Transaction
import com.naniger.arzikina.domain.model.TransactionType
import com.naniger.arzikina.domain.model.UserPreferences
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.CategoryRepository
import com.naniger.arzikina.domain.repository.TransactionRepository
import com.naniger.arzikina.domain.repository.UserPreferencesRepository
import com.naniger.arzikina.util.AppResult
import com.naniger.arzikina.util.DatePeriods
import com.naniger.arzikina.util.PersonalStatistics
import com.naniger.arzikina.util.StatsPeriodPreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/** Nombre de mois affichés dans le graphique d'évolution. */
private const val EVOLUTION_MONTHS_COUNT = 6

/** Répartition (dépenses OU revenus, voir [BreakdownType]) de la période sélectionnée, par catégorie. */
data class CategoryBreakdownItem(
    val category: Category?,
    val amountMinor: Long,
    val percentage: Float
)

/**
 * Type de mouvement représenté par la section "Répartition" (voir [StatisticsFragment], Spinner
 * dédié) — [TransactionType.EXPENSE]/[TransactionType.INCOME] uniquement : les virements
 * ([TransactionType.TRANSFER]) n'ont pas de catégorie de dépense/revenu à répartir, même exclusion
 * que l'ancien comportement (figé sur les dépenses) avant ce chantier.
 */
enum class BreakdownType {
    EXPENSE,
    INCOME
}

/** Un point du graphique d'évolution mensuelle. */
data class MonthlyEvolutionPoint(
    val yearMonth: YearMonth,
    val incomeMinor: Long,
    val expenseMinor: Long
)

/**
 * Sélection de période courante de l'écran Statistiques — MIROIR de `period`/`customStart`/
 * `customEnd` côté Web (`routes/statistiques.tsx`). [customStart]/[customEnd] ne sont consultés que
 * lorsque [preset] vaut [StatsPeriodPreset.CUSTOM] ; conservés même quand un autre préréglage est
 * sélectionné, pour ne pas perdre la saisie de l'utilisateur s'il revient sur "Personnalisée".
 */
data class PeriodSelection(
    val preset: StatsPeriodPreset = StatsPeriodPreset.MONTH,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null
)

/**
 * Raison pour laquelle la période personnalisée ne peut pas être appliquée — sealed sur un ENUM
 * plutôt qu'une chaîne en dur dans le ViewModel : la traduction du message revient à
 * [StatisticsFragment] (voir `strings.xml`), pour rester compatible avec la prise en charge
 * multilingue prévue par le projet (voir instructions projet, section "Évolutivité").
 */
enum class StatsPeriodError {
    MISSING_DATES,
    START_AFTER_END
}

data class StatisticsUiState(
    val currencyCode: String,
    val periodSelection: PeriodSelection,
    val periodError: StatsPeriodError?,
    val totalIncomeMinor: Long,
    val totalExpenseMinor: Long,
    val totalNetMinor: Long,
    val breakdownType: BreakdownType,
    val categoryBreakdown: List<CategoryBreakdownItem>,
    val monthlyEvolution: List<MonthlyEvolutionPoint>
)

/** Résolution interne de [PeriodSelection] en bornes epoch ms — voir [StatisticsViewModel.resolvePeriod]. */
private sealed interface PeriodResolution {
    data class Valid(val startMillis: Long, val endMillis: Long) : PeriodResolution
    data class Invalid(val error: StatsPeriodError) : PeriodResolution
}

/** Regroupe les 4 flux "données" de l'écran pour rester dans la limite de 5 arguments de
 *  `combine` une fois [StatisticsViewModel.periodSelection]/[StatisticsViewModel.breakdownType]
 *  ajoutés — même principe que `DashboardBaseData` côté Dashboard. */
private data class StatisticsBaseData(
    val accounts: List<Account>,
    val categories: List<Category>,
    val transactions: List<Transaction>,
    val preferences: UserPreferences
)

/**
 * ViewModel de l'écran Statistiques : totaux revenus/dépenses/solde et
 * répartition par catégorie (dépenses OU revenus, voir [BreakdownType]) sur une période choisie
 * par l'utilisateur ([PeriodSelection]/[StatsPeriodPreset]) — même formule et
 * mêmes préréglages que la page Web `statistiques.tsx`
 * ([StatsPeriodPreset.toDateRange], miroir de `resolveStatsPeriodRange`).
 *
 * Le graphique d'évolution ([computeMonthlyEvolution]) reste volontairement
 * INDÉPENDANT de cette sélection : il répond à une question différente
 * ("comment évolue mon budget dans le temps ?") de celle du sélecteur de
 * période ("combien ai-je dépensé sur CETTE période ?"), et une tendance
 * mensuelle sur 6 mois n'a pas de sens pour une période personnalisée d'un
 * jour ou d'une semaine — décision explicitement confirmée avant
 * implémentation (voir échanges du chantier).
 *
 * Limite volontaire, inchangée depuis avant ce chantier : seules les
 * transactions dont le compte est dans la devise principale de l'utilisateur
 * ([UserPreferencesRepository]) sont prises en compte (pas de conversion de
 * change). Les comptes exclus des statistiques personnelles
 * ([PersonalStatistics]) sont retirés AVANT ce filtre de devise.
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val periodSelection = MutableStateFlow(PeriodSelection())
    private val breakdownType = MutableStateFlow(BreakdownType.EXPENSE)

    val uiState: StateFlow<AppResult<StatisticsUiState>> = combine(
        combine(
            accountRepository.observeAccounts(),
            categoryRepository.observeCategories(),
            transactionRepository.observeTransactions(),
            userPreferencesRepository.observePreferences()
        ) { accounts, categories, transactions, preferences ->
            StatisticsBaseData(accounts, categories, transactions, preferences)
        },
        periodSelection,
        breakdownType
    ) { base, selection, type ->
        val (accounts, categories, transactions, preferences) = base
        val categoriesById = categories.associateBy { it.id }
        val personalScope = PersonalStatistics.scope(accounts, transactions)
        val primaryCurrencyAccountIds = personalScope.accounts
            .filter { it.currencyCode == preferences.currencyCode }
            .map { it.id }
            .toSet()
        val relevantTransactions = personalScope.transactions.filter { it.accountId in primaryCurrencyAccountIds }

        val resolution = resolvePeriod(selection)
        val periodTransactions = when (resolution) {
            is PeriodResolution.Valid ->
                relevantTransactions.filter { it.date in resolution.startMillis..resolution.endMillis }
            is PeriodResolution.Invalid -> emptyList()
        }
        val totalIncome = periodTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpense = periodTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

        StatisticsUiState(
            currencyCode = preferences.currencyCode,
            periodSelection = selection,
            periodError = (resolution as? PeriodResolution.Invalid)?.error,
            totalIncomeMinor = totalIncome,
            totalExpenseMinor = totalExpense,
            totalNetMinor = totalIncome - totalExpense,
            breakdownType = type,
            categoryBreakdown = computeCategoryBreakdown(periodTransactions, categoriesById, type),
            // Volontairement basé sur relevantTransactions (PAS periodTransactions) : voir la doc
            // de tête de la classe.
            monthlyEvolution = computeMonthlyEvolution(relevantTransactions)
        )
    }
        .map<StatisticsUiState, AppResult<StatisticsUiState>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    /**
     * Choix d'un préréglage (Spinner de période, voir [StatisticsFragment]). Cas
     * particulier de [StatsPeriodPreset.CUSTOM] sélectionné pour la toute première fois (aucune date
     * personnalisée encore choisie) : pré-remplit avec le mois en cours plutôt que de laisser les
     * deux champs vides — évite d'afficher immédiatement [StatsPeriodError.MISSING_DATES] avant même
     * que l'utilisateur ait pu agir, même principe que `routes/statistiques.tsx` côté Web (les deux
     * `<input type="date">` y sont toujours pré-remplis).
     */
    fun onPresetSelected(preset: StatsPeriodPreset) {
        periodSelection.update { current ->
            if (preset == StatsPeriodPreset.CUSTOM && current.customStart == null && current.customEnd == null) {
                val (start, end) = StatsPeriodPreset.MONTH.toDateRange()!!
                current.copy(preset = preset, customStart = start, customEnd = end)
            } else {
                current.copy(preset = preset)
            }
        }
    }

    fun onCustomStartSelected(date: LocalDate) {
        periodSelection.update { it.copy(preset = StatsPeriodPreset.CUSTOM, customStart = date) }
    }

    fun onCustomEndSelected(date: LocalDate) {
        periodSelection.update { it.copy(preset = StatsPeriodPreset.CUSTOM, customEnd = date) }
    }

    /** Revient au mois en cours — même comportement que le bouton "Réinitialiser" côté Web. */
    fun onResetPeriod() {
        periodSelection.value = PeriodSelection()
    }

    /** Choix du type de mouvement représenté par "Répartition" (Spinner dédié, voir [StatisticsFragment]). */
    fun onBreakdownTypeSelected(type: BreakdownType) {
        breakdownType.value = type
    }

    /**
     * Résout [selection] en bornes epoch ms INCLUSIVES (23:59:59 pour la fin, voir
     * [DatePeriods.toEpochMillisEndOfDay]) — ou en [StatsPeriodError] si la période personnalisée
     * n'est pas exploitable. Aucun recalcul de statistiques n'est effectué dans ce dernier cas (voir
     * l'appelant, qui retombe sur une liste de transactions vide).
     */
    private fun resolvePeriod(selection: PeriodSelection): PeriodResolution {
        if (selection.preset != StatsPeriodPreset.CUSTOM) {
            val (start, end) = selection.preset.toDateRange()!!
            return PeriodResolution.Valid(DatePeriods.toEpochMillis(start), DatePeriods.toEpochMillisEndOfDay(end))
        }
        val start = selection.customStart
        val end = selection.customEnd
        if (start == null || end == null) return PeriodResolution.Invalid(StatsPeriodError.MISSING_DATES)
        if (start.isAfter(end)) return PeriodResolution.Invalid(StatsPeriodError.START_AFTER_END)
        return PeriodResolution.Valid(DatePeriods.toEpochMillis(start), DatePeriods.toEpochMillisEndOfDay(end))
    }

    private fun computeCategoryBreakdown(
        transactions: List<Transaction>,
        categoriesById: Map<Long, Category>,
        type: BreakdownType
    ): List<CategoryBreakdownItem> {
        val transactionType = when (type) {
            BreakdownType.EXPENSE -> TransactionType.EXPENSE
            BreakdownType.INCOME -> TransactionType.INCOME
        }
        val matching = transactions.filter { it.type == transactionType }
        val total = matching.sumOf { it.amount }
        if (total <= 0L) return emptyList()

        // categoryId n'est `null` que pour un transfert (voir TransactionType.TRANSFER), déjà
        // exclu de `matching` par le filtre `type == transactionType` ci-dessus ; mapNotNull
        // couvre quand même ce cas pour rester correct si l'invariant venait à changer.
        return matching
            .mapNotNull { transaction -> transaction.categoryId?.let { it to transaction } }
            .groupBy({ (categoryId, _) -> categoryId }, { (_, transaction) -> transaction })
            .map { (categoryId, txs) ->
                val amount = txs.sumOf { it.amount }
                CategoryBreakdownItem(
                    category = categoriesById[categoryId],
                    amountMinor = amount,
                    percentage = amount.toFloat() / total.toFloat()
                )
            }
            .sortedByDescending { it.amountMinor }
    }

    private fun computeMonthlyEvolution(transactions: List<Transaction>): List<MonthlyEvolutionPoint> {
        val currentMonth = YearMonth.now()
        val months = (EVOLUTION_MONTHS_COUNT - 1 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
        val transactionsByMonth = transactions.groupBy { it.date.toYearMonth() }

        return months.map { month ->
            val monthTransactions = transactionsByMonth[month].orEmpty()
            MonthlyEvolutionPoint(
                yearMonth = month,
                incomeMinor = monthTransactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                expenseMinor = monthTransactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            )
        }
    }

    private fun Long.toYearMonth(): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
}
