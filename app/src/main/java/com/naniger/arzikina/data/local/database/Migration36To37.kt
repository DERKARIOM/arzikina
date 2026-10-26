package com.naniger.arzikina.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v36 → v37 : panier de vente persistant — création de `cart_drafts` et `cart_lines` (voir
 * [com.naniger.arzikina.data.local.entity.CartDraftEntity] et
 * [com.naniger.arzikina.data.local.entity.CartLineEntity]).
 *
 * Migration purement ADDITIVE : deux tables neuves, aucune table existante modifiée, aucun backfill.
 * Aucune `FOREIGN KEY` (voir la doc de `ShopEntity`). Données locales uniquement : pas d'équivalent serveur.
 */
val MIGRATION_36_37 = object : Migration(startVersion = 36, endVersion = 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cart_drafts` (" +
                "`userId` INTEGER NOT NULL, " +
                "`shopId` INTEGER NOT NULL, " +
                "`draftKey` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`userId`, `shopId`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cart_lines` (" +
                "`userId` INTEGER NOT NULL, " +
                "`shopId` INTEGER NOT NULL, " +
                "`productId` INTEGER NOT NULL, " +
                "`quantityMilli` INTEGER NOT NULL, " +
                "`position` INTEGER NOT NULL, " +
                "PRIMARY KEY(`userId`, `shopId`, `productId`))"
        )
    }
}
