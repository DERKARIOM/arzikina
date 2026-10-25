package com.naniger.arzikina.presentation.shop.sales

/**
 * Contrat entre le panier et la liste des produits ouverte en « mode sélection » : la liste renvoie
 * l'identifiant du produit choisi via l'API de résultat de Fragment, puis se ferme.
 */
object ShopPickResult {
    const val REQUEST_KEY = "shop_pick_product"
    const val KEY_PRODUCT_ID = "productId"
    const val ARG_PICK_MODE = "pickMode"
}
