package com.naniger.arzikina.presentation.shop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentShopBinding
import com.naniger.arzikina.databinding.ItemShopInfoRowBinding
import com.naniger.arzikina.databinding.ItemShopStatBinding
import com.naniger.arzikina.domain.shop.ProductAvailability
import com.naniger.arzikina.domain.shop.ProductQuantityFormat
import com.naniger.arzikina.domain.shop.SalePeriod
import com.naniger.arzikina.presentation.shop.products.ShopProductAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Page « Tableau de bord » de l'onglet Boutique (hébergée par [ShopFragment]). Les indicateurs viennent de requêtes d'agrégation SQL (aucun calcul
 * sur la liste en mémoire). Les valeurs absentes sont affichées comme telles (« — »), jamais inventées.
 */
@AndroidEntryPoint
class ShopDashboardFragment : Fragment(R.layout.fragment_shop) {

    private val viewModel: ShopViewModel by viewModels()
    private var binding: FragmentShopBinding? = null

    private val alertsAdapter = ShopProductAdapter { openProduct(it.id) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentShopBinding.bind(view)
        binding = b

        b.alertsList.layoutManager = LinearLayoutManager(requireContext())
        b.alertsList.adapter = alertsAdapter
        b.alertsList.itemAnimator = null

        b.statTotal.statLabel.setText(R.string.shop_stat_total)
        b.statInStock.statLabel.setText(R.string.shop_stat_in_stock)
        b.statLow.statLabel.setText(R.string.shop_stat_low)
        b.statOut.statLabel.setText(R.string.shop_stat_out)
        b.valueCostRow.rowLabel.setText(R.string.shop_stock_value_cost)
        b.valueSaleRow.rowLabel.setText(R.string.shop_stock_value_sale)

        b.newSaleButton.setOnClickListener { findNavController().navigate(R.id.saleCartFragment) }
        b.statsButton.setOnClickListener { findNavController().navigate(R.id.shopStatsFragment) }
        b.periodChips.setOnCheckedStateChangeListener { _, ids ->
            when (ids.firstOrNull()) {
                R.id.chipPeriodToday -> viewModel.onPeriodSelected(SalePeriod.TODAY)
                R.id.chipPeriod7 -> viewModel.onPeriodSelected(SalePeriod.LAST_7_DAYS)
                R.id.chipPeriod30 -> viewModel.onPeriodSelected(SalePeriod.LAST_30_DAYS)
            }
        }
        b.salesHistoryButton.setOnClickListener { findNavController().navigate(R.id.shopSalesHistoryFragment) }
        b.addProductButton.setOnClickListener { findNavController().navigate(R.id.shopProductFormFragment) }
        b.productsButton.setOnClickListener { openProducts(ProductAvailability.ALL) }
        b.alertsButton.setOnClickListener { openProducts(ProductAvailability.LOW) }

        viewModel.ensureShop(getString(R.string.shop_default_name))
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshClock()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding?.alertsList?.adapter = null
        binding = null
    }

    private fun openProducts(availability: ProductAvailability) =
        findNavController().navigate(R.id.shopProductsFragment, bundleOf("availability" to availability.code))

    private fun openProduct(id: Long) =
        findNavController().navigate(R.id.shopProductDetailFragment, bundleOf("productId" to id))

    private fun render(state: ShopDashboardState) {
        val b = binding ?: return
        val ready = !state.isLoading && !state.hasError
        b.loading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        b.errorText.visibility = if (state.hasError) View.VISIBLE else View.GONE
        b.content.visibility = if (ready) View.VISIBLE else View.GONE
        if (!ready) return

        val stats = state.stats
        b.statTotal.showCount(stats?.totalProducts, R.color.shop_stock_normal, tinted = false)
        b.statInStock.showCount(stats?.inStock, R.color.shop_stock_normal, tinted = true)
        b.statLow.showCount(stats?.lowStock, R.color.shop_stock_low, tinted = (stats?.lowStock ?: 0) > 0)
        b.statOut.showCount(stats?.outOfStock, R.color.shop_stock_out, tinted = (stats?.outOfStock ?: 0) > 0)
        renderStockValue(b, state)
        renderSales(b, state)

        val alertCount = (stats?.lowStock ?: 0) + (stats?.outOfStock ?: 0)
        b.alertsButton.text = getString(R.string.shop_action_alerts, alertCount)
        b.alertsButton.isEnabled = alertCount > 0
        alertsAdapter.submit(state.alerts, state.currencyCode)
        b.alertsEmpty.visibility = if (state.alerts.isEmpty()) View.VISIBLE else View.GONE
        b.alertsList.visibility = if (state.alerts.isEmpty()) View.GONE else View.VISIBLE
    }

