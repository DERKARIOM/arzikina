package com.naniger.arzikina.presentation.utilities.webapp

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.di.DefaultDispatcher
import com.naniger.arzikina.domain.web.WebConsole
import com.naniger.arzikina.util.qr.QrCodeGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Prépare le QR code de la console web. Généré une seule fois hors du thread principal puis
 * conservé : une rotation d'écran ne le régénère pas.
 */
@HiltViewModel
class WebAppViewModel @Inject constructor(
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) : ViewModel() {

    val loginUrl: String = WebConsole.LOGIN_URL

    private val _qrCode = MutableStateFlow<Bitmap?>(null)
    val qrCode: StateFlow<Bitmap?> = _qrCode.asStateFlow()

    init {
        viewModelScope.launch {
            _qrCode.value = withContext(defaultDispatcher) {
                runCatching { QrCodeGenerator.generate(loginUrl, QR_SIZE_PX) }.getOrNull()
            }
        }
    }

    private companion object {
        /** Assez grand pour rester net sur un écran dense (affiché à ~240 dp). */
        const val QR_SIZE_PX = 768
    }
}
