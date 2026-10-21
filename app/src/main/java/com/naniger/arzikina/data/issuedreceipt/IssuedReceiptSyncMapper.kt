package com.naniger.arzikina.data.issuedreceipt

import com.naniger.arzikina.data.local.entity.IssuedReceiptEntity
import com.naniger.arzikina.data.local.entity.IssuerProfileEntity
import com.naniger.arzikina.data.remote.dto.IssuedReceiptServerStateDto
import com.naniger.arzikina.data.remote.dto.IssuedReceiptSyncPayload
import com.naniger.arzikina.data.remote.dto.IssuerProfileServerStateDto
import com.naniger.arzikina.data.remote.dto.IssuerProfileSyncPayload
import com.naniger.arzikina.domain.model.SyncOperation
import java.util.UUID

/**
 * Conversions PURES (sans Room ni réseau) entre entités locales, charges utiles sortantes et états
 * serveur entrants — isolées du gestionnaire pour être testées sans Android.
 */
internal object IssuedReceiptSyncMapper {

    /**
     * `syncId` DÉTERMINISTE du profil : un seul profil par utilisateur côté serveur (index unique),
     * donc tous les appareils d'un même compte doivent viser la même ligne, sans coordination.
     */
    fun profileSyncId(serverUserId: String): String =
        UUID.nameUUIDFromBytes("arzikina:issuer_profile:$serverUserId".toByteArray(Charsets.UTF_8)).toString()

    /** `baseVersion` : `null` pour une création, sinon la version connue (détection de conflit serveur). */
    fun baseVersionFor(operation: SyncOperation, version: Int): Int? =
        if (operation == SyncOperation.CREATE) null else version

    fun toPayload(entity: IssuerProfileEntity, syncId: String, operation: SyncOperation): IssuerProfileSyncPayload =
        IssuerProfileSyncPayload(
            id = syncId,
            baseVersion = baseVersionFor(operation, entity.version),
            businessName = entity.businessName,
            address = entity.address,
            phone = entity.phone,
            email = entity.email,
            taxId = entity.taxId,
            logoFileName = entity.logoFileName,
            defaultIssuedByName = entity.defaultIssuedByName,
            defaultIssuedByTitle = entity.defaultIssuedByTitle,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )

    fun toPayload(entity: IssuedReceiptEntity, syncId: String, operation: SyncOperation): IssuedReceiptSyncPayload {
        val issuer = IssuedReceiptJson.decodeIssuer(entity.issuerJson)
        return IssuedReceiptSyncPayload(
            id = syncId,
            baseVersion = baseVersionFor(operation, entity.version),
            number = entity.number,
            issuedAt = entity.issuedAt,
            customerName = entity.customerName,
            customerPhone = entity.customerPhone,
            description = entity.description,
            amountMinor = entity.amountMinor,
            currencyCode = entity.currencyCode,
            paymentMethod = entity.paymentMethod,
            transactionReference = entity.transactionReference,
            notes = entity.notes,
            issuedByName = entity.issuedByName,
            issuedByTitle = entity.issuedByTitle,
            pricingJson = entity.pricingJson,
            issuerBusinessName = issuer.businessName,
            issuerAddress = issuer.address,
            issuerPhone = issuer.phone,
            issuerEmail = issuer.email,
            issuerTaxId = issuer.taxId,
            issuerLogoFileName = issuer.logoFileName,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    /** [local] : ligne existante (conserve `id` Room et cache PDF), `null` pour une création. */
    fun toEntity(state: IssuerProfileServerStateDto, userId: Long, local: IssuerProfileEntity?): IssuerProfileEntity =
        IssuerProfileEntity(
            id = local?.id ?: 0L,
            userId = userId,
            businessName = state.businessName,
            address = state.address,
            phone = state.phone,
            email = state.email,
            taxId = state.taxId,
            logoFileName = state.logoFileName,
            defaultIssuedByName = state.defaultIssuedByName,
            defaultIssuedByTitle = state.defaultIssuedByTitle,
            createdAt = state.createdAt,
            updatedAt = state.updatedAt,
            syncId = state.id,
            deletedAt = state.deletedAt,
            version = state.version
        )

    /**
     * [number] : numéro à stocker (celui du serveur, sauf renumérotation locale d'un doublon).
     * Le cache PDF est conservé tant que le numéro est inchangé ET que le contenu n'a pas bougé
     * (`updatedAt` identique) ; sinon il est invalidé pour être régénéré.
     */
    fun toEntity(
        state: IssuedReceiptServerStateDto,
        userId: Long,
        local: IssuedReceiptEntity?,
        number: String = state.number
    ): IssuedReceiptEntity {
        val issuerJson = IssuedReceiptJson.encodeIssuer(
            com.naniger.arzikina.domain.model.IssuerSnapshot(
                businessName = state.issuerBusinessName,
                address = state.issuerAddress,
                phone = state.issuerPhone,
                email = state.issuerEmail,
                taxId = state.issuerTaxId,
                logoFileName = state.issuerLogoFileName
            )
        )
        val keepPdf = local != null && local.number == number && local.updatedAt == state.updatedAt
        return IssuedReceiptEntity(
            id = local?.id ?: 0L,
            userId = userId,
            number = number,
            issuedAt = state.issuedAt,
            customerName = state.customerName,
            customerPhone = state.customerPhone,
            description = state.description,
            amountMinor = state.amountMinor,
            currencyCode = state.currencyCode,
            paymentMethod = state.paymentMethod,
            transactionReference = state.transactionReference,
            notes = state.notes,
            issuedByName = state.issuedByName,
            issuedByTitle = state.issuedByTitle,
            pricingJson = state.pricingJson,
            issuerJson = issuerJson,
            pdfFileName = if (keepPdf) local?.pdfFileName else null,
            createdAt = state.createdAt,
            updatedAt = state.updatedAt,
            syncId = state.id,
            deletedAt = state.deletedAt,
            version = state.version
        )
    }

    /** Noms de logo référencés par une charge utile de profil ou de reçu (JSON de la file d'envoi). */
    fun logoNames(profile: IssuerProfileSyncPayload): List<String> = listOfNotNull(profile.logoFileName)

    fun logoNames(receipt: IssuedReceiptSyncPayload): List<String> = listOfNotNull(receipt.issuerLogoFileName)
}

/** Règles pures du transfert des logos (testées sans réseau). */
internal object LogoSyncPolicy {

    /**
     * `true` si une réponse HTTP d'erreur est DÉFINITIVE pour ce fichier (réessayer ne changera rien :
     * type refusé, trop gros, quota atteint…). 401/403 (jeton expiré), 408, 429 et les 5xx sont
     * temporaires : le lot reste à retenter.
     */
    fun isPermanentFailure(httpCode: Int): Boolean =
        httpCode in 400..499 && httpCode !in TRANSIENT_CLIENT_CODES

    private val TRANSIENT_CLIENT_CODES = setOf(401, 403, 408, 429)
}
