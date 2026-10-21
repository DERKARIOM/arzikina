package com.naniger.arzikina.data.issuedreceipt

import com.naniger.arzikina.domain.model.IssuerSnapshot
import com.naniger.arzikina.domain.model.ReceiptDiscount
import com.naniger.arzikina.domain.model.ReceiptLine
import com.naniger.arzikina.domain.model.ReceiptPricing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Représentation JSON des deux blocs stockés en texte dans `issued_receipts` (voir
 * [com.naniger.arzikina.data.local.entity.IssuedReceiptEntity]) et, plus tard, transportés tels
 * quels par la synchronisation (mêmes noms de champs côté serveur et Web).
 *
 * Couche DATA uniquement : le domaine ne connaît pas kotlinx.serialization. `ignoreUnknownKeys`
 * et valeurs par défaut : un client plus ancien lit sans erreur un JSON écrit par un plus récent.
 */
internal object IssuedReceiptJson {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class LineJson(val label: String, val quantityMilli: Long, val unitPriceMinor: Long)

    /** [discountType] : `none` | `percent` | `fixed` ; [discountValue] : points de base ou montant mineur. */
    @Serializable
    private data class PricingJson(
        val lines: List<LineJson> = emptyList(),
        val discountType: String = DISCOUNT_NONE,
        val discountValue: Long = 0L,
        val taxBasisPoints: Int = 0
    )

    @Serializable
    private data class IssuerJson(
        val businessName: String = "",
        val address: String = "",
        val phone: String = "",
        val email: String? = null,
        val taxId: String? = null,
        val logoFileName: String? = null,
        val signatureFileName: String? = null
    )

    fun encodePricing(pricing: ReceiptPricing): String {
        val (type, value) = when (val d = pricing.discount) {
            ReceiptDiscount.None -> DISCOUNT_NONE to 0L
            is ReceiptDiscount.Percent -> DISCOUNT_PERCENT to d.basisPoints.toLong()
            is ReceiptDiscount.Fixed -> DISCOUNT_FIXED to d.amountMinor
        }
        return json.encodeToString(
            PricingJson.serializer(),
            PricingJson(
                lines = pricing.lines.map { LineJson(it.label, it.quantityMilli, it.unitPriceMinor) },
                discountType = type,
                discountValue = value,
                taxBasisPoints = pricing.taxBasisPoints
            )
        )
    }

    /** `null` si [raw] est absent ou illisible : un JSON corrompu ne doit jamais empêcher d'ouvrir le reçu. */
    fun decodePricing(raw: String?): ReceiptPricing? {
        if (raw == null) return null
        val parsed = runCatching { json.decodeFromString(PricingJson.serializer(), raw) }.getOrNull() ?: return null
        val discount = when (parsed.discountType) {
            DISCOUNT_PERCENT -> ReceiptDiscount.Percent(parsed.discountValue.toInt())
            DISCOUNT_FIXED -> ReceiptDiscount.Fixed(parsed.discountValue)
            else -> ReceiptDiscount.None
        }
        return ReceiptPricing(
            lines = parsed.lines.map { ReceiptLine(it.label, it.quantityMilli, it.unitPriceMinor) },
            discount = discount,
            taxBasisPoints = parsed.taxBasisPoints
        )
    }

    fun encodeIssuer(issuer: IssuerSnapshot): String = json.encodeToString(
        IssuerJson.serializer(),
        IssuerJson(issuer.businessName, issuer.address, issuer.phone, issuer.email, issuer.taxId, issuer.logoFileName, issuer.signatureFileName)
    )

    fun decodeIssuer(raw: String): IssuerSnapshot {
        val parsed = runCatching { json.decodeFromString(IssuerJson.serializer(), raw) }.getOrNull() ?: IssuerJson()
        return IssuerSnapshot(
            businessName = parsed.businessName,
            address = parsed.address,
            phone = parsed.phone,
            email = parsed.email,
            taxId = parsed.taxId,
            logoFileName = parsed.logoFileName,
            signatureFileName = parsed.signatureFileName
        )
    }

    private const val DISCOUNT_NONE = "none"
    private const val DISCOUNT_PERCENT = "percent"
    private const val DISCOUNT_FIXED = "fixed"
}
