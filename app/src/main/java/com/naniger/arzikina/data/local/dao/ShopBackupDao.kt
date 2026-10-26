package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.naniger.arzikina.data.local.entity.ProductCategoryEntity
import com.naniger.arzikina.data.local.entity.ProductEntity
import com.naniger.arzikina.data.local.entity.SaleEntity
import com.naniger.arzikina.data.local.entity.SaleLineEntity
import com.naniger.arzikina.data.local.entity.SalePaymentEntity
import com.naniger.arzikina.data.local.entity.ShopEntity
import com.naniger.arzikina.data.local.entity.StockMovementEntity

/**
 * Lectures et insertions en bloc des tables Boutique pour la sauvegarde / restauration (voir
 * `ShopBackupHandler`). Dédié à cet usage pour ne pas alourdir les DAO métier. Filtrage par `userId` et
 * lignes supprimées (`deletedAt`) exclues, comme partout ailleurs. Les `insert*` renvoient les ids générés
 * dans l'ordre de la liste (garantie Room) : ils alimentent les tables de correspondance ancien → nouvel id.
 */
@Dao
interface ShopBackupDao {

    @Query("SELECT * FROM shops WHERE userId = :userId AND deletedAt IS NULL ORDER BY id")
    suspend fun shops(userId: Long): List<ShopEntity>

    @Query("SELECT * FROM product_categories WHERE userId = :userId AND deletedAt IS NULL ORDER BY id")
    suspend fun productCategories(userId: Long): List<ProductCategoryEntity>

    @Query("SELECT * FROM products WHERE userId = :userId AND deletedAt IS NULL ORDER BY id")
    suspend fun products(userId: Long): List<ProductEntity>

    @Query("SELECT * FROM stock_movements WHERE userId = :userId AND deletedAt IS NULL ORDER BY id")
    suspend fun stockMovements(userId: Long): List<StockMovementEntity>

    @Query("SELECT * FROM sales WHERE userId = :userId AND deletedAt IS NULL ORDER BY id")
    suspend fun sales(userId: Long): List<SaleEntity>

    @Query("SELECT * FROM sale_lines WHERE userId = :userId AND deletedAt IS NULL ORDER BY id")
    suspend fun saleLines(userId: Long): List<SaleLineEntity>

    @Query("SELECT * FROM sale_payments WHERE userId = :userId AND deletedAt IS NULL ORDER BY id")
    suspend fun salePayments(userId: Long): List<SalePaymentEntity>

    @Query("SELECT imageFileName FROM products WHERE userId = :userId AND imageFileName IS NOT NULL")
    suspend fun imageFileNames(userId: Long): List<String>

    @Insert
    suspend fun insertShops(items: List<ShopEntity>): List<Long>

    @Insert
    suspend fun insertProductCategories(items: List<ProductCategoryEntity>): List<Long>

    @Insert
    suspend fun insertProducts(items: List<ProductEntity>): List<Long>

    @Insert
    suspend fun insertStockMovements(items: List<StockMovementEntity>): List<Long>

    @Insert
    suspend fun insertSales(items: List<SaleEntity>): List<Long>

    @Insert
    suspend fun insertSaleLines(items: List<SaleLineEntity>): List<Long>

    @Insert
    suspend fun insertSalePayments(items: List<SalePaymentEntity>): List<Long>
}
