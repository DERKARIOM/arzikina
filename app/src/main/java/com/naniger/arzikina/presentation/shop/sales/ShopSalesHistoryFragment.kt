package com.naniger.arzikina.presentation.shop.sales

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentShopSalesHistoryBinding
import com.naniger.arzikina.domain.shop.SalePeriod
import com.naniger.arzikina.domain.shop.SaleSort
import com.naniger.arzikina.domain.shop.SaleStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Historique des ventes : recherche, période, statut, tri ; un clic ouvre le détail (reçu, annulation). */
@AndroidEntryPoint
class ShopSalesHistoryFragment : Fragment(R.layout.fragment_shop_sales_history) {

    private val viewModel: ShopSalesHistoryViewModel by viewModels()
    private var binding: FragmentShopSalesHistoryBinding? = null
    private val adapter = ShopSalesAdapter { findNavController().navigate(R.id.shopSaleDetailFragment, bundleOf("saleId" to it.id)) }

    private val chipToPeriod by lazy {
        mapOf(
            R.id.chipPeriodAll to SalePeriod.ALL,
            R.id.chipPeriodToday to SalePeriod.TODAY,
            R.id.chipPeriod7 to SalePeriod.LAST_7_DAYS,
            R.id.chipPeriod30 to SalePeriod.LAST_30_DAYS
        )
    }
    private val chipToStatus by lazy {
        mapOf(
            R.id.chipStatusAll to null,
            R.id.chipStatusConfirmed to SaleStatus.CONFIRMED,
            R.id.chipStatusCancelled to SaleStatus.CANCELLED
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentShopSalesHistoryBinding.bind(view)
        binding = b

        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.toolbar.setOnMenuItemClickListener(::onMenuItem)
        b.salesList.layoutManager = LinearLayoutManager(requireContext())
        b.salesList.adapter = adapter
        b.salesList.itemAnimator = null
        b.emptyNewSaleButton.setOnClickListener { findNavController().navigate(R.id.saleCartFragment) }
        b.searchInput.doAfterTextChanged { viewModel.onQueryChange(it?.toString().orEmpty()) }
        b.periodChips.setOnCheckedStateChangeListener { _, ids ->
            ids.firstOrNull()?.let { id -> chipToPeriod[id]?.let(viewModel::onPeriodChange) }
        }
        b.statusChips.setOnCheckedStateChangeListener { _, ids ->
            ids.firstOrNull()?.let { id -> viewModel.onStatusChange(chipToStatus[id]) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.criteria.collect(::renderCriteria) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding?.salesList?.adapter = null
        binding = null
    }

    private fun onMenuItem(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_sales_sort -> true.also { showSortDialog() }
        else -> false
    }

    private fun showSortDialog() {
        val labels = arrayOf(getString(R.string.shop_history_sort_date), getString(R.string.shop_history_sort_amount))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.shop_menu_sort)
            .setSingleChoiceItems(labels, viewModel.criteria.value.sort.ordinal) { dialog, which ->
                viewModel.onSortChange(SaleSort.entries[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun renderCriteria(criteria: SalesHistoryCriteria) {
        val b = binding ?: return
        val periodChip = chipToPeriod.entries.first { it.value == criteria.period }.key
        if (b.periodChips.checkedChipId != periodChip) b.periodChips.check(periodChip)
        val statusChip = chipToStatus.entries.first { it.value == criteria.status }.key
        if (b.statusChips.checkedChipId != statusChip) b.statusChips.check(statusChip)
    }

    private fun render(state: SalesHistoryUiState) {
        val b = binding ?: return
        val loaded = state as? SalesHistoryUiState.Loaded
        val items = loaded?.items.orEmpty()
        val nothingAtAll = loaded != null && items.isEmpty() && !loaded.filtersActive
        val noResults = loaded != null && items.isEmpty() && loaded.filtersActive

        adapter.submit(items, loaded?.accountsById.orEmpty())
        b.loading.visibility = if (state is SalesHistoryUiState.Loading) View.VISIBLE else View.GONE
        b.errorText.visibility = if (state is SalesHistoryUiState.Error) View.VISIBLE else View.GONE
        b.salesList.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
        // Même écran vide pour « aucune vente » et « aucun résultat », avec un texte adapté.
        b.emptyState.visibility = if (nothingAtAll || noResults) View.VISIBLE else View.GONE
        b.emptyTitle.setText(if (noResults) R.string.shop_history_no_results_title else R.string.shop_history_empty_title)
        b.emptyMessage.setText(if (noResults) R.string.shop_history_no_results_message else R.string.shop_history_empty_message)
        b.emptyNewSaleButton.visibility = if (nothingAtAll) View.VISIBLE else View.GONE
        // Rien à chercher ni à filtrer tant qu'aucune vente n'existe.
        b.searchCard.visibility = if (nothingAtAll) View.GONE else View.VISIBLE
        b.chipsContainer.visibility = if (nothingAtAll) View.GONE else View.VISIBLE
    }
}
