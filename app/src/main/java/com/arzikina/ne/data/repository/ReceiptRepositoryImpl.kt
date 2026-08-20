package com.arzikina.ne.data.repository

import android.net.Uri
import com.arzikina.ne.data.local.dao.ReceiptDao
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.data.receipts.ReceiptFileStorage
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Receipt
import com.arzikina.ne.domain.repository.ReceiptRepository
import com.arzikina.ne.domain.repository.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implémentation Room de [ReceiptRepository], couplée à [ReceiptFileStorage] pour le fichier
 * physique (voir doc de tête de l'interface : c'est le SEUL endroit du projet où les deux sont
 * assemblés — [ReceiptFileStorage] ignore totalement Room/[Receipt], voir sa propre doc).
 *
 * Isolation multi-utilisateurs : voir `AccountRepositoryImpl` pour le raisonnement. Pas de
 * `database.withTransaction` ici (contrairement à `RecurringTransactionRepositoryImpl`) : aucune
 * écriture multi-DAO à coordonner pour un reçu (table `receipts` seule, aucune cascade) — un simple
 * `withContext(ioDispatcher)` suffit. La copie de fichier ([ReceiptFileStorage.copyToPrivateStorage])
 * n'est, elle non plus, pas transactionnelle avec l'écriture Room : en cas d'échec Room après une
 * copie réussie, le fichier orphelin reste sur le disque sans ligne associée — cas extrêmement rare
 * (Room en local, jamais réseau) et sans risque pour l'utilisateur (aucune référence brisée visible),
 * contrairement à l'inverse (ligne Room sans fichier) qui est activement évité partout ici.
 */
class ReceiptRepositoryImpl @Inject constructor(
    private val receiptDao: ReceiptDao,
    private val receiptFileStorage: ReceiptFileStorage,
    private val sessionManager: SessionManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ReceiptRepository {

    override fun observeReceipts(): Flow<List<Receipt>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                receiptDao.observeAllForUser(userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    override suspend fun getReceipt(id: Long): Receipt? =
        withContext(ioDispatcher) { receiptDao.getById(id, requireCurrentUserId())?.toDomain() }

    override suspend fun resolveDisplayName(sourceUri: String): String? =
        withContext(ioDispatcher) { receiptFileStorage.queryDisplayName(Uri.parse(sourceUri)) }

    override suspend fun importReceipt(
        sourceUri: String,
        displayName: String,
        mimeType: String,
        sourceApp: String?,
        sourceName: String?
    ): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        // Copie AVANT toute écriture Room : si la copie échoue (URI révoquée...), aucune ligne
        // orpheline n'est créée — voir la doc de tête pour le cas inverse (accepté).
        val copied = receiptFileStorage.copyToPrivateStorage(Uri.parse(sourceUri))
        val now = System.currentTimeMillis()
        val receipt = Receipt(
            fileName = displayName,
            localPath = copied.relativePath,
            receivedAt = now,
            fileSize = copied.fileSize,
            mimeType = mimeType,
            sourceApp = sourceApp,
            sourceName = sourceName,
            createdAt = now,
            updatedAt = now
        )
        receiptDao.upsert(receipt.toEntity(userId))
    }

    override suspend fun saveReceipt(receipt: Receipt): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = receiptDao.getById(receipt.id, userId) ?: error("Reçu introuvable.")
        receiptDao.upsert(
            receipt.copy(createdAt = existing.createdAt, updatedAt = System.currentTimeMillis()).toEntity(userId)
        )
        receipt.id
    }

    override suspend fun deleteReceipt(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = receiptDao.getById(id, userId) ?: return@withContext
        // Fichier supprimé AVANT la ligne Room : en cas d'échec inattendu entre les deux, on
        // préfère un fichier orphelin sur le disque (invisible, sans conséquence) plutôt qu'une
        // ligne Room pointant vers un fichier déjà supprimé (visible, cassé) — voir la doc de tête.
        receiptFileStorage.deleteFile(existing.localPath)
        receiptDao.deleteById(id, userId)
    }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
