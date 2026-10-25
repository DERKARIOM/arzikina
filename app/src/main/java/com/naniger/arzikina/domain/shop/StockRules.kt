package com.naniger.arzikina.domain.shop

/** Niveau d'un stock par rapport au seuil minimal souhaité du produit. */
enum class StockLevel { OUT_OF_STOCK, LOW, NORMAL }

/** Raison pour laquelle un mouvement de stock est refusé (voir [StockRules.validate]). */
enum class StockMovementError {
    ZERO_QUANTITY,
    QUANTITY_TOO_LARGE,
    WRONG_DIRECTION,
    REASON_REQUIRED,
    FRACTION_NOT_ALLOWED
}

/**
 * Règles de stock, PURES (aucune dépendance Android ni base) : testables sur JVM, appelées par les
 * repositories avant toute écriture.
 *
 * Principe : le stock n'est jamais écrasé. Chaque changement est un mouvement signé et immuable ;
 * la quantité disponible en est la somme. Raison d'être : la synchronisation garde la dernière
 * écriture d'une LIGNE ; une quantité éditée sur deux appareils hors ligne perdrait des
 * mouvements, alors que des mouvements ajoutés (identifiants uniques) se cumulent sans conflit.
 *
 * Quantités en milli-unités (1 unité = 1000), même convention que `ReceiptLine.quantityMilli`.
 */
object StockRules {

    const val QUANTITY_UNIT_MILLI: Long = 1_000L

    /** Plafond d'une quantité de mouvement : 1 milliard d'unités. Évite tout débordement de [Long]. */
    const val MAX_QUANTITY_MILLI: Long = 1_000_000_000L * QUANTITY_UNIT_MILLI

    /**
     * `null` si le mouvement est acceptable, sinon la première règle violée. [deltaMilli] est SIGNÉ
     * (positif = entrée, négatif = sortie).
     */
    fun validate(
        type: StockMovementType,
        deltaMilli: Long,
        reason: String?,
        unit: ProductUnit
    ): StockMovementError? {
        if (deltaMilli == 0L) return StockMovementError.ZERO_QUANTITY
        if (deltaMilli == Long.MIN_VALUE || kotlin.math.abs(deltaMilli) > MAX_QUANTITY_MILLI) {
            return StockMovementError.QUANTITY_TOO_LARGE
        }
        val directionOk = when (type.direction) {
            StockMovementType.Direction.IN -> deltaMilli > 0
            StockMovementType.Direction.OUT -> deltaMilli < 0
            StockMovementType.Direction.ANY -> true
        }
        if (!directionOk) return StockMovementError.WRONG_DIRECTION
        if (type.requiresReason && reason.isNullOrBlank()) return StockMovementError.REASON_REQUIRED
        if (!unit.allowsFraction && deltaMilli % QUANTITY_UNIT_MILLI != 0L) {
            return StockMovementError.FRACTION_NOT_ALLOWED
        }
        return null
    }

    /** Stock = somme des mouvements. Lève [ArithmeticException] en cas de débordement (jamais silencieux). */
    fun stockOf(deltasMilli: Iterable<Long>): Long =
        deltasMilli.fold(0L) { acc, delta -> Math.addExact(acc, delta) }

    /**
     * [OUT_OF_STOCK][StockLevel.OUT_OF_STOCK] si le stock est nul ou négatif ; [LOW][StockLevel.LOW]
     * si un seuil est défini (> 0) et que le stock l'atteint ou passe dessous ; sinon normal.
     */
    fun levelOf(stockMilli: Long, minStockMilli: Long): StockLevel = when {
        stockMilli <= 0L -> StockLevel.OUT_OF_STOCK
        minStockMilli > 0L && stockMilli <= minStockMilli -> StockLevel.LOW
        else -> StockLevel.NORMAL
    }

    /** Une vente de [quantityMilli] est possible : quantité positive et stock suffisant (pas de vente à découvert). */
    fun canSell(stockMilli: Long, quantityMilli: Long): Boolean =
        quantityMilli > 0L && quantityMilli <= MAX_QUANTITY_MILLI && stockMilli >= quantityMilli
}
