package com.naniger.arzikina.presentation.shop.stock

import com.naniger.arzikina.domain.shop.ProductQuantityFormat
import com.naniger.arzikina.domain.shop.StockChangeError
import com.naniger.arzikina.domain.shop.StockMovementType
import com.naniger.arzikina.util.Money

/** Saisie brute du formulaire de mouvement. */
data class StockMovementFormInput(
    val type: StockMovementType,
    val quantity: String = "",
    val reason: String = "",
    val reference: String = "",
    val unitCost: String = "",
    val updatePurchasePrice: Boolean = false
)

/** Saisie interprétée : la quantité est POSITIVE ; son sens (+/−) vient du type (voir [StockMovementFormParser.signedDelta]). */
sealed interface StockMovementFormResult {
    data class Valid(
        val quantityMilli: Long,
        val reason: String?,
        val reference: String?,
        val unitCostMinor: Long?,
        val updatePurchasePrice: Boolean
    ) : StockMovementFormResult

    data class Invalid(val errors: Set<StockChangeError>) : StockMovementFormResult
}

/**
 * Lit la saisie d'un mouvement (logique pure, testable). Les règles métier (sens, motif, stock suffisant)
 * restent dans `StockMovementPolicy`, appliquée par le repository : ici, seuls les nombres illisibles.
 */
object StockMovementFormParser {

    private const val MAX_NUMBER_LENGTH = 15

    fun parse(input: StockMovementFormInput): StockMovementFormResult {
        val errors = mutableSetOf<StockChangeError>()
        val quantity = if (input.quantity.isBlank() || input.quantity.length > MAX_NUMBER_LENGTH) {
            null
        } else {
            ProductQuantityFormat.parse(input.quantity)
        }
        // Une correction peut viser 0 (rien en rayon) ; tout autre mouvement exige une quantité > 0.
        val zeroAllowed = input.type == StockMovementType.ADJUSTMENT
        if (quantity == null || (quantity == 0L && !zeroAllowed)) errors += StockChangeError.ZERO_QUANTITY

        var cost: Long? = null
        if (input.type == StockMovementType.RESTOCK && input.unitCost.isNotBlank()) {
            val significant = input.unitCost.filterNot { it.isWhitespace() || Character.isSpaceChar(it) }
            cost = if (significant.length > MAX_NUMBER_LENGTH) null else Money.parseToMinorUnits(significant)
            if (cost == null) errors += StockChangeError.COST_INVALID
        }
        val reason = input.reason.trim().takeIf { it.isNotEmpty() }
        if (input.type.requiresReason && reason == null) errors += StockChangeError.REASON_REQUIRED

        return if (errors.isNotEmpty() || quantity == null) {
            StockMovementFormResult.Invalid(errors)
        } else {
            StockMovementFormResult.Valid(
                quantityMilli = quantity,
                reason = reason,
                reference = input.reference.trim().takeIf { it.isNotEmpty() },
                unitCostMinor = cost,
                updatePurchasePrice = input.updatePurchasePrice && cost != null
            )
        }
    }

    /** Sens du mouvement : les sorties (perte/casse) sont négatives, les autres types saisis ici sont des entrées. */
    fun signedDelta(type: StockMovementType, quantityMilli: Long): Long =
        if (type == StockMovementType.DAMAGED_OR_LOST) -quantityMilli else quantityMilli
}
