package com.naniger.arzikina.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ligne d'une vente : INSTANTANÉ du produit au moment de la vente. [productName], [unitPriceMinor] et
 * [unitCostMinor] sont copiés : modifier ou archiver le produit ensuite ne change ni l'historique, ni
 * le reçu, ni la marge calculée. [unitCostMinor] est `null` si le prix d'achat n'était pas renseigné.
 *
 * [quantityMilli] en milli-unités ; [lineTotalMinor] = `ReceiptCalculator.lineTotalOrNull` (même
 * arrondi que le reçu), stocké pour ne jamais être recalculé différemment plus tard.
 *
 * Immuable après création. Voir [ShopEntity] : pas de `ForeignKey`.
 */
@Entity(
    tableName = "sale_lines",
    indices = [
        Index("userId"),
        Index("saleId"),
        Index("productId"),
        Index(value = ["syncId"], unique = true)
    ]
)
data class SaleLineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val saleId: Long,
    val productId: Long,
    val productName: String,
    val unitPriceMinor: Long,
    val unitCostMinor: Long?,
    val quantityMilli: Long,
    val lineTotalMinor: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val syncId: String? = null,
    val deletedAt: Long? = null,
    val version: Int = 1
)
