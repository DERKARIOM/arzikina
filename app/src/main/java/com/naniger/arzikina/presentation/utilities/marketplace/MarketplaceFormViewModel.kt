package com.naniger.arzikina.presentation.utilities.marketplace

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.Category
import com.naniger.arzikina.domain.model.TransactionTemplate
import com.naniger.arzikina.domain.model.TransactionType
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.CategoryRepository
import com.naniger.arzikina.domain.repository.TransactionRepository
import com.naniger.arzikina.domain.repository.TransactionTemplateRepository
import com.naniger.arzikina.presentation.accounts.computeCurrentBalances
import com.naniger.arzikina.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * État du formulaire de création/édition d'un modèle (voir [TransactionTemplate]).
 *
 * [isFavorite] : chargé tel quel en mode édition (voir [MarketplaceFormViewModel.init]) et renvoyé
 * inchangé à [TransactionTemplateRepository.saveTemplate] — jamais modifiable depuis CE formulaire
 * (cahier des charges section 6/7 : la bascule ⭐ reste une action ISOLÉE du menu ⋮, voir
 * `MarketplaceViewModel.onToggleFavorite`), pour ne jamais désactiver silencieusement le favori
 * d'un modèle simplement parce qu'on modifie un autre champ — même raisonnement que
 * `RecurringTransactionFormState.isActive`.
 *
 * `TransactionTemplate.id == 0L` fait office de sentinelle "nouveau modèle" (même convention que
 * [com.naniger.arzikina.domain.model.RecurringTransaction.id]).
 */
data class MarketplaceFormState(
    val name: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val amountInput: String = "",
    val categoryId: Long = 0L,
    val accountId: Long = 0L,
    val description: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long? = null,
    // Heure par défaut optionnelle (voir TransactionTemplate.defaultHour/defaultMinute) : même
    // principe que RecurringTransactionFormState.hasEndDate/endDate — hasDefaultTime pilote
    // l'affichage ET ce qui est persisté (null/null si false), defaultHour/defaultMinute
    // conservent la dernière sélection même quand le switch est désactivé (pas reperdue si
    // l'utilisateur le réactive). Défauts 08:00 alignés sur
    // RecurringTransaction.DEFAULT_TRIGGER_HOUR/MINUTE.
    val hasDefaultTime: Boolean = false,
    val defaultHour: Int = 8,
    val defaultMinute: Int = 0,
    @StringRes val nameError: Int? = null,
    @StringRes val amountError: Int? = null,
    @StringRes val categoryError: Int? = null,
    @StringRes val accountError: Int? = null
)

sealed interface MarketplaceFormEvent {
    data object Saved : MarketplaceFormEvent
    data object Deleted : MarketplaceFormEvent
}

/**
 * ViewModel du formulaire de modèle (cahier des charges "Marketplace personnelle", section 3) —
 * même structure que [com.naniger.arzikina.presentation.utilities.recurring.RecurringTransactionFormViewModel],
 * volontairement plus simple (pas de date/fréquence de déclenchement — seule l'heure par défaut
 * OPTIONNELLE est reprise, voir [MarketplaceFormState.hasDefaultTime]).
 */
