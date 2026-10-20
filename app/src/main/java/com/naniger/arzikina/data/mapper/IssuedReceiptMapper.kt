package com.naniger.arzikina.data.mapper

import com.naniger.arzikina.data.issuedreceipt.IssuedReceiptJson
import com.naniger.arzikina.data.local.dao.IssuedReceiptSummaryRow
import com.naniger.arzikina.data.local.entity.IssuedReceiptEntity
import com.naniger.arzikina.data.local.entity.IssuerProfileEntity
import com.naniger.arzikina.domain.model.IssuedReceipt
import com.naniger.arzikina.domain.model.IssuedReceiptSummary
import com.naniger.arzikina.domain.model.IssuerProfile
import com.naniger.arzikina.domain.model.PaymentMethod

fun IssuedReceiptEntity.toDomain(): IssuedReceipt = IssuedReceipt(
    id = id,
    number = number,
    issuedAt = issuedAt,
    customerName = customerName,
    customerPhone = customerPhone,
    description = description,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    // Valeur inconnue (client plus récent) : jamais d'exception à la lecture.
    paymentMethod = runCatching { PaymentMethod.valueOf(paymentMethod) }.getOrDefault(PaymentMethod.OTHER),
    transactionReference = transactionReference,
    notes = notes,
    issuedByName = issuedByName,
    issuedByTitle = issuedByTitle,
    pricing = IssuedReceiptJson.decodePricing(pricingJson),
    issuer = IssuedReceiptJson.decodeIssuer(issuerJson),
    pdfFileName = pdfFileName,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/** [userId] : fourni par le repository, jamais par l'appelant. `syncId`/`deletedAt`/`version` : à la charge du repository (voir `IssuedReceiptRepositoryImpl`). */
fun IssuedReceipt.toEntity(userId: Long): IssuedReceiptEntity = IssuedReceiptEntity(
    id = id,
    userId = userId,
    number = number,
    issuedAt = issuedAt,
    customerName = customerName,
    customerPhone = customerPhone,
    description = description,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    paymentMethod = paymentMethod.name,
    transactionReference = transactionReference,
    notes = notes,
    issuedByName = issuedByName,
    issuedByTitle = issuedByTitle,
    pricingJson = pricing?.let { IssuedReceiptJson.encodePricing(it) },
    issuerJson = IssuedReceiptJson.encodeIssuer(issuer),
    pdfFileName = pdfFileName,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun IssuedReceiptSummaryRow.toDomain(): IssuedReceiptSummary = IssuedReceiptSummary(
    id = id,
    number = number,
    customerName = customerName,
    issuedAt = issuedAt,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    hasPdf = pdfFileName != null
)

fun IssuerProfileEntity.toDomain(): IssuerProfile = IssuerProfile(
    businessName = businessName,
    address = address,
    phone = phone,
    email = email,
    taxId = taxId,
    logoFileName = logoFileName,
    defaultIssuedByName = defaultIssuedByName,
    defaultIssuedByTitle = defaultIssuedByTitle
)
