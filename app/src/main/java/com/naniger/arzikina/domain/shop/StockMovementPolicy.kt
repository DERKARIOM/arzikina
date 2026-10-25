package com.naniger.arzikina.domain.shop

import com.naniger.arzikina.domain.issuedreceipt.ReceiptCalculator

/** Pourquoi un changement de stock est refusé. Les cinq premiers reprennent [StockMovementError]. */
enum class StockChangeError {
    ZERO_QUANTITY,
    QUANTITY_TOO_LARGE,
    WRONG_DIRECTION,
    REASON_REQUIRED,
    FRACTION_NOT_ALLOWED,

    /** La sortie dépasse le stock disponible : le stock ne devient jamais négatif. */
    INSUFFICIENT_STOCK,

    /** Le stock résultant dépasse le plafond de [StockRules.MAX_QUANTITY_MILLI]. */
    STOCK_OVERFLOW,

    /** Coût unitaire négatif, démesuré, ou fourni pour un type qui n'en porte pas. */
    COST_INVALID,

    /** Type réservé à un autre module (vente, annulation, stock initial) : non saisissable à la main. */
    TYPE_NOT_ALLOWED
}

/** Résultat de [StockMovementPolicy.plan] : le nouveau stock, ou la règle violée. */
sealed interface StockChangePlan {
    data class Accepted(val newStockMilli: Long) : StockChangePlan
    data class Rejected(val error: StockChangeError) : StockChangePlan
}

/**
 * Règles d'un mouvement de stock SAISI À LA MAIN, pures et testables (aucune dépendance Android ni base).
 * Complète [StockRules] (règles de forme du mouvement) par les règles qui dépendent du stock courant :
 * jamais de stock négatif, jamais de dépassement du plafond.
 */
object StockMovementPolicy {

    /** Types saisissables par l'utilisateur ; ventes et stock initial sont écrits par leurs propres flux. */
    val MANUAL_TYPES: List<StockMovementType> = listOf(
        StockMovementType.RESTOCK,
        StockMovementType.CUSTOMER_RETURN,
        StockMovementType.DAMAGED_OR_LOST,
        StockMovementType.ADJUSTMENT
    )

    fun plan(
        type: StockMovementType,
        deltaMilli: Long,
        reason: String?,
        unitCostMinor: Long?,
        unit: ProductUnit,
        currentStockMilli: Long
    ): StockChangePlan {
        if (type !in MANUAL_TYPES) return StockChangePlan.Rejected(StockChangeError.TYPE_NOT_ALLOWED)
        StockRules.validate(type, deltaMilli, reason, unit)?.let {
            return StockChangePlan.Rejected(StockChangeError.valueOf(it.name))
        }
        if (unitCostMinor != null &&
            (type != StockMovementType.RESTOCK || unitCostMinor < 0L || unitCostMinor > ReceiptCalculator.MAX_AMOUNT_MINOR)
        ) {
            return StockChangePlan.Rejected(StockChangeError.COST_INVALID)
        }
        val newStock = try {
            Math.addExact(currentStockMilli, deltaMilli)
        } catch (e: ArithmeticException) {
            return StockChangePlan.Rejected(StockChangeError.STOCK_OVERFLOW)
        }
        return when {
            newStock < 0L -> StockChangePlan.Rejected(StockChangeError.INSUFFICIENT_STOCK)
            newStock > StockRules.MAX_QUANTITY_MILLI -> StockChangePlan.Rejected(StockChangeError.STOCK_OVERFLOW)
            else -> StockChangePlan.Accepted(newStock)
        }
    }

    /**
     * Écart d'une correction d'inventaire : quantité comptée − stock courant. `null` si la quantité comptée
     * est invalide (négative, au-dessus du plafond, ou fractionnaire pour une unité comptée).
     */
    fun adjustmentDelta(countedMilli: Long, currentStockMilli: Long, unit: ProductUnit): Long? {
        if (countedMilli !in 0..StockRules.MAX_QUANTITY_MILLI) return null
        if (!unit.allowsFraction && countedMilli % StockRules.QUANTITY_UNIT_MILLI != 0L) return null
        return countedMilli - currentStockMilli
    }
}
