package com.naniger.arzikina.data.repository

import androidx.room.withTransaction
import com.naniger.arzikina.data.local.SqlLike
import com.naniger.arzikina.data.local.dao.ProductCategoryDao
import com.naniger.arzikina.data.local.dao.ProductDao
import com.naniger.arzikina.data.local.database.ArzikinaDatabase
import com.naniger.arzikina.data.local.dao.StockMovementDao
import com.naniger.arzikina.data.local.entity.ProductCategoryEntity
import com.naniger.arzikina.data.local.entity.ProductEntity
import com.naniger.arzikina.data.local.entity.StockMovementEntity
import com.naniger.arzikina.data.mapper.toDomain
import com.naniger.arzikina.data.shop.ProductImageStore
import com.naniger.arzikina.di.IoDispatcher
import com.naniger.arzikina.domain.repository.ProductRepository
import com.naniger.arzikina.domain.repository.SessionManager
import com.naniger.arzikina.domain.shop.Product
import com.naniger.arzikina.domain.shop.ProductCategory
import com.naniger.arzikina.domain.shop.ProductDraft
import com.naniger.arzikina.domain.shop.ProductException
import com.naniger.arzikina.domain.shop.ProductFilter
import com.naniger.arzikina.domain.shop.ProductImageChange
import com.naniger.arzikina.domain.shop.ProductSummary
import com.naniger.arzikina.domain.shop.ProductValidator
import com.naniger.arzikina.domain.shop.ShopStats
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
 * Implémentation Room de [ProductRepository]. Périmètre (utilisateur + boutique) fourni par
 * [ActiveShopProvider]. Recherche, filtres et tri sont exécutés par SQLite (`ProductDao`).
 *
 * Écritures atomiques : produit, stock initial (mouvement `INITIAL`) et cache `stockMilli` sont écrits
 * dans une seule transaction. Les lignes sont créées sans `syncId` (rattrapage par l'étape Ventes).
 */
class ProductRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val productDao: ProductDao,
    private val categoryDao: ProductCategoryDao,
    private val movementDao: StockMovementDao,
    private val shopSync: ShopSyncEnqueuer,
    private val sessionManager: SessionManager,
    private val activeShop: ActiveShopProvider,
    private val imageStore: ProductImageStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ProductRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeProducts(filter: ProductFilter): Flow<List<ProductSummary>> =
        activeShop.observeScope().flatMapLatest { scope ->
            if (scope == null) {
                flowOf(emptyList())
            } else {
                productDao.observeSummaries(
                    userId = scope.userId,
                    shopId = scope.shopId,
                    pattern = SqlLike.containsPattern(filter.query),
                    categoryId = filter.categoryId,
                    availability = filter.availability.code,
                    sort = filter.sort.code,
                    archived = filter.archived
                ).map { rows -> rows.map { it.toDomain(imageStore) } }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeStats(): Flow<ShopStats?> =
        activeShop.observeScope().flatMapLatest { scope ->
            if (scope == null) flowOf(null) else productDao.observeStats(scope.userId, scope.shopId).map { it.toDomain() }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeStockAlerts(limit: Int): Flow<List<ProductSummary>> =
        activeShop.observeScope().flatMapLatest { scope ->
            if (scope == null) {
                flowOf(emptyList())
            } else {
                productDao.observeStockAlerts(scope.userId, scope.shopId, limit).map { rows -> rows.map { it.toDomain(imageStore) } }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeProduct(id: Long): Flow<Product?> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) flowOf(null) else productDao.observeById(id, userId).map { it?.toDomain(imageStore) }
        }

    override suspend fun getProduct(id: Long): Product? = withContext(ioDispatcher) {
        val userId = sessionManager.getCurrentUserIdOnce() ?: return@withContext null
        productDao.getById(id, userId)?.toDomain(imageStore)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCategories(): Flow<List<ProductCategory>> =
        activeShop.observeScope().flatMapLatest { scope ->
            if (scope == null) {
                flowOf(emptyList())
            } else {
                categoryDao.observeForShop(scope.userId, scope.shopId).map { rows -> rows.map { it.toDomain() } }
            }
        }

    override suspend fun saveProduct(draft: ProductDraft): Long = withContext(ioDispatcher) {
        val scope = activeShop.currentScopeOrNull() ?: throw ProductException.NoSession()
        val normalized = draft.normalized()
        val errors = ProductValidator.validate(normalized)
        if (errors.isNotEmpty()) throw ProductException.Invalid(errors)

        // Traitement lourd de l'image AVANT la transaction (ne bloque pas la base) ; fichier supprimé si l'écriture échoue.
        val newImage = (normalized.imageChange as? ProductImageChange.Replace)?.let { imageStore.import(it.sourceUri) }
        try {
            val saved = database.withTransaction {
                // Anti-doublon dans la transaction : lecture et écriture ne peuvent pas s'intercaler.
                normalized.barcode?.let { code ->
                    productDao.findIdByBarcode(scope.userId, scope.shopId, code, normalized.id)
                        ?.let { throw ProductException.DuplicateBarcode(it) }
                }
                normalized.sku?.let { sku ->
                    productDao.findIdBySku(scope.userId, scope.shopId, sku, normalized.id)
                        ?.let { throw ProductException.DuplicateSku(it) }
                }
                val now = System.currentTimeMillis()
                if (normalized.id == 0L) {
                    SavedProduct(create(scope, normalized, newImage, now), orphanedImage = null)
                } else {
                    update(scope, normalized, newImage, now)
                }
            }
            // Ancienne photo supprimée seulement APRÈS validation de la transaction.
            saved.orphanedImage?.let(imageStore::delete)
            saved.id
        } catch (e: Throwable) {
            newImage?.let(imageStore::delete)
            throw e
        }
    }

    /** Résultat d'une écriture : [orphanedImage] est l'ancien fichier devenu inutile (à supprimer après commit). */
    private class SavedProduct(val id: Long, val orphanedImage: String?)

    private suspend fun create(scope: ShopScope, draft: ProductDraft, imageFileName: String?, now: Long): Long {
        val productId = productDao.insert(
            ProductEntity(
                userId = scope.userId,
                shopId = scope.shopId,
                categoryId = draft.categoryId,
                name = draft.name,
                description = draft.description,
                sku = draft.sku,
                barcode = draft.barcode,
                unit = draft.unit.name,
                purchasePriceMinor = draft.purchasePriceMinor,
                salePriceMinor = draft.salePriceMinor,
                minStockMilli = draft.minStockMilli,
                stockMilli = draft.initialStockMilli,
                expiryDate = draft.expiryDate,
                imageFileName = imageFileName,
                isArchived = false,
                createdAt = now,
                updatedAt = now
            )
        )
        shopSync.enqueueProduct(productId, scope.userId)
        if (draft.initialStockMilli != 0L) {
            val movementId = movementDao.insert(
                StockMovementEntity(
                    userId = scope.userId,
                    productId = productId,
                    type = StockMovementType.INITIAL.name,
                    deltaMilli = draft.initialStockMilli,
                    unitCostMinor = draft.purchasePriceMinor,
                    reason = null,
                    reference = null,
                    saleId = null,
                    occurredAt = now,
                    createdAt = now,
                    updatedAt = now
                )
            )
            shopSync.enqueueMovement(movementId, scope.userId)
        }
        return productId
    }

    /** Met à jour la fiche SANS toucher au stock, à l'archivage, à `createdAt` ni à l'identité de synchronisation. */
    private suspend fun update(scope: ShopScope, draft: ProductDraft, newImage: String?, now: Long): SavedProduct {
        val existing = productDao.getById(draft.id, scope.userId)
            ?.takeIf { it.shopId == scope.shopId }
            ?: throw ProductException.NotFound(draft.id)
        val finalImage = when (draft.imageChange) {
            ProductImageChange.Keep -> existing.imageFileName
            ProductImageChange.Remove -> null
            is ProductImageChange.Replace -> newImage
        }
        productDao.update(
            existing.copy(
                categoryId = draft.categoryId,
                name = draft.name,
                description = draft.description,
                sku = draft.sku,
                barcode = draft.barcode,
                unit = draft.unit.name,
                purchasePriceMinor = draft.purchasePriceMinor,
                salePriceMinor = draft.salePriceMinor,
                minStockMilli = draft.minStockMilli,
                expiryDate = draft.expiryDate,
                imageFileName = finalImage,
                updatedAt = now
            )
        )
        shopSync.enqueueProduct(existing.id, scope.userId)
        val orphan = existing.imageFileName?.takeIf { it != finalImage }
        return SavedProduct(existing.id, orphan)
    }

    override suspend fun setArchived(id: Long, archived: Boolean): Unit = withContext(ioDispatcher) {
        val userId = sessionManager.getCurrentUserIdOnce() ?: throw ProductException.NoSession()
        database.withTransaction {
            val existing = productDao.getById(id, userId) ?: throw ProductException.NotFound(id)
            if (existing.isArchived != archived) {
                productDao.update(existing.copy(isArchived = archived, updatedAt = System.currentTimeMillis()))
                shopSync.enqueueProduct(id, userId)
            }
        }
    }

    override suspend fun createCategory(name: String): ProductCategory = withContext(ioDispatcher) {
        val scope = activeShop.currentScopeOrNull() ?: throw ProductException.NoSession()
        val clean = name.trim()
        require(clean.isNotEmpty() && clean.length <= ProductValidator.MAX_NAME_LENGTH) { "Nom de catégorie invalide" }
        database.withTransaction {
            categoryDao.findByName(scope.userId, scope.shopId, clean)?.toDomain() ?: run {
                val now = System.currentTimeMillis()
                val entity = ProductCategoryEntity(
                    userId = scope.userId,
                    shopId = scope.shopId,
                    name = clean,
                    createdAt = now,
                    updatedAt = now
                )
                val id = categoryDao.insert(entity)
                shopSync.enqueueCategory(id, scope.userId)
                entity.copy(id = id).toDomain()
            }
        }
    }

    /** Texte nettoyé : espaces de bord retirés, champs facultatifs vides → `null`. */
    private fun ProductDraft.normalized() = copy(
        name = name.trim(),
        description = description.cleaned(),
        sku = sku.cleaned(),
        barcode = barcode.cleaned()
    )

    private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
