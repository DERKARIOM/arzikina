package com.naniger.arzikina.presentation.shop.stats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentShopStatsBinding
import com.naniger.arzikina.databinding.ItemShopInfoRowBinding
import com.naniger.arzikina.domain.shop.ProductQuantityFormat
import com.naniger.arzikina.domain.shop.ProductRanking
import com.naniger.arzikina.domain.shop.SalePeriod
import com.naniger.arzikina.presentation.shop.ShopFormat
import com.naniger.arzikina.util.AppDateFormats
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Statistiques commerciales : indicateurs de la période, évolution du CA par jour, classement des produits. */
@AndroidEntryPoint
class ShopStatsFragment : Fragment(R.layout.fragment_shop_stats) {

    private val viewModel: ShopStatsViewModel by viewModels()
    private var binding: FragmentShopStatsBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentShopStatsBinding.bind(view)
        binding = b
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.periodChips.setOnCheckedStateChangeListener { _, ids ->
            when (ids.firstOrNull()) {
                R.id.chipPeriodToday -> viewModel.onPeriodChange(SalePeriod.TODAY)
                R.id.chipPeriod7 -> viewModel.onPeriodChange(SalePeriod.LAST_7_DAYS)
                R.id.chipPeriod30 -> viewModel.onPeriodChange(SalePeriod.LAST_30_DAYS)
            }
        }
        b.rankingChips.setOnCheckedStateChangeListener { _, ids ->
            when (ids.firstOrNull()) {
                R.id.chipBest -> viewModel.onRankingChange(ProductRanking.BEST_SELLING)
                R.id.chipLeast -> viewModel.onRankingChange(ProductRanking.LEAST_SOLD)
                R.id.chipProfit -> viewModel.onRankingChange(ProductRanking.MOST_PROFITABLE)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun render(state: StatsUiState) {
        val b = binding ?: return
        val loaded = state as? StatsUiState.Loaded
        b.loading.visibility = if (state is StatsUiState.Loading) View.VISIBLE else View.GONE
        b.errorText.visibility = if (state is StatsUiState.Error) View.VISIBLE else View.GONE
        b.content.visibility = if (loaded != null) View.VISIBLE else View.INVISIBLE
        loaded ?: return

        val currency = loaded.currencyCode
        val s = loaded.summary
        fun money(v: Long) = ShopFormat.money(v, currency)

        b.revenueRow.rowLabel.setText(R.string.shop_stats_revenue)
        b.revenueRow.rowValue.text = money(s.revenueMinor)
        b.countRow.rowLabel.setText(R.string.shop_stats_sales_count)
        b.countRow.rowValue.text = s.salesCount.toString()
        b.basketRow.rowLabel.setText(R.string.shop_stats_average_basket)
        b.basketRow.rowValue.text = s.averageBasketMinor?.let(::money) ?: "—"
        b.collectedRow.rowLabel.setText(R.string.shop_stats_collected)
        b.collectedRow.rowValue.text = money(s.collectedMinor)
        b.dueRow.rowLabel.setText(R.string.shop_stats_remaining_due)
        b.dueRow.rowValue.text = money(s.remainingDueMinor)
        b.cancelledRow.rowLabel.setText(R.string.shop_stats_cancelled)
        b.cancelledRow.rowValue.text = getString(R.string.shop_stats_cancelled_value, s.cancelledCount, money(s.cancelledMinor))

        renderChart(b, loaded)
        renderProducts(b, loaded)
    }

    private fun renderChart(b: FragmentShopStatsBinding, loaded: StatsUiState.Loaded) {
        val noSales = loaded.summary.salesCount == 0
        b.bars.visibility = if (noSales) View.GONE else View.VISIBLE
        b.barsEmpty.visibility = if (noSales) View.VISIBLE else View.GONE
        val daily = loaded.daily
        b.barsFrom.visibility = if (noSales || daily.size < 2) View.GONE else View.VISIBLE
        b.barsTo.visibility = b.barsFrom.visibility
        if (noSales) return
        b.bars.setValues(daily.map { it.revenueMinor })
        b.bars.contentDescription = getString(R.string.shop_stats_chart_description, daily.size)
        val formatter = AppDateFormats.mediumDate(requireContext())
        b.barsFrom.text = LocalDate.ofEpochDay(daily.first().dayKey).format(formatter)
        b.barsTo.text = LocalDate.ofEpochDay(daily.last().dayKey).format(formatter)
    }

    private fun renderProducts(b: FragmentShopStatsBinding, loaded: StatsUiState.Loaded) {
        val currency = loaded.currencyCode
        val ranking = loaded.criteria.ranking
        val rows = b.productRows
        rows.removeAllViews()
        loaded.products.forEach { p ->
            val row = ItemShopInfoRowBinding.inflate(LayoutInflater.from(requireContext()), rows, false)
            row.rowLabel.text = p.name
            row.rowValue.text = when (ranking) {
                ProductRanking.MOST_PROFITABLE -> ShopFormat.money(p.grossMarginMinor ?: 0L, currency)
                else -> "${ProductQuantityFormat.format(p.quantityMilli)} · ${ShopFormat.money(p.revenueMinor, currency)}"
            }
            rows.addView(row.root)
        }
        val empty = loaded.products.isEmpty()
        b.productsEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        b.productsEmpty.setText(
            if (ranking == ProductRanking.MOST_PROFITABLE) R.string.shop_stats_profit_empty else R.string.shop_stats_products_empty
        )
        val note = when (ranking) {
            ProductRanking.MOST_PROFITABLE -> R.string.shop_stats_profit_note
            ProductRanking.LEAST_SOLD -> R.string.shop_stats_least_note
            ProductRanking.BEST_SELLING -> null
        }
        b.productsNote.visibility = if (note == null || empty) View.GONE else View.VISIBLE
        note?.let { b.productsNote.setText(it) }
    }
}
