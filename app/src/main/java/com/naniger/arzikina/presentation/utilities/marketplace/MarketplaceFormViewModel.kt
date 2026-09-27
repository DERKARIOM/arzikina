package com.naniger.arzikina.presentation.utilities.marketplace

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.Category
import com.naniger.arzikina.domain.model.TemplateAlreadyLinkedException
import com.naniger.arzikina.domain.model.TransactionTemplate
import com.naniger.arzikina.domain.model.TransactionType
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.CategoryRepository
import com.naniger.arzikina.domain.repository.TransactionRepository
import com.naniger.arzikina.domain.repository.TransactionTemplateRepository
import com.naniger.arzikina.presentation.accounts.computeCurrentBalances
import com.naniger.arzikina.presentation.components.DefaultNameLocalizer
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
    @StringRes val accountError: Int? = null,
    /**
     * « Créer un modèle à partir d'une transaction » (voir [MarketplaceFormViewModel.isFromTransaction]) :
     * id du modèle DÉJÀ créé à partir de cette transaction, découvert à l'ouverture ou au moment
     * d'enregistrer (voir `TemplateAlreadyLinkedException`) — [MarketplaceFormFragment] affiche
     * alors un message et propose « Voir le modèle » au lieu d'en créer un second. Un état plutôt
     * qu'un [MarketplaceFormEvent] : il peut être découvert dès l'`init`, avant que l'écran
     * n'écoute les événements (une émission serait alors perdue).
     */
    val alreadyLinkedTemplateId: Long? = null,
    /** Transaction d'origine introuvable (supprimée entre-temps) ou non convertible en modèle
     * (transfert, ligne de frais) — voir [TemplateFromTransaction.isEligible]. Même raison d'être un
     * état que [alreadyLinkedTemplateId]. */
    val sourceTransactionUnavailable: Boolean = false
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
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val defaultNameLocalizer: DefaultNameLocalizer
) : ViewModel() {

    private val args = MarketplaceFormFragmentArgs.fromSavedStateHandle(savedStateHandle)
    private val templateId: Long = args.templateId
    val isEditMode: Boolean = templateId != 0L

    /** « Créer un modèle à partir d'une transaction » (voir `TransactionTemplate.sourceTransactionId`) :
     * transaction d'origine, uniquement en CRÉATION (ignorée en édition — la relation d'un modèle
     * existant ne change jamais). `null` pour une création classique depuis la Marketplace. */
    private val sourceTransactionId: Long? = args.sourceTransactionId.takeIf { it != 0L && !isEditMode }
    val isFromTransaction: Boolean = sourceTransactionId != null

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
        } else if (sourceTransactionId != null) {
            viewModelScope.launch { prefillFromTransaction(sourceTransactionId) }
        }
    }

    /**
     * Préremplit le formulaire à partir de la transaction enregistrée (valeurs EN BASE, jamais
     * des modifications non enregistrées) selon [TemplateFromTransaction.prefillOf]. Rien n'est
     * créé ici : l'utilisateur relit/modifie puis confirme avec « Enregistrer » (le formulaire
     * prérempli tient lieu de confirmation). La transaction n'est que LUE.
     */
    private suspend fun prefillFromTransaction(transactionId: Long) {
        templateRepository.getTemplateCreatedFromTransaction(transactionId)?.let { existing ->
            _formState.update { it.copy(alreadyLinkedTemplateId = existing.id) }
            return
        }
        val transaction = transactionRepository.getTransaction(transactionId)
        if (transaction == null || !TemplateFromTransaction.isEligible(transaction)) {
            _formState.update { it.copy(sourceTransactionUnavailable = true) }
            return
        }
        val categoryName = transaction.categoryId
            ?.let { categoryRepository.getCategory(it) }
            ?.let { defaultNameLocalizer.displayName(it) }
        val prefill = TemplateFromTransaction.prefillOf(transaction, categoryName)
        _formState.update {
            it.copy(
                name = prefill.name,
                type = prefill.type,
                amountInput = Money.formatForInput(prefill.amount),
                categoryId = prefill.categoryId,
                accountId = prefill.accountId,
                description = prefill.description
            )
        }
    }

    fun onNameChange(value: String) {
        _formState.update { it.copy(name = value, nameError = null) }
    }

    fun onTypeChange(type: TransactionType) {
        // Une catégorie de dépense n'a pas de sens pour un revenu (et inversement, voir
        // `Category.type`) : remise à zéro pour forcer un nouveau choix cohérent avec [categories],
        // même principe que `RecurringTransactionFormViewModel.onTypeChange`.
        // Même type : rien à réinitialiser. Indispensable car MarketplaceFormFragment.render coche
        // lui-même le bouton du type courant (ce qui rappelle cette méthode) — sans cette garde, la
        // catégorie d'un modèle de REVENU chargé/prérempli serait aussitôt effacée.
        _formState.update { if (it.type == type) it else it.copy(type = type, categoryId = 0L, categoryError = null) }
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
            try {
                saveTemplate(state, amountMinor)
            } catch (e: TemplateAlreadyLinkedException) {
                // Un modèle a été créé à partir de cette transaction entre l'ouverture du formulaire
                // et maintenant : aucun second modèle, voir alreadyLinkedTemplateId.
                _formState.update { it.copy(alreadyLinkedTemplateId = e.existingTemplateId) }
                return@launch
            }
            _events.emit(MarketplaceFormEvent.Saved)
        }
    }

    private suspend fun saveTemplate(state: MarketplaceFormState, amountMinor: Long) {
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
                // Pris en compte à la création uniquement (voir TransactionTemplateRepository.saveTemplate).
                sourceTransactionId = sourceTransactionId,
                // Valeurs ignorées/recalculées par le repository (voir la doc de
                // `TransactionTemplateRepositoryImpl.saveTemplate`), jamais lues depuis ce
                // formulaire — même raisonnement que `RecurringTransactionFormViewModel.save`.
                createdAt = state.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun delete() {
        if (!isEditMode) return
        viewModelScope.launch {
            templateRepository.deleteTemplate(templateId)
            _events.emit(MarketplaceFormEvent.Deleted)
        }
    }
}
