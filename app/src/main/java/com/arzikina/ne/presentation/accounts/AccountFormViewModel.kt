package com.arzikina.ne.presentation.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.model.AccountType
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.util.CardInputFormatter
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.Money
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

/**
 * État du formulaire d'ajout/édition de compte.
 *
 * `Account.id == 0L` fait office de sentinelle "nouveau compte" (même
 * convention que [Account.id] et [AccountRepository.saveAccount]).
 *
 * Champs `card*Input` : sans objet hors [AccountType.CREDIT_CARD] (voir
 * [AccountFormFragment], qui masque leur section). [cardNumberInput] et
 * [cardCvvInput] ne sont JAMAIS écrits en base (voir [AccountType.CREDIT_CARD]) :
 * ils n'existent que le temps de la validation dans [save].
 */
data class AccountFormState(
    val name: String = "",
    val icon: AccountIcon = AccountIcon.CASH,
    val colorArgb: Long = 0xFF10B981L,
    val currencyCode: String = Constants.DEFAULT_CURRENCY_CODE,
    val initialBalanceInput: String = "0",
    val createdAt: Long? = null,
    val type: AccountType = AccountType.CASH,
    val cardNumberInput: String = "",
    val cardExpiryInput: String = "",
    val cardCvvInput: String = "",
    /**
     * Snapshot de la carte déjà enregistrée (mode édition uniquement, `null` sinon). Permet de
     * laisser [cardNumberInput]/[cardCvvInput] vides pour "ne pas changer la carte" au lieu
     * d'obliger à ressaisir le numéro complet et le CVV pour la moindre modification (ex. changer
     * juste la couleur) — voir [save], puisqu'ils ne sont jamais conservés en base.
     */
    val existingCardLastFourDigits: String? = null,
    val nameError: String? = null,
    val balanceError: String? = null,
    val cardNumberError: String? = null,
    val cardExpiryError: String? = null,
    val cardCvvError: String? = null
) {
    /**
     * `toString()` explicite qui REDACTE [cardNumberInput]/[cardCvvInput] (voir section
     * sécurité : "ne jamais afficher le CVV/numéro dans les logs"). Rien ne journalise cet état
     * aujourd'hui, mais le `toString()` généré par défaut d'une `data class` les inclurait tels
     * quels dès qu'un futur `Log.d(state.toString())` ou un rapport de plantage les capturerait
     * par accident — cette redaction rend cette fuite structurellement impossible plutôt que de
     * compter sur la vigilance de chaque futur appel de log.
     */
    override fun toString(): String = "AccountFormState(name=$name, type=$type, " +
        "cardNumberInput=${cardNumberInput.redactDigits()}, cardExpiryInput=$cardExpiryInput, " +
        "cardCvvInput=${cardCvvInput.redactDigits()}, existingCardLastFourDigits=$existingCardLastFourDigits, " +
        "hasErrors=${listOfNotNull(nameError, balanceError, cardNumberError, cardExpiryError, cardCvvError).isNotEmpty()})"

    private fun String.redactDigits(): String = if (isEmpty()) "" else "*".repeat(length)
}

sealed interface AccountFormEvent {
    data object Saved : AccountFormEvent
    data object Deleted : AccountFormEvent
}

