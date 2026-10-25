package com.naniger.arzikina.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v35 → v36 : module « Boutique » — création de `shops`, `product_categories`, `products`,
 * `stock_movements`, `sales`, `sale_lines` et `sale_payments` (voir les entités du même nom dans
 * `data/local/entity`).
 *
 * Migration purement ADDITIVE : sept tables neuves, aucune table existante modifiée, aucun backfill
 * (aucune donnée préexistante n'y correspond). Aucune `FOREIGN KEY` (voir la doc de `ShopEntity`).
 * Le type de compte `BOUTIQUE` n'exige AUCUNE migration : `accounts.type` stocke le nom de l'enum
 * en texte libre.
 *
 * Équivalent serveur : `database/migrations/013_shop.sql`.
 */
val MIGRATION_35_36 = object : Migration(startVersion = 35, endVersion = 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `shops` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`accountId` INTEGER, " +
                "`currencyCode` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`syncId` TEXT, " +
                "`deletedAt` INTEGER, " +
                "`version` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shops_userId` ON `shops` (`userId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_shops_syncId` ON `shops` (`syncId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `product_categories` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`shopId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`syncId` TEXT, " +
                "`deletedAt` INTEGER, " +
                "`version` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_categories_userId` ON `product_categories` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_product_categories_shopId` ON `product_categories` (`shopId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_product_categories_syncId` ON `product_categories` (`syncId`)"
        )

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `products` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`shopId` INTEGER NOT NULL, " +
                "`categoryId` INTEGER, " +
                "`name` TEXT NOT NULL, " +
                "`description` TEXT, " +
                "`sku` TEXT, " +
                "`barcode` TEXT, " +
                "`unit` TEXT NOT NULL, " +
                "`purchasePriceMinor` INTEGER, " +
                "`salePriceMinor` INTEGER NOT NULL, " +
                "`minStockMilli` INTEGER NOT NULL, " +
                "`stockMilli` INTEGER NOT NULL, " +
                "`expiryDate` INTEGER, " +
                "`imageFileName` TEXT, " +
                "`isArchived` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`syncId` TEXT, " +
                "`deletedAt` INTEGER, " +
                "`version` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_userId` ON `products` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_shopId_name` ON `products` (`shopId`, `name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_shopId_barcode` ON `products` (`shopId`, `barcode`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_shopId_sku` ON `products` (`shopId`, `sku`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_products_categoryId` ON `products` (`categoryId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_syncId` ON `products` (`syncId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `stock_movements` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`productId` INTEGER NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`deltaMilli` INTEGER NOT NULL, " +
                "`unitCostMinor` INTEGER, " +
                "`reason` TEXT, " +
                "`reference` TEXT, " +
                "`saleId` INTEGER, " +
                "`occurredAt` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`syncId` TEXT, " +
                "`deletedAt` INTEGER, " +
                "`version` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_userId` ON `stock_movements` (`userId`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_stock_movements_productId_occurredAt` " +
                "ON `stock_movements` (`productId`, `occurredAt`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_saleId` ON `stock_movements` (`saleId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_stock_movements_syncId` ON `stock_movements` (`syncId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sales` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`shopId` INTEGER NOT NULL, " +
                "`reference` TEXT NOT NULL, " +
                "`soldAt` INTEGER NOT NULL, " +
                "`currencyCode` TEXT NOT NULL, " +
                "`customerName` TEXT, " +
                "`customerPhone` TEXT, " +
                "`subtotalMinor` INTEGER NOT NULL, " +
                "`discountMinor` INTEGER NOT NULL, " +
                "`totalMinor` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`cancelledAt` INTEGER, " +
                "`cancelReason` TEXT, " +
                "`issuedReceiptId` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`syncId` TEXT, " +
                "`deletedAt` INTEGER, " +
                "`version` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_userId` ON `sales` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shopId_soldAt` ON `sales` (`shopId`, `soldAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shopId_reference` ON `sales` (`shopId`, `reference`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sales_syncId` ON `sales` (`syncId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sale_lines` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`saleId` INTEGER NOT NULL, " +
                "`productId` INTEGER NOT NULL, " +
                "`productName` TEXT NOT NULL, " +
                "`unitPriceMinor` INTEGER NOT NULL, " +
                "`unitCostMinor` INTEGER, " +
                "`quantityMilli` INTEGER NOT NULL, " +
                "`lineTotalMinor` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`syncId` TEXT, " +
                "`deletedAt` INTEGER, " +
                "`version` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_lines_userId` ON `sale_lines` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_lines_saleId` ON `sale_lines` (`saleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_lines_productId` ON `sale_lines` (`productId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sale_lines_syncId` ON `sale_lines` (`syncId`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sale_payments` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`userId` INTEGER NOT NULL, " +
                "`saleId` INTEGER NOT NULL, " +
                "`accountId` INTEGER NOT NULL, " +
                "`method` TEXT NOT NULL, " +
                "`amountMinor` INTEGER NOT NULL, " +
                "`paidAt` INTEGER NOT NULL, " +
                "`transactionId` INTEGER, " +
                "`reversalTransactionId` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "`syncId` TEXT, " +
                "`deletedAt` INTEGER, " +
                "`version` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_payments_userId` ON `sale_payments` (`userId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_payments_saleId` ON `sale_payments` (`saleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_payments_accountId` ON `sale_payments` (`accountId`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_sale_payments_transactionId` ON `sale_payments` (`transactionId`)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sale_payments_syncId` ON `sale_payments` (`syncId`)")
    }
}
