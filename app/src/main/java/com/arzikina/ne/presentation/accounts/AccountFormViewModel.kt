package com.arzikina.ne.presentation.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.AccountIcon
import com.arzikina.ne.domain.model.AccountType
import com.arzikina.ne.domain.repository.AccountRepository
import com.arzikina.ne.util.CardInputFormatter
import com.arzikina.ne.util.Constants
import com.arzikina.ne.util.Money
import com.arzikina.ne.util.external.ExternalAppInfo
import com.arzikina.ne.util.external.ExternalAppLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * [cardCvvInput], une fois validés, sont chiffrés via `CardCipher` puis
 * persistés séparément par [AccountRepository.saveCardSecrets] (voir [save]) —
 * ils ne transitent JAMAIS en clair au-delà de ce ViewModel.
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
    /**
     * Voir [Account.isExcludedFromStatistics] — `false` par défaut, y compris pour un NOUVEAU
     * compte (un compte compte dans les statistiques personnelles sauf choix explicite contraire).
     */
    val isExcludedFromStatistics: Boolean = false,
    /** Sans objet hors [AccountType.MOBILE_MONEY] (voir [AccountFormFragment], qui masque sa
     * section) — voir [Account.mobileMoneyPackageName]. */
    val mobileMoneyPackageNameInput: String = "",
    /**
     * Nom lisible de l'application correspondant à [mobileMoneyPackageNameInput], résolu EN
     * DIRECT via [ExternalAppLauncher] à chaque changement du champ (voir
     * [AccountFormViewModel.onMobileMoneyPackageNameChange]) — `null` si le package est vide,
     * invalide, ou ne correspond à aucune application installée sur CET appareil. Purement
     * informatif (aide affichée sous le champ, voir cahier des charges section 10 : "ne pas
     * afficher uniquement le package") : jamais persisté, jamais utilisé pour valider la saisie
     * (un package valide pour un futur appareil peut très bien être introuvable ici et
     * maintenant).
     */
    val mobileMoneyAppLabel: String? = null,
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

    /** Voir [AccountFormViewModel.onSelectMobileMoneyAppClicked] : [apps] est déjà résolue
     * (interrogation PackageManager faite hors du thread principal, voir [ExternalAppLauncher])
     * au moment où cet événement est émis — [AccountFormFragment] n'a plus qu'à l'afficher via
     * `ExternalAppPickerDialog`. */
    data class ShowAppPicker(val apps: List<ExternalAppInfo>) : AccountFormEvent
}

@HiltViewModel
class AccountFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val externalAppLauncher: ExternalAppLauncher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
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
                            existingCardLastFourDigits = account.cardLastFourDigits,
                            isExcludedFromStatistics = account.isExcludedFromStatistics,
                            mobileMoneyPackageNameInput = account.mobileMoneyPackageName.orEmpty()
                        )
                    }
                    account.mobileMoneyPackageName?.let { resolveMobileMoneyAppLabel(it) }
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

    fun onExcludedFromStatisticsChange(value: Boolean) {
        _formState.update { it.copy(isExcludedFromStatistics = value) }
    }

    /** Saisie manuelle du package (voir cahier des charges, section 1) — [resolveMobileMoneyAppLabel]
     * tient [AccountFormState.mobileMoneyAppLabel] à jour à chaque frappe, comme pour la
     * sélection via [onMobileMoneyAppSelected]. */
    fun onMobileMoneyPackageNameChange(value: String) {
        _formState.update { it.copy(mobileMoneyPackageNameInput = value, mobileMoneyAppLabel = null) }
        resolveMobileMoneyAppLabel(value)
    }

    /**
     * Ouvre `ExternalAppPickerDialog` (voir [AccountFormEvent.ShowAppPicker]) avec la liste des
     * applications détectées — [ExternalAppLauncher.listLaunchableApps] interroge
     * `PackageManager` pour potentiellement une centaine d'applications, d'où le passage par
     * [ioDispatcher] (voir section performances du projet : jamais de travail I/O-bound sur le
     * thread principal, même si `PackageManager` n'est techniquement pas un accès disque/réseau).
     */
    fun onSelectMobileMoneyAppClicked() {
        viewModelScope.launch {
            val apps = withContext(ioDispatcher) { externalAppLauncher.listLaunchableApps() }
            _events.emit(AccountFormEvent.ShowAppPicker(apps))
        }
    }

    /** Résultat du sélecteur : le nom lisible est déjà connu (voir [ExternalAppInfo.label]), pas
     * besoin de re-résoudre via [resolveMobileMoneyAppLabel]. */
    fun onMobileMoneyAppSelected(app: ExternalAppInfo) {
        _formState.update { it.copy(mobileMoneyPackageNameInput = app.packageName, mobileMoneyAppLabel = app.label) }
    }

    /** Voir [AccountFormState.mobileMoneyAppLabel] — `null` sans résoudre si [packageName] est
     * vide (évite un aller-retour PackageManager inutile pour le cas le plus fréquent, un champ
     * pas encore rempli). */
    private fun resolveMobileMoneyAppLabel(packageName: String) {
        val trimmed = packageName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val label = withContext(ioDispatcher) { externalAppLauncher.getAppInfo(trimmed)?.label }
            // Le champ a pu changer pendant la résolution (frappe rapide) : n'applique le
            // résultat que s'il correspond encore à la valeur ACTUELLE du champ.
            if (_formState.value.mobileMoneyPackageNameInput.trim() == trimmed) {
                _formState.update { it.copy(mobileMoneyAppLabel = label) }
            }
        }
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

        // Un NOUVEAU numéro a été saisi (donc pas simplement conservé, voir keepExistingCard
        // ci-dessus) uniquement si le type est carte ET que cardNumberInput n'est pas vide.
        val hasNewCardSecrets = state.type == AccountType.CREDIT_CARD && state.cardNumberInput.isNotEmpty()

        // Forcé à `null` pour tout type AUTRE que Mobile Money (voir cahier des charges, section 2 :
        // "si l'utilisateur change le type du compte de Mobile Money vers un autre type, gérer
        // proprement la valeur existante sans casser les données") — la saisie reste visible dans
        // ce formulaire tant qu'on ne quitte pas l'écran (voir onTypeChange, qui ne la touche pas),
        // mais rien d'orphelin n'est jamais persisté pour un compte qui n'est plus Mobile Money.
        val mobileMoneyPackageName = if (state.type == AccountType.MOBILE_MONEY) {
            state.mobileMoneyPackageNameInput.trim().ifEmpty { null }
        } else {
            null
        }

        viewModelScope.launch {
            val savedAccountId = accountRepository.saveAccount(
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
                    cardExpiryYear = cardExpiryYear,
                    isExcludedFromStatistics = state.isExcludedFromStatistics,
                    mobileMoneyPackageName = mobileMoneyPackageName
                )
            )
            if (hasNewCardSecrets) {
                // Chiffré via Android Keystore avant toute écriture (voir CardCipher) : le numéro
                // complet/CVV en clair ne transitent jamais au-delà de cet appel.
                accountRepository.saveCardSecrets(savedAccountId, state.cardNumberInput, state.cardCvvInput)
            }
            _events.emit(AccountFormEvent.Saved)
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
