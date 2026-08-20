package com.arzikina.ne.presentation.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.domain.model.FeeCategoryNames
import com.arzikina.ne.domain.model.FeeType
import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionFee
import com.arzikina.ne.domain.model.TransactionType
import com.arzikina.ne.domain.model.LoanCategoryNames
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.domain.repository.CategoryRepository
import com.arzikina.ne.domain.repository.LoanRepository
import com.arzikina.ne.domain.repository.TransactionRepository
import com.arzikina.ne.presentation.accounts.computeCurrentBalances
import com.arzikina.ne.util.Money
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * État du formulaire d'ajout/édition de transaction.
 *
 * `Transaction.id == 0L` fait office de sentinelle "nouvelle transaction"
 * (même convention que [Transaction.id]). `dateTimeMillis` fusionne date et
 * heure : le formulaire les édite séparément ([DatePickerField]/
 * [TimePickerField]) mais le domaine ne stocke qu'un seul instant.
 */
data class TransactionFormState(
    val amountInput: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val accountId: Long = 0L,
    /** Compte destination d'un transfert (voir [TransactionType.TRANSFER]) ; même
     * convention "0L = non choisi" que [accountId]/[categoryId]. Sans objet pour
     * un revenu/une dépense — voir [TransactionFormViewModel.onTypeChange]. */
    val transferAccountId: Long = 0L,
    val categoryId: Long = 0L,
    val dateTimeMillis: Long = System.currentTimeMillis(),
    val description: String = "",
    /** `true` quand [description] a été générée par [TransactionFormViewModel.autoFillTransferDescription]
     * (voir sa doc) plutôt que saisie par l'utilisateur — permet de continuer à la régénérer tant
     * qu'il n'y a pas touché, sans jamais écraser une saisie volontaire. Sans effet hors transfert. */
    val isDescriptionAutoFilled: Boolean = false,
    val paymentMethod: PaymentMethod? = null,
    val createdAt: Long? = null,
    val amountError: String? = null,
    val accountError: String? = null,
    val categoryError: String? = null,
    val transferAccountError: String? = null,
    /** Voir cahier des charges "Gestion des frais supplémentaires sur les transactions" —
     * `false` par défaut, y compris pour une nouvelle transaction. Révèle [feeAmountInput]/
     * [feeType]/[feeAccountId]/[feeDescriptionInput] dans le formulaire. */
    val hasFee: Boolean = false,
    val feeAmountInput: String = "",
    val feeType: FeeType = FeeType.TRANSFER,
    /** Même convention "0L = non choisi" que [accountId] — mais synchronisé automatiquement sur
     * [accountId] tant que [isFeeAccountAutoFilled] vaut `true` (voir [onAccountChange]/
     * [onHasFeeToggle]) : "par défaut le compte source" (cahier des charges), modifiable
     * explicitement ensuite. */
    val feeAccountId: Long = 0L,
    val isFeeAccountAutoFilled: Boolean = true,
    val feeDescriptionInput: String = "",
    val feeAmountError: String? = null,
    val feeAccountError: String? = null,
    /**
     * Non-`null` uniquement en modification, une fois [LoanRepository.findLoanIdForTransaction]
     * résolu (voir [TransactionFormViewModel.init]) : id du prêt/emprunt dont cette transaction est
     * le décaissement OU un remboursement. Modifier/supprimer une telle transaction ici
     * désynchroniserait `Loan.amountRepaid`/`remainingAmount`/`status` — [save]/[delete] refusent
     * tous deux dans ce cas (voir leur doc), [TransactionFormFragment] désactive aussi les champs
     * et affiche une bannière redirigeant vers "Détail du prêt/emprunt".
     */
    val linkedLoanId: Long? = null,
    /**
     * Voir [Transaction.receiptId] — `null` sauf : (a) en modification, si la transaction chargée
     * est déjà liée à un reçu (voir [TransactionFormViewModel.init]) ; (b) en création, si ce
     * formulaire a été ouvert depuis "Détail du reçu" via `presetReceiptId` (voir
     * [TransactionFormViewModel.applyReceiptPresets], cahier des charges "Créer une transaction
     * depuis un reçu"). Piloté ici (pas de nouveau champ séparé) pour rester disponible dès
     * [save] sans logique supplémentaire. Sert aussi de condition d'affichage à la bannière
     * "✨ Informations détectées depuis le reçu" (cas (b) uniquement, voir
     * [TransactionFormFragment]) et, plus tard, à la ligne "Reçu associé" en modification
     * (cas (a), Étape 8 à venir).
     */
    val receiptId: Long? = null
)

