package com.naniger.arzikina.data.repository

import com.naniger.arzikina.data.local.dao.AccountDao
import com.naniger.arzikina.data.local.dao.IssuedReceiptDao
import com.naniger.arzikina.data.local.dao.ProductCategoryDao
import com.naniger.arzikina.data.local.dao.ProductDao
import com.naniger.arzikina.data.local.dao.SaleDao
import com.naniger.arzikina.data.local.dao.SaleLineDao
import com.naniger.arzikina.data.local.dao.SalePaymentDao
import com.naniger.arzikina.data.local.dao.ShopDao
import com.naniger.arzikina.data.local.dao.StockMovementDao
import com.naniger.arzikina.data.local.dao.TransactionDao
import com.naniger.arzikina.data.local.dao.UserServerLinkDao
import com.naniger.arzikina.data.remote.dto.ProductCategorySyncPayload
import com.naniger.arzikina.data.remote.dto.ProductSyncPayload
import com.naniger.arzikina.data.remote.dto.SaleLineSyncPayload
import com.naniger.arzikina.data.remote.dto.SalePaymentSyncPayload
import com.naniger.arzikina.data.remote.dto.SaleSyncPayload
import com.naniger.arzikina.data.remote.dto.ShopSyncPayload
import com.naniger.arzikina.data.remote.dto.StockMovementSyncPayload
import com.naniger.arzikina.data.shop.ShopSyncMapper
import com.naniger.arzikina.domain.model.SyncOperation
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point d'entrée UNIQUE pour enfiler la synchronisation du module Boutique (voir
 * `claude/shop/SYNC-BOUTIQUE-CONTRAT.md`). À appeler APRÈS chaque écriture Room d'une ligne synchronisée,
 * DANS la même transaction : la file et les données ne peuvent ainsi jamais diverger.
 *
 * Chaque méthode `enqueueXxx(id, userId)` RELIT la ligne en base (version et horodatage exacts, aucune
 * donnée périmée), puis :
 *  1. attribue un `syncId` si la ligne n'en a pas encore (la première écriture est alors une CRÉATION) ;
 *  2. résout les `syncId` des lignes référencées. Un parent Boutique encore sans `syncId` est enfilé AVANT
 *     l'enfant (création récursive) — sinon il resterait à jamais hors de la file ;
 *  3. enfile le payload JSON via [SyncQueueEnqueuer].
 * Si un parent obligatoire est introuvable, rien n'est enfilé ET le `syncId` n'est pas attribué : la ligne
 * reste à `syncId` nul, donc reprise par le rattrapage de la synchronisation.
 *
 * Le stock d'un produit n'est PAS synchronisé (cache recalculé) : `ProductDao.updateStock` n'enfile rien.
 * Retourne le `syncId` de la ligne (existant ou attribué), `null` si elle est introuvable ou non résolue.
 */
