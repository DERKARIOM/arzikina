package com.arzikina.ne.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.BackupResult
import com.arzikina.ne.domain.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/** Événement ponctuel (affiché une seule fois, ex. Snackbar) suite à un export/import. */
sealed interface BackupEvent {
    data class ExportSuccess(val result: BackupResult) : BackupEvent
    data class ImportSuccess(val result: BackupResult) : BackupEvent
    data class Error(val message: String) : BackupEvent
}

/**
 * [isExporting]/[isImporting] séparés (plutôt qu'un seul booléen) : permet à
 * [com.arzikina.ne.presentation.settings.BackupFragment] de savoir PRÉCISÉMENT quel bouton doit
 * afficher son indicateur de progression — un volume de données important (des milliers de
 * transactions) peut rendre l'export/import assez long pour que l'écran semble figé sans ce retour
 * visuel (voir cahier des charges).
 */
data class BackupUiState(
    val isExporting: Boolean = false,
    val isImporting: Boolean = false
) {
    /** Les deux boutons sont désactivés dès qu'une opération est en cours, quelle qu'elle soit :
     * export et import touchent la même base, les lancer en parallèle n'a pas de sens. */
    val isBusy: Boolean get() = isExporting || isImporting
}

/**
 * ViewModel dédié à la sauvegarde/restauration, séparé de [SettingsViewModel]
 * (préférences) : ce sont deux responsabilités indépendantes qui partagent
 * seulement le même écran.
 *
 * Les flux ([InputStream]/[OutputStream]) proviennent du Storage Access
 * Framework, résolus par [com.arzikina.ne.presentation.settings.SettingsScreen]
 * à partir de l'`Uri` choisie par l'utilisateur — ce ViewModel n'a aucune
 * dépendance Android.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _events = MutableSharedFlow<BackupEvent>()
    val events: SharedFlow<BackupEvent> = _events.asSharedFlow()

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun exportBackup(outputStream: OutputStream) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            runCatching { backupRepository.exportBackup(outputStream) }
                .onSuccess { _events.emit(BackupEvent.ExportSuccess(it)) }
                .onFailure { _events.emit(BackupEvent.Error(it.message ?: "Erreur inconnue")) }
            _uiState.update { it.copy(isExporting = false) }
        }
    }

    fun importBackup(inputStream: InputStream) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            runCatching { backupRepository.importBackup(inputStream) }
                .onSuccess { _events.emit(BackupEvent.ImportSuccess(it)) }
                .onFailure { _events.emit(BackupEvent.Error(it.message ?: "Erreur inconnue")) }
            _uiState.update { it.copy(isImporting = false) }
        }
    }
}
