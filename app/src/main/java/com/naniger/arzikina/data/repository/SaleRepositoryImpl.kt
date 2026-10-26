package com.naniger.arzikina.data.repository

import androidx.room.withTransaction
import com.naniger.arzikina.data.local.dao.AccountDao
import com.naniger.arzikina.data.local.dao.CategoryDao
import com.naniger.arzikina.data.local.dao.ProductDao
import com.naniger.arzikina.data.local.dao.SaleDao
import com.naniger.arzikina.data.local.dao.SaleLineDao
import com.naniger.arzikina.data.local.dao.SalePaymentDao
import com.naniger.arzikina.data.local.dao.ShopDao
import com.naniger.arzikina.data.local.dao.StockMovementDao
import com.naniger.arzikina.data.local.dao.TransactionDao
import com.naniger.arzikina.data.local.database.ArzikinaDatabase
import com.naniger.arzikina.data.local.database.SystemCategoryResolver
import com.naniger.arzikina.data.local.entity.SaleEntity
import com.naniger.arzikina.data.local.entity.SaleLineEntity
import com.naniger.arzikina.data.local.entity.SalePaymentEntity
import com.naniger.arzikina.data.local.entity.StockMovementEntity
import com.naniger.arzikina.data.local.entity.TransactionEntity
import com.naniger.arzikina.data.local.SqlLike
import com.naniger.arzikina.data.mapper.toDomain
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.domain.model.PaymentMethod
import com.naniger.arzikina.domain.model.SyncOperation
import com.naniger.arzikina.domain.model.SystemCategoryKey
import com.naniger.arzikina.domain.model.TransactionType
import com.naniger.arzikina.domain.repository.SaleRepository
import com.naniger.arzikina.domain.repository.SessionManager
import com.naniger.arzikina.domain.shop.Sale
import com.naniger.arzikina.domain.shop.SaleException
import com.naniger.arzikina.domain.shop.SaleCancellation
import com.naniger.arzikina.domain.shop.SaleFilter
import com.naniger.arzikina.domain.shop.SalePlan
import com.naniger.arzikina.domain.shop.SaleSort
import com.naniger.arzikina.domain.shop.SaleSummary
import com.naniger.arzikina.domain.shop.SalePlanner
import com.naniger.arzikina.domain.shop.SaleProductSnapshot
import com.naniger.arzikina.domain.shop.SaleReference
import com.naniger.arzikina.domain.shop.SaleRequest
import com.naniger.arzikina.domain.shop.SaleStatus
import com.naniger.arzikina.domain.shop.ProductUnit
import com.naniger.arzikina.domain.shop.StockMovementType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * Implémentation Room de [SaleRepository].
 *
 * Toutes les règles métier viennent de [SalePlanner] (pur, testé sur JVM) ; ici seulement la lecture
 * de l'instantané des produits et l'écriture du résultat, dans UNE transaction Room :
 * vente + lignes (prix/coût figés) + encaissement + mouvements `SALE` + cache de stock + transaction
 * de revenu. Si une étape échoue, rien n'est écrit.
 *
 * Aucun second système financier : l'encaissement est une transaction `INCOME` ordinaire sur le
 * compte choisi (le solde reste « solde initial + transactions »), reliée à la vente par
 * `sale_payments.transactionId`. Elle est enfilée pour la synchronisation APRÈS la transaction Room,
 * comme `TransactionRepositoryImpl.saveTransaction`.
 *
 * Idempotence : le `syncId` de la vente est la clé du panier ([SaleRequest.draftKey]).
 * Vente, lignes, encaissement et mouvements sont enfilés pour la synchronisation dans la MÊME
 * transaction Room ([ShopSyncEnqueuer], parents avant enfants) ; l'annulation enfile la vente, les mouvements
 * d'annulation et les encaissements mis à jour.
 */
class SaleRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val saleDao: SaleDao,
    private val saleLineDao: SaleLineDao,
    private val salePaymentDao: SalePaymentDao,
    private val productDao: ProductDao,
    private val movementDao: StockMovementDao,
    private val shopDao: ShopDao,
    private val activeShop: ActiveShopProvider,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val categorySyncEnqueuer: CategorySyncEnqueuer,
    private val shopSync: ShopSyncEnqueuer,
    private val transactionSyncEnqueuer: TransactionSyncEnqueuer,
    private val sessionManager: SessionManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SaleRepository {

    /** Résultat de la transaction : la vente et, si elle vient d'être créée, la transaction à synchroniser. */
    private data class Outcome(val saleId: Long, val createdTransaction: TransactionEntity?)

    override suspend fun confirmSale(request: SaleRequest): Sale = withContext(ioDispatcher) {
        val userId = sessionManager.getCurrentUserIdOnce() ?: throw SaleException.NoSession()
        require(request.draftKey.isNotBlank() && request.draftKey.length <= MAX_DRAFT_KEY) { "Clé de panier invalide" }

        val outcome = database.withTransaction { write(userId, request) }
        outcome.createdTransaction?.let { transactionSyncEnqueuer.enqueue(it, SyncOperation.CREATE) }
        load(userId, outcome.saleId) ?: throw SaleException.NotFound(outcome.saleId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSale(id: Long): Flow<Sale?> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(null)
            } else {
                combine(
                    saleDao.observeById(id, userId),
                    saleLineDao.observeForSale(userId, id),
                    salePaymentDao.observeForSale(userId, id)
                ) { sale, lines, payments -> sale?.toDomain(lines, payments) }
            }
        }

    private suspend fun load(userId: Long, saleId: Long): Sale? {
        val sale = saleDao.getById(saleId, userId) ?: return null
        return sale.toDomain(saleLineDao.getForSale(userId, saleId), salePaymentDao.getForSale(userId, saleId))
    }

    override suspend fun getSale(id: Long): Sale? = withContext(ioDispatcher) {
        val userId = sessionManager.getCurrentUserIdOnce() ?: throw SaleException.NoSession()
        load(userId, id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeSales(filter: SaleFilter): Flow<List<SaleSummary>> =
        activeShop.observeScope().flatMapLatest { scope ->
            if (scope == null) {
                flowOf(emptyList())
            } else {
                saleDao.observeSummaries(
                    userId = scope.userId,
                    shopId = scope.shopId,
                    pattern = SqlLike.containsPattern(filter.query),
                    status = filter.status?.name,
                    fromMillis = filter.fromMillis,
                    toMillis = filter.toMillis,
                    sort = if (filter.sort == SaleSort.AMOUNT) SORT_AMOUNT else SORT_DATE,
                    limit = HISTORY_LIMIT
                ).map { rows -> rows.map { it.toDomain() } }
            }
        }

    override suspend fun linkReceipt(saleId: Long, receiptId: Long, expectedCurrent: Long?): Boolean =
        withContext(ioDispatcher) {
            val userId = sessionManager.getCurrentUserIdOnce() ?: throw SaleException.NoSession()
            database.withTransaction {
                val linked = saleDao.linkReceipt(saleId, userId, receiptId, expectedCurrent ?: NO_RECEIPT, System.currentTimeMillis()) > 0
                if (linked) shopSync.enqueueSale(saleId, userId)
                linked
            }
        }

    override suspend fun cancelSale(id: Long, reason: String?): Sale = withContext(ioDispatcher) {
        val userId = sessionManager.getCurrentUserIdOnce() ?: throw SaleException.NoSession()
        val reversals = database.withTransaction { cancel(userId, id, SaleCancellation.cleanReason(reason)) }
        reversals.forEach { transactionSyncEnqueuer.enqueue(it, SyncOperation.CREATE) }
        load(userId, id) ?: throw SaleException.NotFound(id)
    }

    /**
     * DOIT être appelée dans `database.withTransaction`. Retourne les transactions inverses créées (à
     * synchroniser après la transaction) ; liste vide si la vente était déjà annulée.
     */
    private suspend fun cancel(userId: Long, saleId: Long, reason: String?): List<TransactionEntity> {
        val sale = saleDao.getById(saleId, userId) ?: throw SaleException.NotFound(saleId)
        val now = System.currentTimeMillis()
        // La mise à jour conditionnelle est la garde : 0 ligne = déjà annulée (rejeu ou course), rien à refaire.
        if (saleDao.markCancelled(saleId, userId, now, reason) == 0) return emptyList()
        shopSync.enqueueSale(saleId, userId)

        saleLineDao.getForSale(userId, saleId).forEach { line ->
            // Produit supprimé depuis la vente : plus de stock à restaurer.
            val product = productDao.getById(line.productId, userId) ?: return@forEach
            val restored = SaleCancellation.restoredStock(product.stockMilli, line.quantityMilli)
                ?: throw SaleException.InvalidAmount()
            val movementId = movementDao.insert(
                StockMovementEntity(
                    userId = userId,
                    productId = product.id,
                    type = StockMovementType.SALE_CANCELLATION.name,
                    deltaMilli = line.quantityMilli,
                    unitCostMinor = line.unitCostMinor,
                    reason = reason,
                    reference = sale.reference,
                    saleId = saleId,
                    occurredAt = now,
                    createdAt = now,
                    updatedAt = now
                )
            )
            shopSync.enqueueMovement(movementId, userId)
            productDao.updateStock(product.id, userId, restored, now)
        }

        val refundsCategory = SystemCategoryResolver.resolve(categoryDao, categorySyncEnqueuer, SystemCategoryKey.SHOP_REFUNDS, userId)
        val created = mutableListOf<TransactionEntity>()
        salePaymentDao.getForSale(userId, saleId).filter { it.reversalTransactionId == null }.forEach { payment ->
            // Compte supprimé depuis la vente : ses transactions ont disparu avec lui, rien à neutraliser.
            if (accountDao.getById(payment.accountId, userId) == null) return@forEach
            val reversal = TransactionEntity(
                userId = userId,
                amount = payment.amountMinor,
                type = TransactionType.EXPENSE,
                accountId = payment.accountId,
                transferAccountId = null,
                categoryId = refundsCategory.id,
                date = now,
                description = REVERSAL_PREFIX + sale.reference,
                receiptPhotoUri = null,
                latitude = null,
                longitude = null,
                paymentMethod = PaymentMethod.entries.firstOrNull { it.name == payment.method },
                createdAt = now,
                syncId = UUID.randomUUID().toString(),
                updatedAt = now
            )
            val reversalId = transactionDao.upsert(reversal)
            salePaymentDao.setReversal(payment.id, userId, reversalId, now)
            shopSync.enqueuePayment(payment.id, userId)
            created += reversal.copy(id = reversalId)
        }
        return created
    }

    /** DOIT être appelée dans `database.withTransaction` : lectures et écritures forment un tout. */
    private suspend fun write(userId: Long, request: SaleRequest): Outcome {
        // Rejeu d'un panier déjà validé : on rend la vente existante, sans rien réécrire.
        saleDao.getBySyncId(userId, request.draftKey)?.let { return Outcome(it.id, null) }

        val shop = shopDao.getFirstForUser(userId) ?: throw SaleException.ShopNotReady()
        val account = accountDao.getById(request.accountId, userId) ?: throw SaleException.AccountUnavailable()
        if (account.currencyCode != shop.currencyCode) throw SaleException.CurrencyMismatch()

        val ids = SalePlanner.mergeLines(request.lines).map { it.productId }
        val products = productDao.getByIds(userId, shop.id, ids).associate { product ->
            product.id to SaleProductSnapshot(
                id = product.id,
                name = product.name,
                unit = ProductUnit.fromStored(product.unit),
                salePriceMinor = product.salePriceMinor,
                purchasePriceMinor = product.purchasePriceMinor,
                stockMilli = product.stockMilli,
                isArchived = product.isArchived
            )
        }
        val plan = SalePlanner.plan(request.lines, request.discount, request.customerName, request.customerPhone, products)

        val now = System.currentTimeMillis()
        val reference = newReference(now)
        val saleId = saleDao.insert(saleEntity(userId, shop.id, shop.currencyCode, request.draftKey, reference, now, plan))
        shopSync.enqueueSale(saleId, userId, created = true)
        saleLineDao.insertAll(plan.lines.map { line ->
            SaleLineEntity(
                userId = userId,
                saleId = saleId,
                productId = line.productId,
                productName = line.productName,
                unitPriceMinor = line.unitPriceMinor,
                unitCostMinor = line.unitCostMinor,
                quantityMilli = line.quantityMilli,
                lineTotalMinor = line.lineTotalMinor,
                createdAt = now,
                updatedAt = now
            )
        })
        shopSync.enqueueSaleLines(saleId, userId)
        plan.lines.forEach { line ->
            val movementId = movementDao.insert(
                StockMovementEntity(
                    userId = userId,
                    productId = line.productId,
                    type = StockMovementType.SALE.name,
                    deltaMilli = -line.quantityMilli,
                    unitCostMinor = line.unitCostMinor,
                    reason = null,
                    reference = reference,
                    saleId = saleId,
                    occurredAt = now,
                    createdAt = now,
                    updatedAt = now
                )
            )
            shopSync.enqueueMovement(movementId, userId)
            productDao.updateStock(line.productId, userId, line.newStockMilli, now)
        }

        val category = SystemCategoryResolver.resolve(categoryDao, categorySyncEnqueuer, SystemCategoryKey.SHOP_SALES, userId)
        val income = TransactionEntity(
            userId = userId,
            amount = plan.totalMinor,
            type = TransactionType.INCOME,
            accountId = account.id,
            transferAccountId = null,
            categoryId = category.id,
            date = now,
            description = reference,
            receiptPhotoUri = null,
            latitude = null,
            longitude = null,
            paymentMethod = PaymentMethod.CASH,
            createdAt = now,
            syncId = UUID.randomUUID().toString(),
            updatedAt = now
        )
        val transactionId = transactionDao.upsert(income)
        val paymentId = salePaymentDao.insert(
            SalePaymentEntity(
                userId = userId,
                saleId = saleId,
                accountId = account.id,
                method = PaymentMethod.CASH.name,
                amountMinor = plan.totalMinor,
                paidAt = now,
                transactionId = transactionId,
                reversalTransactionId = null,
                createdAt = now,
                updatedAt = now
            )
        )
        shopSync.enqueuePayment(paymentId, userId)
        // Le compte choisi devient le compte proposé par défaut aux prochaines ventes.
        if (shop.accountId != account.id) {
            shopDao.updateAccount(shop.id, userId, account.id, now)
            shopSync.enqueueShop(shop.id, userId)
        }

        return Outcome(saleId, income.copy(id = transactionId))
    }

    private fun saleEntity(
        userId: Long,
        shopId: Long,
        currencyCode: String,
        draftKey: String,
        reference: String,
        now: Long,
        plan: SalePlan
    ) = SaleEntity(
        userId = userId,
        shopId = shopId,
        reference = reference,
        soldAt = now,
        currencyCode = currencyCode,
        customerName = plan.customerName,
        customerPhone = plan.customerPhone,
        subtotalMinor = plan.subtotalMinor,
        discountMinor = plan.discountMinor,
        totalMinor = plan.totalMinor,
        status = SaleStatus.CONFIRMED.name,
        cancelledAt = null,
        cancelReason = null,
        issuedReceiptId = null,
        createdAt = now,
        updatedAt = now,
        syncId = draftKey
    )

    private fun newReference(now: Long): String {
        val date = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        return SaleReference.generate(date.year, date.monthValue, date.dayOfMonth)
    }

    private companion object {
        const val MAX_DRAFT_KEY = 64
        const val SORT_DATE = 0
        const val SORT_AMOUNT = 1
        const val HISTORY_LIMIT = 300
        const val NO_RECEIPT = -1L
        const val REVERSAL_PREFIX = "↩ "
    }
}