@Singleton
class ShopSyncEnqueuer @Inject constructor(
    private val queue: SyncQueueEnqueuer,
    private val json: Json,
    private val shopDao: ShopDao,
    private val categoryDao: ProductCategoryDao,
    private val productDao: ProductDao,
    private val movementDao: StockMovementDao,
    private val saleDao: SaleDao,
    private val saleLineDao: SaleLineDao,
    private val paymentDao: SalePaymentDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val issuedReceiptDao: IssuedReceiptDao,
    private val userServerLinkDao: UserServerLinkDao
) {

    suspend fun enqueueShop(id: Long, userId: Long): String? {
        val e = shopDao.getById(id, userId) ?: return null
        // `syncId` déterministe par compte serveur : sans lien serveur (jamais connecté) rien n'est enfilé,
        // la ligne reste à `syncId` nul et le rattrapage la reprend après la connexion.
        val newSyncId = e.syncId ?: userServerLinkDao.getByLocalUserId(userId)?.serverUserId
            ?.let(ShopSyncMapper::shopSyncId) ?: return null
        return enqueueRow(
            ShopSyncPayload.serializer(), "shops", e.syncId, newSyncId, created = false,
            assign = { shopDao.setSyncId(id, userId, it) }
        ) { syncId, op ->
            val account = e.accountId?.let { accountDao.getByIdIncludingDeleted(it, userId)?.syncId }
            ShopSyncMapper.toPayload(e, syncId, op, account)
        }
    }

    suspend fun enqueueCategory(id: Long, userId: Long): String? {
        val e = categoryDao.getById(id, userId) ?: return null
        return enqueueRow(
            ProductCategorySyncPayload.serializer(), "product_categories", e.syncId, newId(), created = false,
            assign = { categoryDao.setSyncId(id, userId, it) }
        ) { syncId, op ->
            val shop = shopSyncId(e.shopId, userId) ?: return@enqueueRow null
            ShopSyncMapper.toPayload(e, syncId, op, shop)
        }
    }

    suspend fun enqueueProduct(id: Long, userId: Long): String? {
        val e = productDao.getById(id, userId) ?: return null
        return enqueueRow(
            ProductSyncPayload.serializer(), "products", e.syncId, newId(), created = false,
            assign = { productDao.setSyncId(id, userId, it) }
        ) { syncId, op ->
            val shop = shopSyncId(e.shopId, userId) ?: return@enqueueRow null
            val category = e.categoryId?.let { categorySyncId(it, userId) }
            ShopSyncMapper.toPayload(e, syncId, op, shop, category)
        }
    }

    /** Mouvement de stock : append-only, donc toujours une création en pratique. */
    suspend fun enqueueMovement(id: Long, userId: Long): String? {
        val e = movementDao.getById(id, userId) ?: return null
        return enqueueRow(
            StockMovementSyncPayload.serializer(), "stock_movements", e.syncId, newId(), created = false,
            assign = { movementDao.setSyncId(id, userId, it) }
        ) { syncId, op ->
            val product = productSyncId(e.productId, userId) ?: return@enqueueRow null
            val sale = e.saleId?.let { saleSyncId(it, userId) }
            ShopSyncMapper.toPayload(e, syncId, op, product, sale)
        }
    }

    /**
     * [created] : `true` à la validation d'une vente. Son `syncId` est posé dès l'insertion (clé
     * d'idempotence du panier), donc l'absence de `syncId` ne suffit pas à reconnaître une création.
     */
    suspend fun enqueueSale(id: Long, userId: Long, created: Boolean = false): String? {
        val e = saleDao.getById(id, userId) ?: return null
        // Vente antérieure à la synchronisation (lignes sans `syncId`) : le serveur ne la connaît pas, un
        // UPDATE échouerait (`not_found`). Une CRÉATION est idempotente côté serveur, donc toujours sûre.
        val neverSent = created || saleLineDao.countUnsyncedForSale(userId, id) > 0
        return enqueueRow(
            SaleSyncPayload.serializer(), "sales", e.syncId, newId(), neverSent,
            assign = { saleDao.setSyncId(id, userId, it) }
        ) { syncId, op ->
            val shop = shopSyncId(e.shopId, userId) ?: return@enqueueRow null
            val receipt = e.issuedReceiptId?.let { issuedReceiptDao.getById(it, userId)?.syncId }
            ShopSyncMapper.toPayload(e, syncId, op, shop, receipt)
        }
    }

    /** Enfile toutes les lignes d'une vente (créées en lot, sans ids retournés). */
    suspend fun enqueueSaleLines(saleId: Long, userId: Long) {
        saleLineDao.getForSale(userId, saleId).forEach { enqueueSaleLine(it.id, userId) }
    }

    suspend fun enqueueSaleLine(id: Long, userId: Long): String? {
        val e = saleLineDao.getById(id, userId) ?: return null
        return enqueueRow(
            SaleLineSyncPayload.serializer(), "sale_lines", e.syncId, newId(), created = false,
            assign = { saleLineDao.setSyncId(id, userId, it) }
        ) { syncId, op ->
            val sale = saleSyncId(e.saleId, userId) ?: return@enqueueRow null
            val product = productSyncId(e.productId, userId) ?: return@enqueueRow null
            ShopSyncMapper.toPayload(e, syncId, op, sale, product)
        }
    }

    suspend fun enqueuePayment(id: Long, userId: Long): String? {
        val e = paymentDao.getById(id, userId) ?: return null
        return enqueueRow(
            SalePaymentSyncPayload.serializer(), "sale_payments", e.syncId, newId(), created = false,
            assign = { paymentDao.setSyncId(id, userId, it) }
        ) { syncId, op ->
            val sale = saleSyncId(e.saleId, userId) ?: return@enqueueRow null
            val account = accountDao.getByIdIncludingDeleted(e.accountId, userId)?.syncId
                ?: return@enqueueRow null
            val transaction = e.transactionId?.let { transactionDao.getByIdIncludingDeleted(it, userId)?.syncId }
            val reversal = e.reversalTransactionId?.let { transactionDao.getByIdIncludingDeleted(it, userId)?.syncId }
            ShopSyncMapper.toPayload(e, syncId, op, sale, account, transaction, reversal)
        }
    }

    /**
     * Cœur commun : choisit l'opération, construit le payload ([build] retourne `null` si une référence
     * obligatoire est introuvable), n'attribue le `syncId` qu'en cas de succès, puis enfile.
     */
    private suspend fun <P> enqueueRow(
        serializer: KSerializer<P>,
        entityType: String,
        existingSyncId: String?,
        newSyncId: String,
        created: Boolean,
        assign: suspend (String) -> Unit,
        build: suspend (syncId: String, operation: SyncOperation) -> P?
    ): String? {
        val syncId = existingSyncId ?: newSyncId
        val operation = if (created || existingSyncId == null) SyncOperation.CREATE else SyncOperation.UPDATE
        val payload = build(syncId, operation) ?: return existingSyncId
        if (existingSyncId == null) assign(syncId)
        queue.enqueue(entityType, syncId, operation, json.encodeToString(serializer, payload))
        return syncId
    }

    private fun newId(): String = UUID.randomUUID().toString()

    // ---- Résolution des parents : syncId existant, ou enfilage du parent s'il n'en a pas encore ----

    private suspend fun shopSyncId(id: Long, userId: Long): String? =
        shopDao.getById(id, userId)?.let { it.syncId ?: enqueueShop(id, userId) }

    private suspend fun categorySyncId(id: Long, userId: Long): String? =
        categoryDao.getById(id, userId)?.let { it.syncId ?: enqueueCategory(id, userId) }

    private suspend fun productSyncId(id: Long, userId: Long): String? =
        productDao.getById(id, userId)?.let { it.syncId ?: enqueueProduct(id, userId) }

    /**
     * Une vente n'est référençable que si sa boutique l'est aussi : sinon ses lignes/paiements/mouvements
     * recevraient un `syncId` alors que la vente n'a jamais été enfilée, et le rattrapage (qui se fonde sur
     * les lignes sans `syncId`) ne la reprendrait plus.
     */
    private suspend fun saleSyncId(id: Long, userId: Long): String? {
        val sale = saleDao.getById(id, userId) ?: return null
        shopSyncId(sale.shopId, userId) ?: return null
        return sale.syncId ?: enqueueSale(id, userId)
    }
}
