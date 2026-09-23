package com.naniger.arzikina.presentation.profile.crop

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * État de l'écran « Recadrer la photo ». Le chargement et le recadrage eux-mêmes sont faits par
 * `CropImageView` (asynchrone, voir [ProfilePhotoCropFragment]) : ce ViewModel ne porte que l'état
 * affiché, pour qu'il survive à une rotation de l'écran.
 *
 * - [ProfilePhotoCropUiState.isBusy] : image en cours de chargement OU de recadrage — indicateur
 *   visible et bouton « Valider » désactivé (évite un double appui qui lancerait deux recadrages).
 */
data class ProfilePhotoCropUiState(
    val isLoadingImage: Boolean = true,
    val isCropping: Boolean = false
) {
    val isBusy: Boolean get() = isLoadingImage || isCropping
}

@HiltViewModel
class ProfilePhotoCropViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Photo à recadrer, déjà choisie (galerie) ou prise (appareil photo) par `ProfileFragment`. */
    val sourceUri: Uri = ProfilePhotoCropFragmentArgs.fromSavedStateHandle(savedStateHandle).sourceUri

    private val _uiState = MutableStateFlow(ProfilePhotoCropUiState())
    val uiState: StateFlow<ProfilePhotoCropUiState> = _uiState.asStateFlow()

    fun onImageLoaded() {
        _uiState.update { it.copy(isLoadingImage = false) }
    }

    /** @return `false` si un recadrage est déjà en cours ou si l'image n'est pas encore chargée :
     *  l'appelant ne doit alors rien lancer. */
    fun onCropRequested(): Boolean {
        if (_uiState.value.isBusy) return false
        _uiState.update { it.copy(isCropping = true) }
        return true
    }

    /** Recadrage terminé en échec : l'utilisateur peut réessayer depuis le même écran. */
    fun onCropFailed() {
        _uiState.update { it.copy(isCropping = false) }
    }
}
