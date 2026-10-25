package com.naniger.arzikina.domain.shop

/**
 * Unité de vente d'un produit de la boutique.
 *
 * [allowsFraction] : `true` pour les unités mesurées (kilogramme, litre), où une vente de 0,5 est
 * légitime ; `false` pour les unités comptées (pièce, paquet, carton), où seule une quantité
 * ENTIÈRE a un sens. Les quantités sont toujours stockées en milli-unités (voir
 * [StockRules.QUANTITY_UNIT_MILLI]) : 1 pièce = 1000, 0,5 kg = 500.
 *
 * Stocké par NOM (texte) : une valeur inconnue venue d'un client plus récent retombe sur [OTHER]
 * via [fromStored] au lieu de faire échouer la lecture. Ne jamais renommer ni supprimer une entrée.
 */
enum class ProductUnit(val allowsFraction: Boolean) {
    PIECE(false),
    PACK(false),
    CARTON(false),
    KILOGRAM(true),
    LITER(true),
    OTHER(false);

    companion object {
        fun fromStored(value: String): ProductUnit = entries.firstOrNull { it.name == value } ?: OTHER
    }
}