@HiltViewModel
class AccountFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val accountId: Long = savedStateHandle.get<Long>(ACCOUNT_ID_ARG) ?: 0L
    val isEditMode: Boolean = accountId != 0L

    private val _formState = MutableStateFlow(AccountFormState())
    val formState: StateFlow<AccountFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<AccountFormEvent>()
    val events: SharedFlow<AccountFormEvent> = _events.asSharedFlow()

    init {
        if (isEditMode) {
            viewModelScope.launch {
                accountRepository.getAccount(accountId)?.let { account ->
                    _formState.update {
                        it.copy(
                            name = account.name,
                            icon = account.icon,
                            colorArgb = account.colorArgb,
                            currencyCode = account.currencyCode,
                            initialBalanceInput = Money.formatMajorUnits(account.initialBalance),
                            createdAt = account.createdAt,
                            type = account.type,
                            // Expiration éditable normalement (elle EST conservée, contrairement
                            // au numéro complet/CVV) : pré-remplie au format "MM/AA" de formatExpiry.
                            cardExpiryInput = if (account.cardExpiryMonth != null && account.cardExpiryYear != null) {
                                "%02d/%02d".format(account.cardExpiryMonth, account.cardExpiryYear % 100)
                            } else {
                                ""
                            },
                            existingCardLastFourDigits = account.cardLastFourDigits
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _formState.update { it.copy(name = value, nameError = null) }
    }

    fun onIconChange(icon: AccountIcon) {
        _formState.update { it.copy(icon = icon) }
    }

    fun onColorChange(colorArgb: Long) {
        _formState.update { it.copy(colorArgb = colorArgb) }
    }

    fun onCurrencyChange(code: String) {
        _formState.update { it.copy(currencyCode = code) }
    }

    fun onInitialBalanceChange(value: String) {
        _formState.update { it.copy(initialBalanceInput = value, balanceError = null) }
    }

    fun onTypeChange(type: AccountType) {
        _formState.update { state ->
            state.copy(
                type = type,
                // Icône par défaut cohérente avec le type choisi (voir AccountIcon.CREDIT_CARD) ;
                // l'utilisateur reste libre d'en choisir une autre ensuite via le sélecteur existant.
                icon = if (type == AccountType.CREDIT_CARD) AccountIcon.CREDIT_CARD else state.icon,
                cardNumberError = null,
                cardExpiryError = null,
                cardCvvError = null
            )
        }
    }

    fun onCardNumberChange(value: String) {
        _formState.update { it.copy(cardNumberInput = CardInputFormatter.cardNumberDigits(value), cardNumberError = null) }
    }

    fun onCardExpiryChange(value: String) {
        _formState.update { it.copy(cardExpiryInput = CardInputFormatter.formatExpiry(value), cardExpiryError = null) }
    }

    fun onCardCvvChange(value: String) {
        _formState.update { it.copy(cardCvvInput = CardInputFormatter.cvvDigits(value), cardCvvError = null) }
    }

    fun save() {
        val state = _formState.value
        val trimmedName = state.name.trim()
        if (trimmedName.isEmpty()) {
            _formState.update { it.copy(nameError = "Le nom est obligatoire") }
            return
        }

        val balanceMinor = Money.parseToMinorUnits(state.initialBalanceInput)
        if (balanceMinor == null) {
            _formState.update { it.copy(balanceError = "Montant invalide") }
            return
        }

        var cardLastFourDigits: String? = null
        var cardExpiryMonth: Int? = null
        var cardExpiryYear: Int? = null

        if (state.type == AccountType.CREDIT_CARD) {
            // Champ numéro vide EN ÉDITION d'une carte déjà enregistrée : on garde le numéro
            // actuel (ses 4 derniers chiffres, seuls conservés) plutôt que d'exiger une ressaisie
            // complète pour la moindre modification (ex. juste la couleur) — voir la doc de
            // [AccountFormState.existingCardLastFourDigits]. CVV sans objet dans ce cas : jamais
            // stocké de toute façon, il ne sert qu'à valider la saisie d'un NOUVEAU numéro.
            val keepExistingCard = state.cardNumberInput.isEmpty() && state.existingCardLastFourDigits != null
            if (keepExistingCard) {
                cardLastFourDigits = state.existingCardLastFourDigits
            } else {
                if (!CardInputFormatter.isValidCardNumber(state.cardNumberInput)) {
                    _formState.update { it.copy(cardNumberError = "Numéro de carte invalide") }
                    return
                }
                if (!CardInputFormatter.isValidCvv(state.cardCvvInput)) {
                    _formState.update { it.copy(cardCvvError = "Code de sécurité invalide") }
                    return
                }
                cardLastFourDigits = state.cardNumberInput.takeLast(4)
            }

            val expiryDigits = state.cardExpiryInput.filter { it.isDigit() }
            val now = YearMonth.now()
            if (!CardInputFormatter.isValidExpiry(expiryDigits, now.year, now.monthValue)) {
                _formState.update { it.copy(cardExpiryError = "Date d'expiration invalide") }
                return
            }
            cardExpiryMonth = expiryDigits.substring(0, 2).toInt()
            cardExpiryYear = 2000 + expiryDigits.substring(2).toInt()
        }

        viewModelScope.launch {
            accountRepository.saveAccount(
                Account(
                    id = accountId,
                    name = trimmedName,
                    icon = state.icon,
                    colorArgb = state.colorArgb,
                    currencyCode = state.currencyCode,
                    initialBalance = balanceMinor,
                    createdAt = state.createdAt ?: System.currentTimeMillis(),
                    type = state.type,
                    cardLastFourDigits = cardLastFourDigits,
                    cardExpiryMonth = cardExpiryMonth,
                    cardExpiryYear = cardExpiryYear
                )
            )
            _events.emit(AccountFormEvent.Saved)
            // state.cardNumberInput/cardCvvInput ne sont jamais lus au-delà de ce point : ils
            // s'effacent avec le ViewModel (fermeture de l'écran) sans avoir jamais été stockés.
        }
    }

    fun delete() {
        if (!isEditMode) return
        viewModelScope.launch {
            accountRepository.deleteAccount(accountId)
            _events.emit(AccountFormEvent.Deleted)
        }
    }

    private companion object {
        const val ACCOUNT_ID_ARG = "accountId"
    }
}