@HiltViewModel
class MarketplaceFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val templateRepository: TransactionTemplateRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    transactionRepository: TransactionRepository
) : ViewModel() {

    private val templateId: Long = MarketplaceFormFragmentArgs.fromSavedStateHandle(savedStateHandle).templateId
    val isEditMode: Boolean = templateId != 0L

    private val _formState = MutableStateFlow(MarketplaceFormState())
    val formState: StateFlow<MarketplaceFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<MarketplaceFormEvent>()
    val events: SharedFlow<MarketplaceFormEvent> = _events.asSharedFlow()

    /** Chargés une fois : la liste des comptes ne dépend pas du reste du formulaire (voir
     * `TransactionFormViewModel.accounts`, même principe). */
    val accounts: StateFlow<List<Account>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Solde COURANT de chaque compte, pour [com.naniger.arzikina.presentation.components.AccountPickerDialog]
     * (voir sa doc : jamais [Account.initialBalance]) — même principe que
     * `RecurringTransactionFormViewModel.accountBalances`. */
    val accountBalances: StateFlow<Map<Long, Long>> = combine(
        accounts,
        transactionRepository.observeTransactions()
    ) { accounts, transactions -> computeCurrentBalances(accounts, transactions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Recalculée à chaque changement de type (revenu/dépense) — voir
     * `RecurringTransactionFormViewModel.categories`, même principe. */
    val categories: StateFlow<List<Category>> = _formState
        .map { it.type }
        .distinctUntilChanged()
        .flatMapLatest { type -> categoryRepository.observeCategoriesByType(type) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (isEditMode) {
            viewModelScope.launch {
                templateRepository.getTemplate(templateId)?.let { template ->
                    _formState.update {
                        it.copy(
                            name = template.name,
                            type = template.type,
                            amountInput = Money.formatForInput(template.amount),
                            categoryId = template.categoryId,
                            accountId = template.accountId,
                            description = template.description,
                            isFavorite = template.isFavorite,
                            createdAt = template.createdAt,
                            hasDefaultTime = template.defaultHour != null,
                            defaultHour = template.defaultHour ?: 8,
                            defaultMinute = template.defaultMinute ?: 0
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _formState.update { it.copy(name = value, nameError = null) }
    }

    fun onTypeChange(type: TransactionType) {
        // Une catégorie de dépense n'a pas de sens pour un revenu (et inversement, voir
        // `Category.type`) : remise à zéro pour forcer un nouveau choix cohérent avec [categories],
        // même principe que `RecurringTransactionFormViewModel.onTypeChange`.
        _formState.update { it.copy(type = type, categoryId = 0L, categoryError = null) }
    }

    fun onAmountChange(value: String) {
        _formState.update { it.copy(amountInput = value, amountError = null) }
    }

    fun onCategoryChange(categoryId: Long) {
        _formState.update { it.copy(categoryId = categoryId, categoryError = null) }
    }

    fun onAccountChange(accountId: Long) {
        _formState.update { it.copy(accountId = accountId, accountError = null) }
    }

    fun onDescriptionChange(value: String) {
        _formState.update { it.copy(description = value) }
    }

    fun onDefaultTimeToggle(enabled: Boolean) {
        _formState.update { it.copy(hasDefaultTime = enabled) }
    }

    fun onDefaultTimeChange(hour: Int, minute: Int) {
        _formState.update { it.copy(defaultHour = hour, defaultMinute = minute) }
    }

    fun save() {
        val state = _formState.value

        val amountMinor = Money.parseToMinorUnits(state.amountInput)
        val amountValid = amountMinor != null && amountMinor > 0L
        val nameValid = state.name.isNotBlank()
        val categoryValid = state.categoryId != 0L
        val accountValid = state.accountId != 0L

        // Messages en dur (pas de `Context` dans ce ViewModel) : même convention que
        // `RecurringTransactionFormViewModel.save`/`TransactionFormViewModel.save`.
        if (!nameValid || !amountValid || !categoryValid || !accountValid) {
            _formState.update {
                it.copy(
                    nameError = if (!nameValid) R.string.error_name_required else null,
                    amountError = if (!amountValid) R.string.error_invalid_amount else null,
                    categoryError = if (!categoryValid) R.string.error_select_category else null,
                    accountError = if (!accountValid) R.string.error_select_account else null
                )
            }
            return
        }

        viewModelScope.launch {
            templateRepository.saveTemplate(
                TransactionTemplate(
                    id = templateId,
                    name = state.name.trim(),
                    type = state.type,
                    amount = amountMinor,
                    categoryId = state.categoryId,
                    accountId = state.accountId,
                    description = state.description,
                    isFavorite = state.isFavorite,
                    defaultHour = state.defaultHour.takeIf { state.hasDefaultTime },
                    defaultMinute = state.defaultMinute.takeIf { state.hasDefaultTime },
                    // Valeurs ignorées/recalculées par le repository (voir la doc de
                    // `TransactionTemplateRepositoryImpl.saveTemplate`), jamais lues depuis ce
                    // formulaire — même raisonnement que `RecurringTransactionFormViewModel.save`.
                    createdAt = state.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            _events.emit(MarketplaceFormEvent.Saved)
        }
    }

    fun delete() {
        if (!isEditMode) return
        viewModelScope.launch {
            templateRepository.deleteTemplate(templateId)
            _events.emit(MarketplaceFormEvent.Deleted)
        }
    }
}
