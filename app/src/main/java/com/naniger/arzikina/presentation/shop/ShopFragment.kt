package com.naniger.arzikina.presentation.shop

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import com.naniger.arzikina.R
import com.naniger.arzikina.presentation.shop.products.ShopProductsFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Onglet Boutique : la page « Produits » (ce que l'on consulte le plus) occupe tout l'écran. Le tableau
 * de bord ([ShopDashboardFragment]) est une destination à part, ouverte par l'icône de la barre d'outils.
 *
 * Ce conteneur garantit l'existence de la boutique (création idempotente) quelle que soit la page ouverte
 * en premier ; aucune autre logique métier ici.
 */
@AndroidEntryPoint
class ShopFragment : Fragment(R.layout.fragment_shop_home) {

    private val viewModel: ShopViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.shopPageContainer, ShopProductsFragment(), TAG_PRODUCTS)
            }
        }
        viewModel.ensureShop(getString(R.string.shop_default_name))
    }

    private companion object {
        const val TAG_PRODUCTS = "shop_page_products"
    }
}
