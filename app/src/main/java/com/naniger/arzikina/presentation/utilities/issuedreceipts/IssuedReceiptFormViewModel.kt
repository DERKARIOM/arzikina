package com.naniger.arzikina.presentation.utilities.issuedreceipts

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.issuedreceipt.GenerateIssuedReceiptPdfUseCase
import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptError
import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptException
import com.naniger.arzikina.domain.issuedreceipt.LogoChange
import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptValidator
import com.naniger.arzikina.domain.issuedreceipt.LogoImportException
import com.naniger.arzikina.domain.issuedreceipt.pdf.IssuedReceiptPdfWriter
import com.naniger.arzikina.domain.model.PaymentMethod
import com.naniger.arzikina.domain.repository.IssuedReceiptRepository
import com.naniger.arzikina.domain.repository.IssuerProfileRepository
import com.naniger.arzikina.domain.repository.UserPreferencesRepository
import com.naniger.arzikina.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * État du formulaire de reçu émis.
 *
 * Logo : [storedLogoFileName] est celui déjà mémorisé dans le profil ; [pendingLogoUri] une image
 * choisie mais pas encore enregistrée ; [logoRemoved] une suppression demandée. Rien n'est écrit
 * avant l'enregistrement.
 *
 * [savedReceiptId]/[savedNumber] : dès la première sauvegarde, le reçu EXISTE. Toute sauvegarde ou
 * génération suivante le met à jour (même id, même numéro) au lieu d'en créer un second : générer le
 * PDF plusieurs fois ne crée jamais de doublon.
 */
data class IssuedReceiptFormState(
    val input: IssuedReceiptFormInput = IssuedReceiptFormInput(),
    val storedLogoFileName: String? = null,
    val pendingLogoUri: String? = null,
    val logoRemoved: Boolean = false,
    val suggestedNumber: String = "",
    val showErrors: Boolean = false,
    val errors: Set<IssuedReceiptError> = emptySet(),
    val numberTaken: Boolean = false,
    val isSaving: Boolean = false,
    val loaded: Boolean = false,
    val savedReceiptId: Long? = null,
    val savedNumber: String? = null,
    val nextLineId: Long = 1L,
    val editing: Boolean = false
) {
    /** Le numéro n'est plus modifiable une fois le reçu enregistré (il a pu être remis au client). */
    val numberLocked: Boolean get() = savedNumber != null
}

sealed interface IssuedReceiptFormEvent {
    data class Message(@StringRes val messageRes: Int) : IssuedReceiptFormEvent

    /** Reçu enregistré ; [pdfFileName]/[pdfUri] renseignés si le PDF a été généré. */
    data class Saved(val number: String, val pdfFileName: String?, val pdfUri: String?) : IssuedReceiptFormEvent

    /** Le reçu est enregistré mais son PDF n'a pas pu être généré (les données saisies sont conservées). */
    data object PdfFailed : IssuedReceiptFormEvent
}

/**
 * ViewModel du formulaire « Nouveau reçu » : préremplit l'émetteur depuis le profil mémorisé,
 * valide, enregistre (profil puis reçu) et génère le PDF. Les règles vivent dans le domaine
 * ([IssuedReceiptDraftFactory], `IssuedReceiptValidator`) : ce ViewModel ne fait que de l'orchestration.
 */
