package com.naniger.arzikina.domain.issuedreceipt.pdf

import com.naniger.arzikina.domain.issuedreceipt.ReceiptCalculator
import com.naniger.arzikina.domain.model.IssuedReceipt
import com.naniger.arzikina.domain.model.ReceiptDiscount
import java.util.Calendar

/** Transforme un [IssuedReceipt] en [ReceiptPdfContent] (textes et montants formatés). Logique pure. */
object ReceiptPdfContentBuilder {

    fun build(receipt: IssuedReceipt, language: String, logo: PdfLogo?): ReceiptPdfContent {
        val labels = ReceiptPdfLabels.forLanguage(language)
        val currency = receipt.currencyCode
        val totals = receipt.pricing?.let { ReceiptCalculator.totalsOrNull(it) }
        val lineTotals = receipt.pricing?.lines?.map { ReceiptCalculator.lineTotalOrNull(it) ?: 0L }.orEmpty()

        val amounts = buildList {
            add(receipt.amountMinor)
            receipt.pricing?.lines?.forEach { add(it.unitPriceMinor) }
            addAll(lineTotals)
            totals?.let { addAll(listOf(it.subtotalMinor, it.discountMinor, it.taxMinor)) }
        }
        val decimals = amounts.any { it % 100L != 0L }
        fun money(value: Long) = ReceiptAmountFormatter.format(value, currency, decimals)

        val table = if (receipt.pricing != null && totals != null) {
            val pricing = receipt.pricing
            val rows = pricing.lines.mapIndexed { index, line ->
                PdfTableRow(
                    label = line.label,
                    quantity = ReceiptAmountFormatter.quantity(line.quantityMilli),
                    unitPrice = money(line.unitPriceMinor),
                    lineTotal = money(lineTotals[index])
                )
            }
            val summary = buildList {
                if (totals.discountMinor > 0 || totals.taxMinor > 0) add(PdfKeyValue(labels.subtotal, money(totals.subtotalMinor)))
                if (totals.discountMinor > 0) {
                    val suffix = (pricing.discount as? ReceiptDiscount.Percent)?.let { " (${ReceiptAmountFormatter.percent(it.basisPoints)})" }.orEmpty()
                    add(PdfKeyValue(labels.discount + suffix, "-" + money(totals.discountMinor)))
                }
                if (pricing.taxBasisPoints > 0) {
                    add(PdfKeyValue("${labels.tax} (${ReceiptAmountFormatter.percent(pricing.taxBasisPoints)})", money(totals.taxMinor)))
                }
            }
            PdfTable(rows, summary)
        } else {
            null
        }

        val issuer = receipt.issuer
        val contact = buildList {
            add(issuer.address)
            add("${labels.phone} : ${issuer.phone}")
            issuer.email?.let { add("${labels.email} : $it") }
            issuer.taxId?.let { add("${labels.taxId} : $it") }
        }
        val details = buildList {
            add(PdfKeyValue(labels.paymentMethod, labels.methodName(receipt.paymentMethod)))
            receipt.transactionReference?.let { add(PdfKeyValue(labels.reference, it)) }
        }

        return ReceiptPdfContent(
            labels = labels,
            businessName = issuer.businessName,
            contactLines = contact,
            logo = logo,
            number = receipt.number,
            dateText = formatDate(receipt.issuedAt),
            customerName = receipt.customerName,
            customerPhone = receipt.customerPhone?.let { "${labels.phone} : $it" },
            description = receipt.description,
            table = table,
            totalText = money(receipt.amountMinor),
            details = details,
            notes = receipt.notes,
            issuedByName = receipt.issuedByName,
            issuedByTitle = receipt.issuedByTitle
        )
    }

    /** « 05/03/2026 », fuseau local (la date d'émission est un début de journée locale). */
    fun formatDate(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        val day = c.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        val month = (c.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
        return "$day/$month/${c.get(Calendar.YEAR)}"
    }
}
