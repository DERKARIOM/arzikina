package com.naniger.arzikina.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Produit vendu par une boutique.
 *
 * - [unit] : NOM de `ProductUnit` (texte, valeur inconnue → `OTHER` au mapping).
 * - [purchasePriceMinor] : prix d'achat unitaire, `null` s'il n'est pas renseigné (le bénéfice et la
 *   valeur d'achat du stock sont alors indisponibles, jamais inventés). [salePriceMinor] : prix de
 *   vente courant ; une vente copie ce prix dans sa ligne, modifier ce champ ne touche jamais
 *   l'historique.
 * - [minStockMilli] : seuil d'alerte ; `0` = aucune alerte de stock faible.
 * - [stockMilli] : quantité disponible en milli-unités, CACHE dénormalisé de la somme des
 *   `stock_movements` du produit, mis à jour dans la MÊME transaction Room que chaque mouvement
 *   (voir `StockRules`). Jamais modifié autrement, JAMAIS synchronisé : chaque appareil le
 *   recalcule à partir des mouvements reçus.
 * - [imageFileName] : nom d'un fichier LOCAL (V1 : l'image n'est pas synchronisée).
 * - [isArchived] : un produit n'est jamais supprimé (ses ventes passées le référencent) ; il est
 *   archivé, donc masqué des ventes mais conservé dans l'historique.
 *
 * Index NON uniques sur [barcode] et [sku] : une ligne reçue de la synchronisation peut dupliquer
 * un code-barres créé hors ligne sur un autre appareil, et un index unique ferait échouer son
 * insertion. L'anti-doublon est appliqué par le repository (recherche avant création).
 *
 * Voir [ShopEntity] : pas de `ForeignKey`. Synchronisation : gabarit commun.
 */
@Entity(
    tableName = "products",
    indices = [
        Index("userId"),
        Index(value = ["shopId", "name"]),
        Index(value = ["shopId", "barcode"]),
        Index(value = ["shopId", "sku"]),
        Index("categoryId"),
        Index(value = ["syncId"], unique = true)
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val userId: Long,
    val shopId: Long,
    val categoryId: Long?,
    val name: String,
    val description: String?,
    val sku: String?,
    val barcode: String?,
    val unit: String,
    val purchasePriceMinor: Long?,
    val salePriceMinor: Long,
    val minStockMilli: Long,
    val stockMilli: Long,
    val expiryDate: Long?,
    val imageFileName: String?,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val syncId: String? = null,
    val deletedAt: Long? = null,
    val version: Int = 1
)
