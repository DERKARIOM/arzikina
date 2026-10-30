package com.naniger.arzikina.presentation.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.domain.currency.CurrencyCatalog
import com.naniger.arzikina.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Devise principale de l'utilisateur, observable par tout écran qui affiche des montants SANS devise propre
 * (ex. planifications financières, dont les montants ne sont rattachés à aucun compte). À obtenir via
 * `activityViewModels()` : une seule instance pour toute l'activité, donc déjà « chaude » quand un écran s'ouvre.
 * Changer la devise ne convertit rien : seul l'affichage suit.
 */
@HiltViewModel
class PreferredCurrencyViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val currencyCode: StateFlow<String> = userPreferencesRepository.observePreferences()
        .map { it.currencyCode }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, CurrencyCatalog.DEFAULT_CODE)
}
