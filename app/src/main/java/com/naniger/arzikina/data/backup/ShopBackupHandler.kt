package com.naniger.arzikina.data.backup

import com.naniger.arzikina.data.local.dao.ProductCategoryDao
import com.naniger.arzikina.data.local.dao.ProductDao
import com.naniger.arzikina.data.local.dao.SaleDao
import com.naniger.arzikina.data.local.dao.SaleLineDao
import com.naniger.arzikina.data.local.dao.SalePaymentDao
import com.naniger.arzikina.data.local.dao.ShopDao
import com.naniger.arzikina.data.local.dao.StockMovementDao
import com.naniger.arzikina.data.local.database.ArzikinaDatabase
import com.naniger.arzikina.data.shop.CartLocalDataSource
import com.naniger.arzikina.data.shop.ProductImageStore
import java.util.Base64
import javax.inject.Inject

/**
 * Sauvegarde et restauration du module « Boutique », séparées de `BackupRepositoryImpl` pour garder celui-ci
 * lisible (une classe = une responsabilité). Appelé À L'INTÉRIEUR de la transaction Room de l'import : tout
 * réussit ou rien n'est écrit.
 *
 * Restauration (voir `BackupRepositoryImpl.importBackup`) : la Boutique est TOUJOURS purgée avant, même si le
 * fichier n'en contient pas — ses ventes pointent vers des transactions et des comptes qui, eux, sont remplacés
 * (nouveaux ids) ; les conserver les laisserait pointer dans le vide. Ordre d'insertion : boutique, catégories,
 * produits, ventes, puis lignes, paiements et mouvements (qui référencent produits et ventes). Les paiements
 * ont besoin de la correspondance des transactions, d'où l'appel APRÈS leur restauration.
 *
 * Photos : lues au disque à l'export et réécrites sous un nouveau nom à l'import. Une photo illisible est
 * ignorée (le produit est restauré sans photo) ; en cas d'échec de la transaction, les photos déjà écrites
 * sont supprimées ([restore]). Les anciennes photos ne sont supprimées qu'APRÈS le succès ([deleteImages]).
 */
class ShopBackupHandler @Inject constructor(
    private val database: ArzikinaDatabase,
    private val images: ProductImageStore,
    private val shopDao: ShopDao,
    private val productCategoryDao: ProductCategoryDao,
    private val productDao: ProductDao,
    private val stockMovementDao: StockMovementDao,
    private val saleDao: SaleDao,
    private val saleLineDao: SaleLineDao,
    private val salePaymentDao: SalePaymentDao,
    private val cart: CartLocalDataSource
) {
    private val dao get() = database.shopBackupDao()

    suspend fun export(userId: Long): ShopBackupDto = ShopBackupDto(
        shops = dao.shops(userId).map { it.toDto() },
        productCategories = dao.productCategories(userId).map { it.toDto() },
        products = dao.products(userId).map { it.toDto(imageBase64 = encodeImage(it.imageFileName)) },
        stockMovements = dao.stockMovements(userId).map { it.toDto() },
        sales = dao.sales(userId).map { it.toDto() },
        saleLines = dao.saleLines(userId).map { it.toDto() },
        salePayments = dao.salePayments(userId).map { it.toDto() }
    )

    /** Noms des photos actuelles, à capturer AVANT la transaction (voir [deleteImages]). */
    suspend fun imageFileNames(userId: Long): List<String> = dao.imageFileNames(userId)

    /** Supprime les fichiers de photos donnés ; ne lève jamais (un fichier orphelin est sans conséquence). */
    fun deleteImages(fileNames: List<String>) {
        fileNames.forEach { runCatching { images.delete(it) } }
    }

    /** Purge toute la Boutique de [userId] (enfants avant parents, par cohérence : aucune clé étrangère SQL). */
    suspend fun purge(userId: Long) {
        salePaymentDao.deleteAllForUser(userId)
        saleLineDao.deleteAllForUser(userId)
        saleDao.deleteAllForUser(userId)
        // Les identifiants de produits changent à la restauration : un ancien panier pointerait vers d'autres produits.
        cart.purge(userId)
        stockMovementDao.deleteAllForUser(userId)
        productDao.deleteAllForUser(userId)
        productCategoryDao.deleteAllForUser(userId)
        shopDao.deleteAllForUser(userId)
    }

    /**
     * Réinsère la Boutique du fichier ([backup] `null` = fichier sans Boutique : rien à insérer).
     * [accountIdMap] et [transactionIdMap] viennent de la restauration principale.
     */
    suspend fun restore(
        userId: Long,
        backup: ShopBackupDto?,
        accountIdMap: Map<Long, Long>,
        transactionIdMap: Map<Long, Long>
    ) {
        if (backup == null) return
        val writtenImages = mutableListOf<String>()
        try {
            val shopIdMap = backup.shops
                .zip(dao.insertShops(backup.shops.map { it.toEntity(userId, accountIdMap) }))
                .associate { (dto, newId) -> dto.id to newId }

            val categoryIdMap = backup.productCategories
                .zip(dao.insertProductCategories(backup.productCategories.map { it.toEntity(userId, shopIdMap) }))
                .associate { (dto, newId) -> dto.id to newId }

            val productEntities = backup.products.map { dto ->
                val fileName = dto.imageBase64?.let { restoreImage(it) }?.also { writtenImages += it }
                dto.toEntity(userId, shopIdMap, categoryIdMap, fileName)
            }
            val productIdMap = backup.products
                .zip(dao.insertProducts(productEntities))
                .associate { (dto, newId) -> dto.id to newId }

            val saleIdMap = backup.sales
                .zip(dao.insertSales(backup.sales.map { it.toEntity(userId, shopIdMap) }))
                .associate { (dto, newId) -> dto.id to newId }

            dao.insertSaleLines(backup.saleLines.map { it.toEntity(userId, saleIdMap, productIdMap) })
            dao.insertSalePayments(
                backup.salePayments.map { it.toEntity(userId, saleIdMap, accountIdMap, transactionIdMap) }
            )
            dao.insertStockMovements(backup.stockMovements.map { it.toEntity(userId, productIdMap, saleIdMap) })
        } catch (e: Throwable) {
            // La transaction sera annulée : les photos déjà écrites n'ont plus de ligne qui les référence.
            deleteImages(writtenImages)
            throw e
        }
    }

    private fun encodeImage(fileName: String?): String? =
        images.resolve(fileName)?.let { file ->
            runCatching { Base64.getEncoder().encodeToString(file.readBytes()) }.getOrNull()
        }

    /** Décode et réécrit une photo ; `null` si le contenu est invalide ou illisible (le produit reste, sans photo). */
    private fun restoreImage(base64: String): String? = runCatching {
        images.restore(Base64.getDecoder().decode(base64))
    }.getOrNull()
}