@HiltViewModel
class IssuedReceiptFormViewModel @Inject constructor(
    private val receipts: IssuedReceiptRepository,
    private val profiles: IssuerProfileRepository,
    private val preferences: UserPreferencesRepository,
    private val generatePdf: GenerateIssuedReceiptPdfUseCase,
    private val pdfWriter: IssuedReceiptPdfWriter,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Id du reçu à MODIFIER (argument de navigation `receiptId`), 0 = nouveau reçu. */
    private val editId: Long = savedStateHandle.get<Long>("receiptId") ?: 0L

    private val _state = MutableStateFlow(IssuedReceiptFormState())
    val state: StateFlow<IssuedReceiptFormState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<IssuedReceiptFormEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<IssuedReceiptFormEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            if (editId > 0L && loadForEdit(editId)) return@launch
            val today = startOfToday()
            val profile = profiles.getProfile()
            val currency = preferences.observePreferences().first().currencyCode
            val suggested = receipts.suggestNextNumber(today)
            _state.update {
                it.copy(
                    input = IssuedReceiptFormInput(
                        businessName = profile?.businessName.orEmpty(),
                        address = profile?.address.orEmpty(),
                        phone = profile?.phone.orEmpty(),
                        email = profile?.email.orEmpty(),
                        taxId = profile?.taxId.orEmpty(),
                        issuedByName = profile?.defaultIssuedByName.orEmpty(),
                        issuedByTitle = profile?.defaultIssuedByTitle.orEmpty(),
                        issuedAt = today,
                        currencyCode = currency
                    ),
                    storedLogoFileName = profile?.logoFileName,
                    suggestedNumber = suggested,
                    loaded = true
                )
            }
        }
    }

    /**
     * Mode modification : le formulaire reprend le reçu tel qu'enregistré (émetteur figé inclus, le
     * profil mémorisé n'est ni lu ni modifié) et son numéro reste verrouillé.
     * @return faux si le reçu n'existe plus (le formulaire s'ouvre alors en création).
     */
    private suspend fun loadForEdit(id: Long): Boolean {
        val receipt = receipts.getReceipt(id) ?: return false
        val editable = IssuedReceiptDraftFactory.inputFrom(receipt)
        _state.update {
            it.copy(
                input = editable.input,
                storedLogoFileName = receipt.issuer.logoFileName,
                nextLineId = editable.nextLineId,
                savedReceiptId = receipt.id,
                savedNumber = receipt.number,
                editing = true,
                loaded = true
            )
        }
        return true
    }

    // --- Édition -----------------------------------------------------------------------------

    fun onBusinessNameChange(v: String) = edit { copy(businessName = v) }
    fun onAddressChange(v: String) = edit { copy(address = v) }
    fun onPhoneChange(v: String) = edit { copy(phone = v) }
    fun onEmailChange(v: String) = edit { copy(email = v) }
    fun onTaxIdChange(v: String) = edit { copy(taxId = v) }
    fun onIssuedByNameChange(v: String) = edit { copy(issuedByName = v) }
    fun onIssuedByTitleChange(v: String) = edit { copy(issuedByTitle = v) }
    fun onNumberChange(v: String) = edit { copy(numberInput = v) }.also { _state.update { s -> s.copy(numberTaken = false) } }
    fun onDateChange(millis: Long) = edit { copy(issuedAt = millis) }
    fun onCustomerNameChange(v: String) = edit { copy(customerName = v) }
    fun onCustomerPhoneChange(v: String) = edit { copy(customerPhone = v) }
    fun onDescriptionChange(v: String) = edit { copy(description = v) }
    fun onAmountChange(v: String) = edit { copy(amountInput = v) }
    fun onCurrencyChange(code: String) = edit { copy(currencyCode = code) }
    fun onPaymentMethodChange(m: PaymentMethod) = edit { copy(paymentMethod = m) }
    fun onReferenceChange(v: String) = edit { copy(reference = v) }
    fun onNotesChange(v: String) = edit { copy(notes = v) }

    // --- Détail (quantités, prix, remise, taxe) ----------------------------------------------

    /**
     * Active/désactive le détail. À l'activation, une première ligne est créée et reprend le montant
     * déjà saisi ; à la désactivation, le total calculé redevient le montant saisi : rien n'est perdu.
     */
    fun onDetailedToggle(enabled: Boolean) {
        _state.update { s ->
            if (s.input.detailed == enabled) return@update s
            if (enabled) {
                val withLine = if (s.input.lines.isEmpty()) {
                    s.copy(
                        input = s.input.copy(lines = listOf(LineInput(id = s.nextLineId, unitPriceInput = s.input.amountInput))),
                        nextLineId = s.nextLineId + 1
                    )
                } else {
                    s
                }
                withLine.copy(input = withLine.input.copy(detailed = true))
            } else {
                val total = IssuedReceiptDraftFactory.totalsOf(s.input)?.totalMinor ?: 0L
                s.copy(input = s.input.copy(detailed = false, amountInput = if (total > 0) Money.formatForInput(total) else s.input.amountInput))
            }
        }
        refreshErrors()
    }

    fun onAddLine() {
        _state.update { s ->
            if (s.input.lines.size >= IssuedReceiptValidator.MAX_LINES) return@update s
            s.copy(input = s.input.copy(lines = s.input.lines + LineInput(id = s.nextLineId)), nextLineId = s.nextLineId + 1)
        }
        refreshErrors()
    }

    fun onRemoveLine(id: Long) = edit { copy(lines = lines.filterNot { it.id == id }) }
    fun onLineLabelChange(id: Long, v: String) = editLine(id) { copy(label = v) }
    fun onLineQuantityChange(id: Long, v: String) = editLine(id) { copy(quantityInput = v) }
    fun onLinePriceChange(id: Long, v: String) = editLine(id) { copy(unitPriceInput = v) }
    fun onDiscountModeChange(mode: DiscountMode) = edit { copy(discountMode = mode) }
    fun onDiscountChange(v: String) = edit { copy(discountInput = v) }
    fun onTaxChange(v: String) = edit { copy(taxInput = v) }

    private fun editLine(id: Long, transform: LineInput.() -> LineInput) =
        edit { copy(lines = lines.map { if (it.id == id) it.transform() else it }) }

    /** Recalcule les erreurs affichées (après une modification de [IssuedReceiptFormState.nextLineId] ou du mode détaillé). */
    private fun refreshErrors() = edit { this }

    /** Suggestion du prochain numéro pour la date choisie (l'année peut changer). */
    fun refreshSuggestedNumber() {
        viewModelScope.launch {
            val suggested = receipts.suggestNextNumber(_state.value.input.issuedAt)
            _state.update { it.copy(suggestedNumber = suggested) }
        }
    }

    /** Image choisie comme logo : vérifiée tout de suite (type réel, taille), pas seulement à l'enregistrement. */
    fun onLogoPicked(uri: String) {
        viewModelScope.launch {
            try {
                profiles.validateLogo(uri)
                _state.update { it.copy(pendingLogoUri = uri, logoRemoved = false) }
            } catch (e: LogoImportException) {
                _events.tryEmit(IssuedReceiptFormEvent.Message(e.reason.messageRes()))
            }
        }
    }

    fun onLogoRemoved() {
        _state.update { it.copy(pendingLogoUri = null, logoRemoved = it.storedLogoFileName != null) }
    }

    // --- Enregistrement ----------------------------------------------------------------------

    /** [generatePdf] : génère aussi le PDF après l'enregistrement. [language] : langue du document (« fr »/« en »). */
    fun save(generatePdf: Boolean, language: String) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                doSave(generatePdf, language)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit(IssuedReceiptFormEvent.Message(R.string.issued_receipt_save_failed))
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private suspend fun doSave(generatePdfNow: Boolean, language: String) {
        val current = _state.value
        val draft = IssuedReceiptDraftFactory.validate(current.input.copy(numberInput = effectiveNumber(current)))
        if (draft is IssuedReceiptDraft.Invalid) {
            _state.update { it.copy(showErrors = true, errors = draft.errors) }
            _events.tryEmit(IssuedReceiptFormEvent.Message(R.string.issued_receipt_fix_errors))
            return
        }
        draft as IssuedReceiptDraft.Valid

        // 1) Profil (mémorisation de l'émetteur + logo). Inutile en modification : l'émetteur reste celui du reçu.
        val logoFileName: String?
        if (current.editing) {
            logoFileName = current.storedLogoFileName
        } else {
            val logoChange = when {
                current.pendingLogoUri != null -> LogoChange.Replace(current.pendingLogoUri)
                current.logoRemoved -> LogoChange.Remove
                else -> LogoChange.Keep
            }
            try {
                profiles.saveProfile(draft.profile, logoChange)
            } catch (e: LogoImportException) {
                _events.tryEmit(IssuedReceiptFormEvent.Message(e.reason.messageRes()))
                return
            } catch (e: IssuedReceiptException.Invalid) {
                _state.update { it.copy(showErrors = true, errors = e.errors) }
                return
            }
            logoFileName = profiles.getProfile()?.logoFileName
            _state.update { it.copy(storedLogoFileName = logoFileName, pendingLogoUri = null, logoRemoved = false) }
        }

        // 2) Reçu : création la première fois, mise à jour ensuite (jamais de doublon).
        val receipt = draft.receipt.copy(
            id = current.savedReceiptId ?: 0L,
            number = current.savedNumber ?: draft.receipt.number,
            issuer = draft.receipt.issuer.copy(logoFileName = logoFileName)
        )
        val id = try {
            receipts.saveReceipt(receipt)
        } catch (e: IssuedReceiptException.DuplicateNumber) {
            _state.update { it.copy(numberTaken = true, showErrors = true) }
            _events.tryEmit(IssuedReceiptFormEvent.Message(R.string.issued_receipt_error_number_taken))
            return
        } catch (e: IssuedReceiptException.Invalid) {
            _state.update { it.copy(showErrors = true, errors = e.errors) }
            return
        }
        val savedNumber = receipts.getReceipt(id)?.number ?: receipt.number
        _state.update {
            it.copy(
                savedReceiptId = id,
                savedNumber = savedNumber,
                input = it.input.copy(numberInput = savedNumber),
                errors = emptySet(),
                showErrors = false
            )
        }

        // 3) PDF (optionnel). Un échec n'annule pas le reçu, déjà enregistré.
        if (!generatePdfNow) {
            _events.tryEmit(IssuedReceiptFormEvent.Saved(savedNumber, null, null))
            return
        }
        try {
            val fileName = generatePdf(id, language)
            _events.tryEmit(IssuedReceiptFormEvent.Saved(savedNumber, fileName, pdfWriter.contentUriFor(fileName)))
        } catch (e: IssuedReceiptException.PdfFailed) {
            _events.tryEmit(IssuedReceiptFormEvent.PdfFailed)
        }
    }

    /** Fichier du logo mémorisé (pour l'aperçu), `null` s'il n'existe plus. */
    fun logoFile(fileName: String): java.io.File? = profiles.logoFile(fileName)

    // --- Utilitaires -------------------------------------------------------------------------

    /** Numéro saisi, sinon vide (= généré à l'enregistrement). Une fois enregistré, le numéro figé. */
    private fun effectiveNumber(state: IssuedReceiptFormState): String = state.savedNumber ?: state.input.numberInput

    private fun edit(transform: IssuedReceiptFormInput.() -> IssuedReceiptFormInput) {
        _state.update { s ->
            val input = s.input.transform()
            // Après une première tentative, les erreurs suivent la saisie : elles disparaissent dès que le champ est corrigé.
            val errors = if (s.showErrors) {
                (IssuedReceiptDraftFactory.validate(input.copy(numberInput = effectiveNumber(s))) as? IssuedReceiptDraft.Invalid)?.errors.orEmpty()
            } else {
                emptySet()
            }
            s.copy(input = input, errors = errors)
        }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @StringRes
    private fun LogoImportException.Reason.messageRes(): Int = when (this) {
        LogoImportException.Reason.UNSUPPORTED_TYPE -> R.string.issued_receipt_logo_error_type
        LogoImportException.Reason.TOO_LARGE -> R.string.issued_receipt_logo_error_size
        LogoImportException.Reason.UNREADABLE -> R.string.issued_receipt_logo_error_unreadable
    }
}
