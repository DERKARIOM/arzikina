package com.naniger.arzikina.data.repository

import androidx.room.withTransaction
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
import com.naniger.arzikina.data.local.database.ArzikinaDatabase
import com.naniger.arzikina.data.remote.dto.ProductCategoryServerStateDto
import com.naniger.arzikina.data.remote.dto.ProductServerStateDto
import com.naniger.arzikina.data.remote.dto.SaleLineServerStateDto
import com.naniger.arzikina.data.remote.dto.SalePaymentServerStateDto
import com.naniger.arzikina.data.remote.dto.SaleServerStateDto
import com.naniger.arzikina.data.remote.dto.ShopServerStateDto
import com.naniger.arzikina.data.remote.dto.StockMovementServerStateDto
import com.naniger.arzikina.data.shop.ProductImageStore
import com.naniger.arzikina.data.shop.ShopImageSyncState
import com.naniger.arzikina.data.shop.ShopSyncMapper
import com.naniger.arzikina.data.shop.ShopSyncPolicy
import com.naniger.arzikina.domain.repository.SessionManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Réception (pull / résultat d'envoi) et rattrapage de la synchronisation du module Boutique, isolés de
 * `SyncEngineImpl` (déjà très volumineux) — même découpage que [IssuedReceiptSyncHandler]. Le moteur ne
 * fait que déléguer. Contrat complet : `claude/shop/SYNC-BOUTIQUE-CONTRAT.md`.
 *
 * Règles :
 * - **Clés étrangères** : l'état serveur porte des `syncId`, résolus ici en ids Room locaux. Une référence
 *   OBLIGATOIRE inconnue lève [UnresolvedReferenceException] (la ligne n'est pas écrite) ; une référence
 *   facultative inconnue conserve la valeur locale. Sur un résultat d'envoi, la ligne locale existe déjà et
 *   garde ses références si elles ne se résolvent pas.
 * - **Idempotence** : voir [ShopSyncPolicy.shouldApply] — un retéléchargement n'écrase pas une ligne déjà à jour.
 * - **Boutique unique par compte** : son `syncId` est déterministe ([ShopSyncMapper.shopSyncId]). Une boutique
 *   locale encore sans `syncId` est ADOPTÉE par l'état serveur au lieu de créer un doublon.
 * - **Stock** : `products.stockMilli` n'est jamais reçu ; il est recalculé (somme des mouvements) à chaque
 *   mouvement reçu. Un stock négatif résultant d'une fusion entre appareils est conservé tel quel, jamais masqué.
 * - Toute application s'exécute dans UNE transaction Room (une ligne reçue est écrite en entier ou pas du tout).
 */
@Singleton
class ShopSyncHandler @Inject constructor(
    private val database: ArzikinaDatabase,
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
    private val enqueuer: ShopSyncEnqueuer,
    private val sessionManager: SessionManager,
    private val userServerLinkDao: UserServerLinkDao,
    private val imageSyncState: ShopImageSyncState,
    private val imageStore: ProductImageStore,
    private val json: Json
) {

    /**
     * Référence obligatoire encore inconnue. [deferrable] : la ligne est assez récente pour que son parent
     * arrive au pull suivant (le moteur ne fait alors pas avancer le curseur au-delà de [updatedAt]).
     */
    class UnresolvedReferenceException(
        val entityType: String,
        val syncId: String,
        val updatedAt: Long,
        val deferrable: Boolean
    ) : Exception("Référence inconnue pour $entityType $syncId")

    // ───────────────────────────── Mise en file (rattrapage) ─────────────────────────────

    /**
     * Voir `SyncEngine.enqueueUnsyncedLocalData`. Parents avant enfants. Les ventes passent AVANT leurs lignes :
     * une vente est reconnue « jamais envoyée » à ses lignes sans `syncId`.
     */
    suspend fun enqueueUnsynced(userId: Long) {
        database.withTransaction {
            // Rattrapage UNIQUE des photos : relevé AVANT l'envoi des produits jamais synchronisés (qui reçoivent leur
            // syncId et partent déjà avec leur photo), pour ne pas les mettre deux fois en file. Sans lien serveur
            // rien n'est enfilé : on ne marque donc rien, le rattrapage aura lieu après la connexion.
            val linked = userServerLinkDao.getByLocalUserId(userId) != null
            val backfillImages = !imageSyncState.isBackfilled(userId) && linked
            val productsWithImage = if (backfillImages) productDao.idsWithImageForSync(userId) else emptyList()

            shopDao.getUnsyncedForUser(userId).forEach { enqueuer.enqueueShop(it.id, userId) }
            categoryDao.getUnsyncedForUser(userId).forEach { enqueuer.enqueueCategory(it.id, userId) }
            productDao.getUnsyncedForUser(userId).forEach { enqueuer.enqueueProduct(it.id, userId) }
            productsWithImage.forEach { enqueuer.enqueueProduct(it, userId) }
            if (backfillImages) imageSyncState.markBackfilled(userId)
            saleDao.getUnsyncedForUser(userId).forEach { enqueuer.enqueueSale(it.id, userId) }
            saleLineDao.getUnsyncedForUser(userId).forEach { enqueuer.enqueueSaleLine(it.id, userId) }
            movementDao.getUnsyncedForUser(userId).forEach { enqueuer.enqueueMovement(it.id, userId) }
            paymentDao.getUnsyncedForUser(userId).forEach { enqueuer.enqueuePayment(it.id, userId) }
        }
    }

    // ───────────────────────────── Application des états serveur ─────────────────────────────

    /**
     * Voir `SyncEngineImpl.applyServerEntity`. [allowCreate] : `false` pour un résultat d'envoi (la ligne
     * existe forcément déjà), `true` pour un pull.
     */
    suspend fun apply(entityType: String, element: JsonElement, allowCreate: Boolean) {
        var replacedImage: String? = null
        database.withTransaction {
            when (entityType) {
                SHOPS -> applyShop(decode(ShopServerStateDto.serializer(), element), allowCreate)
                CATEGORIES -> applyCategory(decode(ProductCategoryServerStateDto.serializer(), element), allowCreate)
                PRODUCTS -> replacedImage = applyProduct(decode(ProductServerStateDto.serializer(), element), allowCreate)
                SALES -> applySale(decode(SaleServerStateDto.serializer(), element), allowCreate)
                SALE_LINES -> applySaleLine(decode(SaleLineServerStateDto.serializer(), element), allowCreate)
                SALE_PAYMENTS -> applyPayment(decode(SalePaymentServerStateDto.serializer(), element), allowCreate)
                STOCK_MOVEMENTS -> applyMovement(decode(StockMovementServerStateDto.serializer(), element), allowCreate)
            }
            Unit
        }
        // Fichier de l'ancienne photo supprimé APRÈS la transaction (une suppression de fichier ne s'annule pas).
        replacedImage?.let(imageStore::delete)
    }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, element: JsonElement): T =
        json.decodeFromJsonElement(serializer, element)

    private suspend fun applyShop(s: ShopServerStateDto, allowCreate: Boolean) {
        val current = sessionManager.getCurrentUserIdOnce()
        var local = shopDao.getBySyncId(s.id)
        // Adoption : une boutique locale jamais synchronisée devient la boutique du compte (pas de doublon).
        val adopted = local == null && allowCreate && current != null
        if (adopted) local = shopDao.getUnsyncedForUser(current!!).firstOrNull()
        val isAdoption = adopted && local != null
        if (!isAdoption && !shouldApply(local?.version, s.version, allowCreate, local != null)) return
        val userId = local?.userId ?: current ?: return
        val accountId = s.accountSyncId?.let { accountDao.getBySyncId(it)?.id ?: local?.accountId }
        shopDao.upsert(ShopSyncMapper.toEntity(s, userId, local, accountId))
    }

    private suspend fun applyCategory(s: ProductCategoryServerStateDto, allowCreate: Boolean) {
        val local = categoryDao.getBySyncId(s.id)
        if (!shouldApply(local?.version, s.version, allowCreate, local != null)) return
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return
        val shopId = shopDao.getBySyncId(s.shopSyncId)?.id ?: local?.shopId ?: unresolved(CATEGORIES, s.shopSyncId, s.updatedAt)
        categoryDao.upsert(ShopSyncMapper.toEntity(s, userId, local, shopId))
    }

    /** @return le nom de l'ANCIENNE photo si elle est remplacée ou retirée (fichier à supprimer), sinon `null`. */
    private suspend fun applyProduct(s: ProductServerStateDto, allowCreate: Boolean): String? {
        val local = productDao.getBySyncId(s.id)
        if (!shouldApply(local?.version, s.version, allowCreate, local != null)) return null
        val userId = local?.userId ?: sessionManager.getCurrentUserIdOnce() ?: return null
        val shopId = shopDao.getBySyncId(s.shopSyncId)?.id ?: local?.shopId ?: unresolved(PRODUCTS, s.shopSyncId, s.updatedAt)
        val categoryId = s.categorySyncId?.let { categoryDao.getBySyncId(it)?.id ?: local?.categoryId }
        val entity = ShopSyncMapper.toEntity(s, userId, local, shopId, categoryId, imageSyncState.isBackfilled(userId))
        productDao.upsert(entity)
        return local?.imageFileName?.takeIf { it != entity.imageFileName }
    }

    private suspend fun applySale(s: SaleServerStateDto, allowCreate: Boolean) {
        val current = sessionManager.getCurrentUserIdOnce() ?: return
        val local = saleDao.getBySyncId(current, s.id)
        if (!shouldApply(local?.version, s.version, allowCreate, local != null)) return
        val userId = local?.userId ?: current
        val shopId = shopDao.getBySyncId(s.shopSyncId)?.id ?: local?.shopId ?: unresolved(SALES, s.shopSyncId, s.updatedAt)
        val receiptId = s.issuedReceiptSyncId?.let { issuedReceiptDao.getBySyncId(it)?.id ?: local?.issuedReceiptId }
        saleDao.upsert(ShopSyncMapper.toEntity(s, userId, local, shopId, receiptId))
    }

    private suspend fun applySaleLine(s: SaleLineServerStateDto, allowCreate: Boolean) {
        val current = sessionManager.getCurrentUserIdOnce() ?: return
        val local = saleLineDao.getBySyncId(s.id)
        if (!shouldApply(local?.version, s.version, allowCreate, local != null)) return
        val userId = local?.userId ?: current
        val saleId = saleDao.getBySyncId(userId, s.saleSyncId)?.id ?: local?.saleId ?: unresolved(SALE_LINES, s.saleSyncId, s.updatedAt)
        val productId = productDao.getBySyncId(s.productSyncId)?.id ?: local?.productId ?: unresolved(SALE_LINES, s.productSyncId, s.updatedAt)
        saleLineDao.upsert(ShopSyncMapper.toEntity(s, userId, local, saleId, productId))
    }

    private suspend fun applyPayment(s: SalePaymentServerStateDto, allowCreate: Boolean) {
        val current = sessionManager.getCurrentUserIdOnce() ?: return
        val local = paymentDao.getBySyncId(s.id)
        if (!shouldApply(local?.version, s.version, allowCreate, local != null)) return
        val userId = local?.userId ?: current
        val saleId = saleDao.getBySyncId(userId, s.saleSyncId)?.id ?: local?.saleId ?: unresolved(SALE_PAYMENTS, s.saleSyncId, s.updatedAt)
        val accountId = accountDao.getBySyncId(s.accountSyncId)?.id ?: local?.accountId ?: unresolved(SALE_PAYMENTS, s.accountSyncId, s.updatedAt)
        val transactionId = s.transactionSyncId?.let { transactionDao.getBySyncId(it)?.id ?: local?.transactionId }
        val reversalId = s.reversalTransactionSyncId?.let { transactionDao.getBySyncId(it)?.id ?: local?.reversalTransactionId }
        paymentDao.upsert(ShopSyncMapper.toEntity(s, userId, local, saleId, accountId, transactionId, reversalId))
    }

    private suspend fun applyMovement(s: StockMovementServerStateDto, allowCreate: Boolean) {
        val current = sessionManager.getCurrentUserIdOnce() ?: return
        val local = movementDao.getBySyncId(s.id)
        if (!shouldApply(local?.version, s.version, allowCreate, local != null)) return
        val userId = local?.userId ?: current
        val productId = productDao.getBySyncId(s.productSyncId)?.id ?: local?.productId ?: unresolved(STOCK_MOVEMENTS, s.productSyncId, s.updatedAt)
        val saleId = s.saleSyncId?.let { saleDao.getBySyncId(userId, it)?.id ?: local?.saleId }
        movementDao.upsert(ShopSyncMapper.toEntity(s, userId, local, productId, saleId))
        // Résultat d'envoi : le mouvement est déjà compté localement. Pull : le stock est recalculé.
        if (allowCreate) {
            productDao.updateStock(productId, userId, movementDao.sumDelta(userId, productId), System.currentTimeMillis())
        }
    }

    /** Une ligne inconnue (`local == null`) est toujours appliquée si l'appelant a vérifié `allowCreate`. */
    private fun shouldApply(localVersion: Int?, serverVersion: Int, allowCreate: Boolean, localExists: Boolean): Boolean {
        if (!localExists && !allowCreate) return false
        return ShopSyncPolicy.shouldApply(allowCreate, localVersion, serverVersion)
    }

    private fun unresolved(entityType: String, syncId: String, updatedAt: Long): Nothing =
        throw UnresolvedReferenceException(
            entityType, syncId, updatedAt,
            deferrable = ShopSyncPolicy.isDeferrable(updatedAt, System.currentTimeMillis())
        )

    companion object {
        const val SHOPS = "shops"
        const val CATEGORIES = "product_categories"
        const val PRODUCTS = "products"
        const val SALES = "sales"
        const val SALE_LINES = "sale_lines"
        const val SALE_PAYMENTS = "sale_payments"
        const val STOCK_MOVEMENTS = "stock_movements"

        /**
         * Ordre de tirage (parents avant enfants) : les ventes avant leurs lignes/paiements/mouvements, les
         * produits avant les lignes et mouvements. À placer APRÈS `accounts`, `transactions` et
         * `issued_receipts` dans `SyncEngineImpl.SUPPORTED_ENTITY_TYPES` (références des paiements et des ventes).
         */
        val ENTITY_TYPES: List<String> =
            listOf(SHOPS, CATEGORIES, PRODUCTS, SALES, SALE_LINES, SALE_PAYMENTS, STOCK_MOVEMENTS)
    }
}
