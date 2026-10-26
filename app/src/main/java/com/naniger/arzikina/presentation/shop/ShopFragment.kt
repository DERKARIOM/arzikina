package com.naniger.arzikina.presentation.shop

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.google.android.material.tabs.TabLayout
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentShopHomeBinding
import com.naniger.arzikina.presentation.shop.products.ShopProductsFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Onglet Boutique : deux pages. « Produits » s'ouvre en premier (c'est ce que l'on consulte le plus),
 * « Tableau de bord » détaille l'activité (stock, ventes, statistiques, alertes).
 *
 * Les deux pages sont des fragments enfants ajoutés une seule fois puis affichés / masqués : la recherche,
 * les filtres et le défilement de la liste survivent au passage d'une page à l'autre. Aucune logique métier
 * ici : chaque page garde son propre ViewModel.
 */
@AndroidEntryPoint
class ShopFragment : Fragment(R.layout.fragment_shop_home) {

    private var selectedPage = PAGE_PRODUCTS

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentShopHomeBinding.bind(view)
        selectedPage = savedInstanceState?.getInt(STATE_PAGE, PAGE_PRODUCTS) ?: PAGE_PRODUCTS

        if (savedInstanceState == null) {
            val dashboard = ShopDashboardFragment()
            childFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.shopPageContainer, ShopProductsFragment(), TAG_PRODUCTS)
                add(R.id.shopPageContainer, dashboard, TAG_DASHBOARD)
                hide(dashboard)
            }
        }

        b.shopTabs.apply {
            addTab(newTab().setText(R.string.shop_tab_products))
            addTab(newTab().setText(R.string.shop_tab_dashboard))
            getTabAt(selectedPage)?.select()
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) = showPage(tab.position)
                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_PAGE, selectedPage)
    }

    private fun showPage(page: Int) {
        selectedPage = page
        val products = childFragmentManager.findFragmentByTag(TAG_PRODUCTS) ?: return
        val dashboard = childFragmentManager.findFragmentByTag(TAG_DASHBOARD) ?: return
        childFragmentManager.commit {
            setReorderingAllowed(true)
            if (page == PAGE_PRODUCTS) show(products).hide(dashboard) else show(dashboard).hide(products)
        }
    }

    private companion object {
        const val STATE_PAGE = "shop_home_page"
        const val PAGE_PRODUCTS = 0
        const val TAG_PRODUCTS = "shop_page_products"
        const val TAG_DASHBOARD = "shop_page_dashboard"
    }
}
