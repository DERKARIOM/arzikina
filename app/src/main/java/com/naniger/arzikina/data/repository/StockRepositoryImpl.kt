package com.naniger.arzikina.data.repository

import androidx.room.withTransaction
import com.naniger.arzikina.data.local.dao.ProductDao
import com.naniger.arzikina.data.local.dao.StockMovementDao
import com.naniger.arzikina.data.local.database.ArzikinaDatabase
import com.naniger.arzikina.data.local.entity.ProductEntity
import com.naniger.arzikina.data.local.entity.StockMovementEntity
import com.naniger.arzikina.data.mapper.toDomain
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.domain.repository.SessionManager
import com.naniger.arzikina.domain.repository.StockRepository
import com.naniger.arzikina.domain.shop.ProductUnit
import com.naniger.arzikina.domain.shop.StockChangeError
import com.naniger.arzikina.domain.shop.StockChangePlan
import com.naniger.arzikina.domain.shop.StockException
import com.naniger.arzikina.domain.shop.StockMovement
import com.naniger.arzikina.domain.shop.StockRules
import com.naniger.arzikina.domain.shop.StockMovementPolicy
import com.naniger.arzikina.domain.shop.StockMovementRequest
import com.naniger.arzikina.domain.shop.StockMovementType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implémentation Room de [StockRepository]. Toutes les règles viennent de [StockMovementPolicy] ; ici
 * seulement : lecture du stock courant, écriture du mouvement et du cache `stockMilli` dans UNE transaction.
 * Les mouvements sont créés sans `syncId` (rattrapage par la synchronisation du module, étape Ventes).
 */
class StockRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val productDao: ProductDao,
    private val movementDao: StockMovementDao,
    private val sessionManager: SessionManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : StockRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMovements(productId: Long, limit: Int): Flow<List<StockMovement>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                movementDao.observeForProduct(userId, productId, limit).map { rows -> rows.map { it.toDomain() } }
            }
        }

    override suspend fun recordMovement(request: StockMovementRequest): Long = withContext(ioDispatcher) {
        val userId = sessionManager.getCurrentUserIdOnce() ?: throw StockException.NoSession()
        database.withTransaction {
            val product = requireProduct(request.productId, userId)
            apply(userId, product, request, StockMovementPolicy.plan(
                type = request.type,
                deltaMilli = request.deltaMilli,
                reason = request.reason.cleaned(),
                unitCostMinor = request.unitCostMinor,
                unit = ProductUnit.fromStored(product.unit),
                currentStockMilli = product.stockMilli
            ))
        }
    }

    override suspend fun adjustStockTo(productId: Long, countedMilli: Long, reason: String): Long =
        withContext(ioDispatcher) {
            val userId = sessionManager.getCurrentUserIdOnce() ?: throw StockException.NoSession()
            database.withTransaction {
                val product = requireProduct(productId, userId)
                val unit = ProductUnit.fromStored(product.unit)
                // L'écart est calculé ICI, sur le stock lu dans la transaction : aucune course possible.
                val delta = StockMovementPolicy.adjustmentDelta(countedMilli, product.stockMilli, unit)
                    ?: throw StockException.Rejected(
                        if (countedMilli in 0..StockRules.MAX_QUANTITY_MILLI) {
                            StockChangeError.FRACTION_NOT_ALLOWED
                        } else {
                            StockChangeError.QUANTITY_TOO_LARGE
                        }
                    )
                val request = StockMovementRequest(productId, StockMovementType.ADJUSTMENT, delta, reason)
                apply(userId, product, request, StockMovementPolicy.plan(
                    type = request.type,
                    deltaMilli = delta,
                    reason = reason.cleaned(),
                    unitCostMinor = null,
                    unit = unit,
                    currentStockMilli = product.stockMilli
                ))
            }
        }

    private suspend fun requireProduct(productId: Long, userId: Long): ProductEntity =
        productDao.getById(productId, userId) ?: throw StockException.ProductNotFound(productId)

    /** Écrit le mouvement et le nouveau stock si le plan est accepté ; retourne le nouveau stock. */
    private suspend fun apply(
        userId: Long,
        product: ProductEntity,
        request: StockMovementRequest,
        plan: StockChangePlan
    ): Long {
        val newStock = when (plan) {
            is StockChangePlan.Rejected -> throw StockException.Rejected(plan.error)
            is StockChangePlan.Accepted -> plan.newStockMilli
        }
        val now = System.currentTimeMillis()
        movementDao.insert(
            StockMovementEntity(
                userId = userId,
                productId = product.id,
                type = request.type.name,
                deltaMilli = request.deltaMilli,
                unitCostMinor = request.unitCostMinor,
                reason = request.reason.cleaned(),
                reference = request.reference.cleaned(),
                saleId = null,
                occurredAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
        productDao.updateStock(product.id, userId, newStock, now)
        val cost = request.unitCostMinor
        if (request.updatePurchasePrice && request.type == StockMovementType.RESTOCK && cost != null) {
            productDao.updatePurchasePrice(product.id, userId, cost, now)
        }
        return newStock
    }

    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