sealed interface TransactionFormEvent {
    data object Saved : TransactionFormEvent
    data object Deleted : TransactionFormEvent

    /** Tentative de [TransactionFormViewModel.save]/[TransactionFormViewModel.delete] refusée
     * (voir la doc de [TransactionFormState.linkedLoanId]) — [TransactionFormFragment] navigue
     * vers "Détail du prêt/emprunt" à la place. */
    data class LoanLinked(val loanId: Long) : TransactionFormEvent
}

@HiltViewModel
class TransactionFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val loanRepository: LoanRepository
) : ViewModel() {

    private val transactionId: Long = savedStateHandle.get<Long>(TRANSACTION_ID_ARG) ?: 0L
    val isEditMode: Boolean = transactionId != 0L

    private val _formState = MutableStateFlow(TransactionFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<TransactionFormEvent>()
    val events: SharedFlow<TransactionFormEvent> = _events.asSharedFlow()

    /** Chargés une fois : la liste des comptes ne dépend pas du reste du formulaire. */
    val accounts: StateFlow<List<Account>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Recalculée à chaque changement de type (revenu/dépense), comme sur l'écran Catégories.
     *
     * Exclut les 4 catégories système Prêts/Emprunts ([LoanCategoryNames]) : elles restent des
     * catégories normales partout ailleurs (listes de transactions, détail de compte, catégories),
     * mais ne doivent pas être choisissables ici pour une transaction manuelle — voir la doc de
     * [TransactionFormState.linkedLoanId] pour le raisonnement complet côté synchronisation.
     *
     * Exclut de la même façon la catégorie système "Frais et commissions" ([FeeCategoryNames]) :
     * elle n'est jamais choisie manuellement, seulement affectée automatiquement à la transaction
     * de frais auto-générée (voir `TransactionRepositoryImpl`).
     */
    val categories: StateFlow<List<Category>> = _formState
        .map { it.type }
        .distinctUntilChanged()
        .flatMapLatest { type -> categoryRepository.observeCategoriesByType(type) }
        .map { categories -> categories.filterNot { it.name in LoanCategoryNames.ALL || it.name in FeeCategoryNames.ALL } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Solde COURANT de chaque compte (voir [computeCurrentBalances]), affiché
     * sur la ligne "Compte" et dans [com.arzikina.ne.presentation.components.AccountPickerDialog] —
     * pas [Account.initialBalance].
     */
    val accountBalances: StateFlow<Map<Long, Long>> = combine(
        accounts,
        transactionRepository.observeTransactions()
    ) { accounts, transactions -> computeCurrentBalances(accounts, transactions) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        if (isEditMode) {
            viewModelScope.launch {
                transactionRepository.getTransaction(transactionId)?.let { transaction ->
                    _formState.update {
                        it.copy(
                            amountInput = Money.formatMajorUnits(transaction.amount),
                            type = transaction.type,
                            accountId = transaction.accountId,
                            transferAccountId = transaction.transferAccountId ?: 0L,
                            // `?: 0L` : categoryId n'est `null` que pour un transfert
                            // (voir TransactionType.TRANSFER) — 0L reste "aucune catégorie",
                            // même convention que les autres champs de ce formulaire.
                            categoryId = transaction.categoryId ?: 0L,
                            dateTimeMillis = transaction.date,
                            description = transaction.description,
                            paymentMethod = transaction.paymentMethod,
                            createdAt = transaction.createdAt,
                            receiptId = transaction.receiptId
                        )
                    }
                    // Voir la doc de [TransactionFormState.linkedLoanId] : résolu après le reste de
                    // l'état ci-dessus (pas bloquant pour l'affichage initial des champs), la
                    // désactivation/bannière apparaît dès que cette valeur arrive.
                    val loanId = loanRepository.findLoanIdForTransaction(transactionId)
                    if (loanId != null) {
                        _formState.update { it.copy(linkedLoanId = loanId) }
                    }
                    // Frais liés (voir Transaction.feeTransactionId) : chargés séparément, après
                    // l'état principal, pour la même raison que linkedLoanId ci-dessus.
                    transaction.feeTransactionId?.let { feeTransactionId ->
                        transactionRepository.getTransaction(feeTransactionId)?.let { feeTransaction ->
                            _formState.update {
                                it.copy(
                                    hasFee = true,
                                    feeAmountInput = Money.formatMajorUnits(feeTransaction.amount),
                                    feeType = feeTransaction.feeType ?: FeeType.OTHER,
                                    feeAccountId = feeTransaction.accountId,
                                    isFeeAccountAutoFilled = feeTransaction.accountId == transaction.accountId,
                                    feeDescriptionInput = feeTransaction.description
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Pré-remplit le compte quand ce formulaire est ouvert depuis
            // "Détail du compte" (voir AccountDetailFragment.navigateToNewTransactionForm), OU
            // depuis "Détail du reçu" (voir applyReceiptPresets ci-dessous, MÊME argument) :
            // 0L = "aucun compte présélectionné", même convention que transactionId.
            val presetAccountId = savedStateHandle.get<Long>(PRESET_ACCOUNT_ID_ARG) ?: 0L
            if (presetAccountId != 0L) {
                _formState.update { it.copy(accountId = presetAccountId) }
            }
            applyReceiptPresets(savedStateHandle)
        }
    }

    /**
     * Préremplissage depuis un reçu PDF (cahier des charges "Créer une transaction depuis un
     * reçu", voir `nav_graph.xml` pour la doc de chaque argument) — UNIQUEMENT à la création
     * (voir [init]), jamais en modification. Chaque champ reste indépendant : `null`/absent
     * signifie simplement "non détecté avec assez de confiance" (voir
     * `ReceiptTransactionInfoParser`, "ne jamais inventer"), pas une erreur — les champs non
     * fournis gardent leur valeur par défaut habituelle, l'utilisateur les complète comme pour
     * n'importe quelle transaction créée manuellement.
     *
     * Une seule mise à jour d'état atomique (plutôt qu'un `update` par champ) : [type] doit être
     * appliqué EN MÊME TEMPS que [categoryId] (voir [categories], filtrée par `state.type` —
     * appliquer l'un sans l'autre exposerait un instant une catégorie du mauvais type).
     */
    private fun applyReceiptPresets(savedStateHandle: SavedStateHandle) {
        val amountMinor = savedStateHandle.get<Long>(PRESET_AMOUNT_MINOR_ARG)?.takeIf { it > 0L }
        val feeAmountMinor = savedStateHandle.get<Long>(PRESET_FEE_AMOUNT_MINOR_ARG)?.takeIf { it > 0L }
        val dateTimeMillis = savedStateHandle.get<Long>(PRESET_DATE_TIME_MILLIS_ARG)?.takeIf { it > 0L }
        val description = savedStateHandle.get<String>(PRESET_DESCRIPTION_ARG)?.takeIf { it.isNotBlank() }
        val categoryId = savedStateHandle.get<Long>(PRESET_CATEGORY_ID_ARG)?.takeIf { it > 0L }
        val receiptId = savedStateHandle.get<Long>(PRESET_RECEIPT_ID_ARG)?.takeIf { it > 0L }
        val type = savedStateHandle.get<String>(PRESET_TYPE_ARG)
            ?.let { raw -> TransactionType.entries.find { it.name == raw } }

        // Ouverture normale du formulaire (bouton "+" habituel) : tous les arguments valent leur
        // défaut, rien à faire plutôt qu'un `update` sans effet.
        if (amountMinor == null && feeAmountMinor == null && dateTimeMillis == null && description == null &&
            categoryId == null && receiptId == null && type == null
        ) {
            return
        }

        _formState.update { state ->
            state.copy(
                amountInput = amountMinor?.let { Money.formatMajorUnits(it) } ?: state.amountInput,
                type = type ?: state.type,
                categoryId = categoryId ?: state.categoryId,
                dateTimeMillis = dateTimeMillis ?: state.dateTimeMillis,
                // Traitée comme une saisie CONFIRMÉE (voir isDescriptionAutoFilled = false), pas
                // une auto-génération : ne doit jamais être silencieusement écrasée/effacée par
                // autoFillTransferDescription (sans objet ici — un reçu ne produit jamais
                // TRANSFER, voir ReceiptTransactionInfoParser — mais correct par principe).
                description = description ?: state.description,
                isDescriptionAutoFilled = if (description != null) false else state.isDescriptionAutoFilled,
                hasFee = feeAmountMinor != null,
                feeAmountInput = feeAmountMinor?.let { Money.formatMajorUnits(it) } ?: state.feeAmountInput,
                // "Par défaut le compte source" (même règle que onHasFeeToggle), lu sur l'état
                // COURANT : presetAccountId (voir ci-dessus) a déjà été appliqué au moment où ce
                // `update` s'exécute (deux appels synchrones dans le même bloc init).
                feeAccountId = if (feeAmountMinor != null && state.feeAccountId == 0L) state.accountId else state.feeAccountId,
                receiptId = receiptId ?: state.receiptId
            )
        }
    }

    fun onAmountChange(value: String) {
        _formState.update { it.copy(amountInput = value, amountError = null) }
    }

    /**
     * Raccourci rapide (voir maquette "PERSONNALISATION – AJOUT DE
     * TRANSACTION", +1000/+5000/+10000) : AJOUTE [majorUnits] au montant déjà
     * saisi plutôt que de le remplacer, pour permettre de cumuler plusieurs
     * raccourcis (ex. +5000 puis +1000 pour 6000). Un montant vide ou invalide
     * est traité comme 0.
     */
    fun onQuickAmountAdd(majorUnits: Long) {
        _formState.update { state ->
            val currentMinor = Money.parseToMinorUnits(state.amountInput) ?: 0L
            val newMinor = currentMinor + majorUnits * 100
            state.copy(amountInput = Money.formatMajorUnits(newMinor), amountError = null)
        }
    }

    fun onTypeChange(type: TransactionType) {
        // La catégorie précédente peut ne plus correspondre au nouveau type ; sans objet
        // pour un transfert (voir TransactionType.TRANSFER), remise à 0L dans tous les cas.
        // Le compte destination, lui, n'a de sens QUE pour un transfert : on le remet à
        // 0L en le quittant (pour ne pas resservir un choix obsolète si l'utilisateur
        // revient sur "Transfert" plus tard), mais on le CONSERVE en y restant.
        _formState.update { state ->
            val leavingTransfer = state.type == TransactionType.TRANSFER && type != TransactionType.TRANSFER
            // La description "Compte A → Compte B" n'a plus de sens hors transfert : on l'efface
            // UNIQUEMENT si elle était auto-générée (une description tapée à la main est conservée).
            val clearingAutoDescription = leavingTransfer && state.isDescriptionAutoFilled
            autoFillTransferDescription(
                state.copy(
                    type = type,
                    categoryId = 0L,
                    categoryError = null,
                    transferAccountId = if (type == TransactionType.TRANSFER) state.transferAccountId else 0L,
                    transferAccountError = null,
                    description = if (clearingAutoDescription) "" else state.description,
                    isDescriptionAutoFilled = if (clearingAutoDescription) false else state.isDescriptionAutoFilled
                )
            )
        }
    }

    fun onAccountChange(accountId: Long) {
        _formState.update { state ->
            autoFillTransferDescription(
                state.copy(
                    accountId = accountId,
                    accountError = null,
                    // Le compte des frais suit le compte source tant qu'il n'a pas été choisi
                    // explicitement (voir la doc de TransactionFormState.feeAccountId) — "par
                    // défaut le compte source", cahier des charges section "Compte utilisé pour
                    // les frais".
                    feeAccountId = if (state.isFeeAccountAutoFilled) accountId else state.feeAccountId
                )
            )
        }
    }

    fun onTransferAccountChange(accountId: Long) {
        _formState.update { autoFillTransferDescription(it.copy(transferAccountId = accountId, transferAccountError = null)) }
    }

    fun onCategoryChange(categoryId: Long) {
        _formState.update { it.copy(categoryId = categoryId, categoryError = null) }
    }

    /**
     * Active/désactive la section "Frais supplémentaires" (voir [TransactionFormState.hasFee]).
     * À l'activation, préremplit [TransactionFormState.feeAccountId] avec le compte source
     * actuel s'il n'a encore jamais été choisi (première activation) — voir la doc de
     * [TransactionFormState.feeAccountId].
     */
    fun onHasFeeToggle(enabled: Boolean) {
        _formState.update { state ->
            state.copy(
                hasFee = enabled,
                feeAccountId = if (enabled && state.feeAccountId == 0L) state.accountId else state.feeAccountId,
                feeAmountError = null,
                feeAccountError = null
            )
        }
    }

    fun onFeeAmountChange(value: String) {
        _formState.update { it.copy(feeAmountInput = value, feeAmountError = null) }
    }

    fun onFeeTypeChange(type: FeeType) {
        _formState.update { it.copy(feeType = type) }
    }

    /** Choix EXPLICITE d'un compte pour les frais (sélecteur de compte du formulaire) : arrête
     * définitivement la synchronisation automatique avec le compte source (voir
     * [onAccountChange]), même si l'utilisateur choisit ensuite exactement le même compte. */
    fun onFeeAccountChange(accountId: Long) {
        _formState.update { it.copy(feeAccountId = accountId, isFeeAccountAutoFilled = false, feeAccountError = null) }
    }

    fun onFeeDescriptionChange(value: String) {
        _formState.update { it.copy(feeDescriptionInput = value) }
    }

    fun onDescriptionChange(value: String) {
        _formState.update { state ->
            // Si la nouvelle valeur correspond exactement à la description qu'on aurait générée,
            // on la considère toujours "auto" (évite qu'une re-synchronisation UI→ViewModel du
            // texte auto-rempli — voir TransactionFormFragment.render — soit prise pour une
            // saisie manuelle et bloque les régénérations suivantes). Toute autre valeur = saisie
            // volontaire de l'utilisateur, qui ne sera plus jamais écrasée automatiquement.
            val generated = transferDescription(state.accountId, state.transferAccountId)
            state.copy(description = value, isDescriptionAutoFilled = state.isDescriptionAutoFilled && value == generated)
        }
    }

    /** Nom des deux comptes séparés par une flèche (ex. "Espèces → Banque"), `null` tant que les
     * deux comptes d'un transfert ne sont pas encore choisis ou inconnus. */
    private fun transferDescription(sourceAccountId: Long, destinationAccountId: Long): String? {
        if (sourceAccountId == 0L || destinationAccountId == 0L) return null
        val accountsById = accounts.value.associateBy { it.id }
        val sourceName = accountsById[sourceAccountId]?.name ?: return null
        val destinationName = accountsById[destinationAccountId]?.name ?: return null
        return "$sourceName → $destinationName"
    }

    /**
     * Régénère [TransactionFormState.description] à partir des comptes source/destination quand
     * [TransactionFormState.type] est [TransactionType.TRANSFER] — tant que l'utilisateur n'a pas
     * tapé sa propre description (voir [TransactionFormState.isDescriptionAutoFilled]). Sans effet
     * hors transfert, ou si l'un des deux comptes n'est pas encore choisi.
     */
    private fun autoFillTransferDescription(state: TransactionFormState): TransactionFormState {
        if (state.type != TransactionType.TRANSFER) return state
        if (state.description.isNotBlank() && !state.isDescriptionAutoFilled) return state
        val generated = transferDescription(state.accountId, state.transferAccountId) ?: return state
        return state.copy(description = generated, isDescriptionAutoFilled = true)
    }

    fun onPaymentMethodChange(method: PaymentMethod?) {
        _formState.update { it.copy(paymentMethod = method) }
    }

    fun onDateChange(date: LocalDate) {
        _formState.update { state ->
            val currentTime = Instant.ofEpochMilli(state.dateTimeMillis).atZone(ZoneId.systemDefault()).toLocalTime()
            state.copy(dateTimeMillis = date.atTime(currentTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
    }

    fun onTimeChange(time: LocalTime) {
        _formState.update { state ->
            val currentDate = Instant.ofEpochMilli(state.dateTimeMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            state.copy(dateTimeMillis = currentDate.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
    }

    fun save() {
        val state = _formState.value

        if (state.linkedLoanId != null) {
            viewModelScope.launch { _events.emit(TransactionFormEvent.LoanLinked(state.linkedLoanId)) }
            return
        }

        val amountMinor = Money.parseToMinorUnits(state.amountInput)
        if (amountMinor == null || amountMinor <= 0L) {
            _formState.update { it.copy(amountError = "Montant invalide") }
            return
        }
        if (state.accountId == 0L) {
            _formState.update { it.copy(accountError = "Choisis un compte") }
            return
        }
        if (state.type == TransactionType.TRANSFER) {
            if (state.transferAccountId == 0L) {
                _formState.update { it.copy(transferAccountError = "Choisis un compte de destination") }
                return
            }
            if (state.transferAccountId == state.accountId) {
                _formState.update { it.copy(transferAccountError = "Le compte de destination doit être différent du compte source") }
                return
            }
        } else if (state.categoryId == 0L) {
            _formState.update { it.copy(categoryError = "Choisis une catégorie") }
            return
        }

        var feeAmountMinor: Long? = null
        if (state.hasFee) {
            feeAmountMinor = Money.parseToMinorUnits(state.feeAmountInput)
            if (feeAmountMinor == null || feeAmountMinor <= 0L) {
                _formState.update { it.copy(feeAmountError = "Montant des frais invalide") }
                return
            }
            if (state.feeAccountId == 0L) {
                _formState.update { it.copy(feeAccountError = "Choisis un compte pour les frais") }
                return
            }
        }

        val isTransfer = state.type == TransactionType.TRANSFER
        // `!!` sûr : feeAmountMinor n'est non-null QUE si state.hasFee (voir validation ci-dessus).
        val fee = if (state.hasFee) {
            TransactionFee(
                amount = feeAmountMinor!!,
                accountId = state.feeAccountId,
                type = state.feeType,
                description = state.feeDescriptionInput.trim()
            )
        } else {
            null
        }

        viewModelScope.launch {
            transactionRepository.saveTransaction(
                Transaction(
                    id = transactionId,
                    amount = amountMinor,
                    type = state.type,
                    accountId = state.accountId,
                    // Un transfert n'a pas de catégorie (voir TransactionType.TRANSFER) ;
                    // à l'inverse, transferAccountId n'a de sens QUE pour un transfert.
                    transferAccountId = if (isTransfer) state.transferAccountId else null,
                    categoryId = if (isTransfer) null else state.categoryId,
                    date = state.dateTimeMillis,
                    description = state.description.trim(),
                    paymentMethod = state.paymentMethod,
                    createdAt = state.createdAt ?: System.currentTimeMillis(),
                    receiptId = state.receiptId
                ),
                fee = fee
            )
            _events.emit(TransactionFormEvent.Saved)
        }
    }

    fun delete() {
        if (!isEditMode) return
        val linkedLoanId = _formState.value.linkedLoanId
        if (linkedLoanId != null) {
            viewModelScope.launch { _events.emit(TransactionFormEvent.LoanLinked(linkedLoanId)) }
            return
        }
        viewModelScope.launch {
            transactionRepository.deleteTransaction(transactionId)
            _events.emit(TransactionFormEvent.Deleted)
        }
    }

    private companion object {
        const val TRANSACTION_ID_ARG = "transactionId"
        const val PRESET_ACCOUNT_ID_ARG = "presetAccountId"
        const val PRESET_AMOUNT_MINOR_ARG = "presetAmountMinor"
        const val PRESET_FEE_AMOUNT_MINOR_ARG = "presetFeeAmountMinor"
        const val PRESET_DATE_TIME_MILLIS_ARG = "presetDateTimeMillis"
        const val PRESET_DESCRIPTION_ARG = "presetDescription"
        const val PRESET_CATEGORY_ID_ARG = "presetCategoryId"
        const val PRESET_RECEIPT_ID_ARG = "presetReceiptId"
        const val PRESET_TYPE_ARG = "presetType"
    }
}
