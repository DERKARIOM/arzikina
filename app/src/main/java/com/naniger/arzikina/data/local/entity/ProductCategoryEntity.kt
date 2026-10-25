package com.naniger.arzikina.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Catégorie de PRODUITS d'une boutique (Alimentation, Boissons…). Distincte de `CategoryEntity`
 * (catégories de transactions : revenus/dépenses) : propres à chaque boutique, sans type ni icône.
 *
 * Voir [ShopEntity] : pas de `ForeignKey`, référence [shopId] par id local.
 */
@Entity(
    tableName = "product_categories",
    indices = [Index("userId"), Index("shopId"), Index(value = ["syncId"], unique = true)]
)
data class ProductCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val shopId: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val syncId: String? = null,
    val deletedAt: Long? = null,
    val version: Int = 1
)
