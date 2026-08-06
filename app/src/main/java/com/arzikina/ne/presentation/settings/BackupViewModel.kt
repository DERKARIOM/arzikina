package com.arzikina.ne.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arzikina.ne.domain.model.BackupResult
import com.arzikina.ne.domain.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    fun exportBackup(outputStream: OutputStream) {
        viewModelScope.launch {
            runCatching { backupRepository.exportBackup(outputStream) }
                .onSuccess { _events.emit(BackupEvent.ExportSuccess(it)) }
                .onFailure { _events.emit(BackupEvent.Error(it.message ?: "Erreur inconnue")) }
        }
    }

    fun importBackup(inputStream: InputStream) {
        viewModelScope.launch {
            runCatching { backupRepository.importBackup(inputStream) }
                .onSuccess { _events.emit(BackupEvent.ImportSuccess(it)) }
                .onFailure { _events.emit(BackupEvent.Error(it.message ?: "Erreur inconnue")) }
        }
    }
}
