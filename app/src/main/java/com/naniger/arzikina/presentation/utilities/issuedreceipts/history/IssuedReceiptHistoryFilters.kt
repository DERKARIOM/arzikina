package com.naniger.arzikina.presentation.utilities.issuedreceipts.history

import com.naniger.arzikina.domain.model.IssuedReceiptFilter
import java.time.ZoneId

/**
 * Filtres saisis dans l'historique. [fromMillis]/[toMillis] : début du jour de départ / dernière
 * milliseconde du jour de fin, en heure locale (voir [HistoryDateBounds]).
 */
data class IssuedReceiptHistoryFilters(
    val query: String = "",
    val fromMillis: Long? = null,
    val toMillis: Long? = null
) {
    val isActive: Boolean get() = query.isNotBlank() || fromMillis != null || toMillis != null

    fun toDomain(): IssuedReceiptFilter = IssuedReceiptFilter(query.trim(), fromMillis, toMillis)

    /** Nouveau jour de départ ; si la fin est antérieure, elle est ramenée à la fin du même jour. */
    fun withFrom(localMillis: Long, zone: ZoneId): IssuedReceiptHistoryFilters {
        val from = HistoryDateBounds.startOfDay(localMillis, zone)
        val to = toMillis?.let { if (it < from) HistoryDateBounds.endOfDay(from, zone) else it }
        return copy(fromMillis = from, toMillis = to)
    }

    /** Nouveau jour de fin ; si le départ est postérieur, il est ramené au début du même jour. */
    fun withTo(localMillis: Long, zone: ZoneId): IssuedReceiptHistoryFilters {
        val to = HistoryDateBounds.endOfDay(localMillis, zone)
        val from = fromMillis?.let { if (it > to) HistoryDateBounds.startOfDay(localMillis, zone) else it }
        return copy(fromMillis = from, toMillis = to)
    }

    fun clearDates(): IssuedReceiptHistoryFilters = copy(fromMillis = null, toMillis = null)
}
