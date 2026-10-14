package com.naniger.arzikina.data.versioning

import com.naniger.arzikina.domain.versioning.UpgradeRequiredSignal
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** `extraBufferCapacity = 1` + `DROP_OLDEST` : `tryEmit` ne bloque jamais le thread réseau et
 *  plusieurs 426 rapprochés se fondent en un seul signal. */
@Singleton
internal class UpgradeRequiredSignalImpl @Inject constructor() : UpgradeRequiredSignal {

    private val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val events: Flow<Unit> = flow

    override fun notifyUpgradeRequired() {
        flow.tryEmit(Unit)
    }
}
