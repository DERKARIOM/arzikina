package com.naniger.arzikina.data.mapper

import com.naniger.arzikina.data.local.dao.SaleSummaryRow
import com.naniger.arzikina.data.local.entity.SaleEntity
import com.naniger.arzikina.data.local.entity.SaleLineEntity
import com.naniger.arzikina.data.local.entity.SalePaymentEntity
import com.naniger.arzikina.domain.model.PaymentMethod
import com.naniger.arzikina.domain.shop.Sale
import com.naniger.arzikina.domain.shop.SaleLine
import com.naniger.arzikina.domain.shop.SaleStatus
import com.naniger.arzikina.domain.shop.SaleSummary

fun SaleLineEntity.toDomain() = SaleLine(
    id = id,
    productId = productId,
    productName = productName,
    unitPriceMinor = unitPriceMinor,
    unitCostMinor = unitCostMinor,
    quantityMilli = quantityMilli,
    lineTotalMinor = lineTotalMinor
)

/** Assemble la vente à partir de ses lignes et de son (unique, en V1) encaissement. */
fun SaleEntity.toDomain(lines: List<SaleLineEntity>, payments: List<SalePaymentEntity>): Sale {
    val payment = payments.firstOrNull()
    return Sale(
        id = id,
        reference = reference,
        soldAt = soldAt,
        currencyCode = currencyCode,
        customerName = customerName,
        customerPhone = customerPhone,
        subtotalMinor = subtotalMinor,
        discountMinor = discountMinor,
        totalMinor = totalMinor,
        status = SaleStatus.fromStored(status),
        accountId = payment?.accountId,
        paymentMethod = payment?.let { p -> paymentMethodOf(p.method) },
        cancelledAt = cancelledAt,
        cancelReason = cancelReason,
        issuedReceiptId = issuedReceiptId,
        lines = lines.map { it.toDomain() }
    )
}

private fun paymentMethodOf(stored: String?): PaymentMethod? = PaymentMethod.entries.firstOrNull { it.name == stored }

fun SaleSummaryRow.toDomain() = SaleSummary(
    id = id,
    reference = reference,
    soldAt = soldAt,
    totalMinor = totalMinor,
    currencyCode = currencyCode,
    status = SaleStatus.fromStored(status),
    customerName = customerName,
    accountId = accountId,
    paymentMethod = paymentMethodOf(method),
    hasReceipt = issuedReceiptId != null
)
