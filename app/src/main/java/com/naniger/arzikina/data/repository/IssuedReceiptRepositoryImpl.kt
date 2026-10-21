package com.naniger.arzikina.data.repository

import androidx.room.withTransaction
import com.naniger.arzikina.data.local.dao.IssuedReceiptDao
import com.naniger.arzikina.data.local.database.ArzikinaDatabase
import com.naniger.arzikina.data.mapper.toDomain
import com.naniger.arzikina.data.mapper.toEntity
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptException
import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptValidator
import com.naniger.arzikina.domain.issuedreceipt.ReceiptCalculator
import com.naniger.arzikina.domain.issuedreceipt.ReceiptNumber
import com.naniger.arzikina.domain.model.IssuedReceipt
import com.naniger.arzikina.domain.model.IssuedReceiptFilter
import com.naniger.arzikina.domain.model.IssuedReceiptSummary
import com.naniger.arzikina.domain.model.SyncOperation
import com.naniger.arzikina.domain.repository.IssuedReceiptRepository
import com.naniger.arzikina.domain.repository.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

/**
 * Implémentation Room de [IssuedReceiptRepository]. Isolation multi-utilisateurs : voir
 * `AccountRepositoryImpl`.
 *
 * Synchronisation : chaque écriture (création, modification, suppression douce) est mise en file par
 * [IssuedReceiptSyncHandler] DANS la même transaction que l'écriture (jamais de ligne modifiée sans
 * opération en file, ni l'inverse). Les lignes antérieures à la synchronisation (`syncId` nul) sont
 * rattrapées par `SyncEngine.enqueueUnsyncedLocalData`.
 *
 * Les écritures passent par `database.withTransaction` : le calcul du prochain numéro et
 * l'insertion sont atomiques, deux enregistrements simultanés ne peuvent pas obtenir le même numéro.
 */
class IssuedReceiptRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val receiptDao: IssuedReceiptDao,
    private val sessionManager: SessionManager,
    private val syncHandler: IssuedReceiptSyncHandler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : IssuedReceiptRepository {

    override fun observeReceipts(filter: IssuedReceiptFilter): Flow<List<IssuedReceiptSummary>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                receiptDao.observeSummaries(
                    userId = userId,
                    pattern = likePattern(filter.query),
                    fromMillis = filter.fromMillis ?: Long.MIN_VALUE,
                    toMillis = filter.toMillis ?: Long.MAX_VALUE
                ).map { rows -> rows.map { it.toDomain() } }
            }
        }

    override suspend fun getReceipt(id: Long): IssuedReceipt? =
        withContext(ioDispatcher) { receiptDao.getById(id, requireCurrentUserId())?.toDomain() }

    override suspend fun suggestNextNumber(issuedAt: Long): String = withContext(ioDispatcher) {
        nextNumberFor(requireCurrentUserId(), issuedAt)
    }

    override suspend fun saveReceipt(receipt: IssuedReceipt): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val normalized = normalize(receipt)

        val errors = IssuedReceiptValidator.validate(normalized)
        if (errors.isNotEmpty()) throw IssuedReceiptException.Invalid(errors)

        val now = System.currentTimeMillis()
        database.withTransaction {
            if (normalized.id == 0L) {
                val number = normalized.number.ifEmpty { nextNumberFor(userId, normalized.issuedAt) }
                if (receiptDao.countByNumber(userId, number) > 0) throw IssuedReceiptException.DuplicateNumber(number)
                val entity = normalized
                    .copy(number = number, pdfFileName = null, createdAt = now, updatedAt = now)
                    .toEntity(userId)
                    .copy(syncId = UUID.randomUUID().toString())
                val newId = receiptDao.upsert(entity)
                syncHandler.enqueueReceipt(entity.copy(id = newId), SyncOperation.CREATE)
                newId
            } else {
                val existing = receiptDao.getById(normalized.id, userId)
                    ?: throw IssuedReceiptException.NotFound(normalized.id)
                // Numéro et date de création figés ; PDF périmé ; identité de synchronisation conservée.
                val updated = normalized
                    .copy(number = existing.number, createdAt = existing.createdAt, updatedAt = now, pdfFileName = null)
                    .toEntity(userId)
                    .copy(
                        // Ligne antérieure à la synchronisation : jamais envoyée, donc CREATE.
                        syncId = existing.syncId ?: UUID.randomUUID().toString(),
                        deletedAt = existing.deletedAt,
                        version = existing.version
                    )
                receiptDao.update(updated)
                syncHandler.enqueueReceipt(
                    updated.copy(id = existing.id),
                    if (existing.syncId == null) SyncOperation.CREATE else SyncOperation.UPDATE
                )
                existing.id
            }
        }
    }

    override suspend fun markPdfGenerated(id: Long, pdfFileName: String): Unit = withContext(ioDispatcher) {
        receiptDao.setPdfFileName(id, requireCurrentUserId(), pdfFileName)
    }

    override suspend fun deleteReceipt(id: Long): Unit = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        database.withTransaction {
            val existing = receiptDao.getById(id, userId) ?: return@withTransaction
            val now = System.currentTimeMillis()
            receiptDao.softDeleteById(id, userId, now)
            // Jamais synchronisé (syncId nul) : le serveur ne connaît pas cette ligne, rien à supprimer là-bas.
            if (existing.syncId != null) {
                syncHandler.enqueueReceipt(existing.copy(deletedAt = now, updatedAt = now), SyncOperation.DELETE)
            }
        }
    }

    /**
     * Texte nettoyé (espaces de bord retirés, blanc → `null` pour les champs facultatifs) et total
     * RECALCULÉ depuis le détail quand il existe : le montant stocké ne peut jamais diverger des
     * lignes, quoi qu'envoie l'appelant.
     */
    private fun normalize(receipt: IssuedReceipt): IssuedReceipt {
        val pricing = receipt.pricing?.let { p -> p.copy(lines = p.lines.map { it.copy(label = it.label.trim()) }) }
        val total = pricing?.let { ReceiptCalculator.totalsOrNull(it)?.totalMinor }
        return receipt.copy(
            number = receipt.number.trim(),
            customerName = receipt.customerName.trim(),
            customerPhone = receipt.customerPhone.cleaned(),
            description = receipt.description.trim(),
            currencyCode = receipt.currencyCode.trim().uppercase(),
            transactionReference = receipt.transactionReference.cleaned(),
            notes = receipt.notes.cleaned(),
            issuedByName = receipt.issuedByName.cleaned(),
            issuedByTitle = receipt.issuedByTitle.cleaned(),
            pricing = pricing,
            amountMinor = total ?: receipt.amountMinor,
            issuer = receipt.issuer.copy(
                businessName = receipt.issuer.businessName.trim(),
                address = receipt.issuer.address.trim(),
                phone = receipt.issuer.phone.trim(),
                email = receipt.issuer.email.cleaned(),
                taxId = receipt.issuer.taxId.cleaned(),
                logoFileName = receipt.issuer.logoFileName.cleaned()
            )
        )
    }

    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private suspend fun nextNumberFor(userId: Long, issuedAt: Long): String {
        val year = Calendar.getInstance().apply { timeInMillis = issuedAt }.get(Calendar.YEAR)
        val existing = receiptDao.getNumbersWithPrefix(userId, ReceiptNumber.yearPrefix(year))
        return ReceiptNumber.next(year, existing)
    }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")

    internal companion object {
        /** Motif `LIKE` : `\`, `%` et `_` saisis par l'utilisateur sont échappés (voir `IssuedReceiptDao.observeSummaries`). */
        fun likePattern(query: String): String {
            val escaped = query.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            return "%$escaped%"
        }
    }
}