    /** Carte « Ventes » : valeurs réelles ou état vide explicite (jamais un chiffre inventé). */
    private fun renderSales(b: FragmentShopBinding, state: ShopDashboardState) {
        val sales = state.sales
        val currency = state.currencyCode
        val hasAnySale = sales.recent.isNotEmpty()

        b.todayCountValue.text = sales.today.salesCount.toString()
        b.todayRevenueValue.text = ShopFormat.money(sales.today.revenueMinor, currency)
        b.periodRevenueRow.rowLabel.setText(R.string.shop_dash_revenue_period)
        b.periodRevenueRow.rowValue.text = ShopFormat.money(sales.periodSummary.revenueMinor, currency)
        b.periodCountRow.rowLabel.setText(R.string.shop_dash_sales_period)
        b.periodCountRow.rowValue.text = sales.periodSummary.salesCount.toString()
        b.periodBasketRow.rowLabel.setText(R.string.shop_stats_average_basket)
        b.periodBasketRow.rowValue.text = sales.periodSummary.averageBasketMinor?.let { ShopFormat.money(it, currency) } ?: EMPTY_VALUE

        val checked = when (sales.period) {
            SalePeriod.TODAY -> R.id.chipPeriodToday
            SalePeriod.LAST_30_DAYS -> R.id.chipPeriod30
            else -> R.id.chipPeriod7
        }
        if (b.periodChips.checkedChipId != checked) b.periodChips.check(checked)

        b.salesEmpty.visibility = if (hasAnySale) View.GONE else View.VISIBLE
        b.recentTitle.visibility = if (hasAnySale) View.VISIBLE else View.GONE
        b.recentRows.removeAllViews()
        sales.recent.forEach { sale ->
            val row = addRow(b.recentRows, sale.reference, ShopFormat.money(sale.totalMinor, currency))
            if (sale.status == com.naniger.arzikina.domain.shop.SaleStatus.CANCELLED) {
                row.rowValue.paintFlags = row.rowValue.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            }
            row.root.setOnClickListener {
                findNavController().navigate(R.id.shopSaleDetailFragment, bundleOf("saleId" to sale.id))
            }
        }

        b.topTitle.visibility = if (hasAnySale) View.VISIBLE else View.GONE
        b.topEmpty.visibility = if (hasAnySale && sales.topProducts.isEmpty()) View.VISIBLE else View.GONE
        b.topRows.removeAllViews()
        sales.topProducts.forEach { product ->
            addRow(b.topRows, product.name, ProductQuantityFormat.format(product.quantityMilli))
        }
    }

    private fun addRow(container: LinearLayout, label: String, value: String): ItemShopInfoRowBinding {
        val row = ItemShopInfoRowBinding.inflate(LayoutInflater.from(requireContext()), container, false)
        row.rowLabel.text = label
        row.rowValue.text = value
        container.addView(row.root)
        return row
    }

    private fun renderStockValue(b: FragmentShopBinding, state: ShopDashboardState) {
        val stats = state.stats
        val noStock = stats == null || (stats.inStock == 0)
        val cost = stats?.stockValueAtCostMinor
        b.valueCostRow.rowValue.text = when {
            noStock -> EMPTY_VALUE
            cost == null -> EMPTY_VALUE
            else -> ShopFormat.money(cost, state.currencyCode)
        }
        b.valueSaleRow.rowValue.text =
            if (noStock) EMPTY_VALUE else ShopFormat.money(stats?.stockValueAtSaleMinor ?: 0L, state.currencyCode)

        val note = when {
            noStock -> getString(R.string.shop_stock_value_empty)
            cost == null -> getString(R.string.shop_stock_value_cost_missing)
            (stats?.productsWithoutCost ?: 0) > 0 ->
                getString(R.string.shop_stock_value_cost_partial, stats?.productsWithoutCost ?: 0)
            else -> null
        }
        b.valueCostNote.text = note
        b.valueCostNote.visibility = if (note == null) View.GONE else View.VISIBLE
    }

    /** Affiche [count] (ou « — » s'il est inconnu) ; [tinted] colore la valeur de l'état correspondant. */
    private fun ItemShopStatBinding.showCount(count: Int?, colorRes: Int, tinted: Boolean) {
        statValue.text = count?.toString() ?: EMPTY_VALUE
        val color = if (tinted) colorRes else R.color.arzikina_on_balance_card
        statValue.setTextColor(ContextCompat.getColor(requireContext(), color))
    }

    private companion object {
        const val EMPTY_VALUE = "—"
    }
}
