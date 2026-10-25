package com.naniger.arzikina.domain.shop

import com.naniger.arzikina.domain.model.ReceiptDiscount

/**
 * Calcul PUR d'une vente (aucune dépendance Android ni base) : à partir de la demande et de
 * l'instantané des produits, produit le plan à écrire ou lève une [SaleException].
 *
 * Isolé du repository pour être testé sur JVM : toutes les règles métier de la validation
 * (stock, quantités, remise, plafonds) vivent ici, le repository ne fait qu'écrire le résultat.
 */
object SalePlanner {

    const val MAX_CUSTOMER_NAME = 80
    const val MAX_CUSTOMER_PHONE = 30

    fun plan(
        lines: List<SaleLineRequest>,
        discount: ReceiptDiscount,
        customerName: String?,
        customerPhone: String?,
        products: Map<Long, SaleProductSnapshot>
    ): SalePlan {
        if (lines.isEmpty()) throw SaleException.EmptyCart()

        val merged = mergeLines(lines)
        val drafts = merged.map { line ->
            val product = products[line.productId]
                ?.takeIf { !it.isArchived }
                ?: throw SaleException.ProductUnavailable(line.productId)
            StockRules.validate(StockMovementType.SALE, -line.quantityMilli, null, product.unit)
                ?.let { throw SaleException.InvalidQuantity(line.productId, it) }
            SaleLineDraft(product.id, product.salePriceMinor, line.quantityMilli)
        }

        val stock = products.mapValues { it.value.stockMilli }
        val insufficient = SaleCalculator.insufficientProducts(drafts, stock)
        if (insufficient.isNotEmpty()) throw SaleException.InsufficientStock(insufficient)

        val totals = SaleCalculator.totalsOrNull(drafts, discount) ?: throw SaleException.InvalidAmount()
        if (totals.totalMinor <= 0L) throw SaleException.ZeroTotal()

        val planned = drafts.map { draft ->
            val product = products.getValue(draft.productId)
            PlannedSaleLine(
                productId = product.id,
                productName = product.name,
                unitPriceMinor = draft.unitPriceMinor,
                unitCostMinor = product.purchasePriceMinor,
                quantityMilli = draft.quantityMilli,
                lineTotalMinor = SaleCalculator.lineTotalOrNull(draft) ?: throw SaleException.InvalidAmount(),
                newStockMilli = product.stockMilli - draft.quantityMilli
            )
        }
        return SalePlan(
            lines = planned,
            subtotalMinor = totals.subtotalMinor,
            discountMinor = totals.discountMinor,
            totalMinor = totals.totalMinor,
            customerName = cleanName(customerName),
            customerPhone = cleanPhone(customerPhone)
        )
    }

    /** Une seule ligne par produit : les quantités d'un même produit sont cumulées (ordre conservé). */
    fun mergeLines(lines: List<SaleLineRequest>): List<SaleLineRequest> {
        val byProduct = LinkedHashMap<Long, Long>()
        for (line in lines) {
            val previous = byProduct[line.productId] ?: 0L
            byProduct[line.productId] = try {
                Math.addExact(previous, line.quantityMilli)
            } catch (e: ArithmeticException) {
                throw SaleException.InvalidAmount()
            }
        }
        return byProduct.map { SaleLineRequest(it.key, it.value) }
    }

    fun cleanName(input: String?): String? =
        input?.trim()?.replace(Regex("\\s+"), " ")?.take(MAX_CUSTOMER_NAME)?.takeIf { it.isNotEmpty() }

    fun cleanPhone(input: String?): String? =
        input?.trim()?.filter { it.isDigit() || it in "+-() " }?.take(MAX_CUSTOMER_PHONE)?.takeIf { it.isNotEmpty() }
}
