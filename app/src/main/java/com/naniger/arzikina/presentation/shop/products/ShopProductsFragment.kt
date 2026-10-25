package com.naniger.arzikina.presentation.shop.products

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentShopProductsBinding
import com.naniger.arzikina.domain.shop.ProductAvailability
import com.naniger.arzikina.domain.shop.ProductFilter
import com.naniger.arzikina.domain.shop.ProductSort
import com.naniger.arzikina.domain.shop.ProductSummary
import com.naniger.arzikina.presentation.shop.sales.ShopPickResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Liste des produits de la boutique (voir [ShopProductsViewModel]). */
@AndroidEntryPoint
class ShopProductsFragment : Fragment(R.layout.fragment_shop_products) {

    private val viewModel: ShopProductsViewModel by viewModels()
    private var binding: FragmentShopProductsBinding? = null

    /** Mode « sélection » (ouvert depuis le panier de vente) : un clic renvoie le produit au lieu d'ouvrir sa fiche. */
    private val pickMode: Boolean get() = arguments?.getBoolean(ShopPickResult.ARG_PICK_MODE, false) ?: false

    /** Grille (photos) par défaut ; la bascule liste/grille survit à la rotation. */
    private var gridMode = true

    private val adapter = ShopProductAdapter(ProductDisplayMode.GRID) { if (pickMode) pick(it) else openProduct(it.id) }

    private val chipToAvailability = mapOf(
        R.id.chipAll to ProductAvailability.ALL,
        R.id.chipInStock to ProductAvailability.IN_STOCK,
        R.id.chipLow to ProductAvailability.LOW,
        R.id.chipOut to ProductAvailability.OUT_OF_STOCK
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentShopProductsBinding.bind(view)
        binding = b

        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.toolbar.setOnMenuItemClickListener(::onMenuItem)
        gridMode = savedInstanceState?.getBoolean(STATE_GRID_MODE, true) ?: true
        applyDisplayMode(b)
        b.productsList.itemAnimator = null
        b.addButton.setOnClickListener { openForm() }
        b.emptyAddButton.setOnClickListener { openForm() }
        if (pickMode) {
            // Sélection pour une vente : pas de création de produit ni de menu d'archives.
            b.toolbar.setTitle(R.string.shop_cart_pick_title)
            b.toolbar.menu.findItem(R.id.action_shop_archived)?.isVisible = false
        }
        b.searchInput.doAfterTextChanged { viewModel.onQueryChange(it?.toString().orEmpty()) }
        b.availabilityChips.setOnCheckedStateChangeListener { _, checkedIds ->
            checkedIds.firstOrNull()?.let { id -> chipToAvailability[id]?.let(viewModel::onAvailabilityChange) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.filter.collect(::renderFilter) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_GRID_MODE, gridMode)
    }

    /** Applique grille ou liste : les deux modes ont des ViewHolders différents, l'adapter est donc réassigné. */
    private fun applyDisplayMode(b: FragmentShopProductsBinding) {
        adapter.mode = if (gridMode) ProductDisplayMode.GRID else ProductDisplayMode.LIST
        b.productsList.layoutManager =
            if (gridMode) GridLayoutManager(requireContext(), GRID_SPAN_COUNT) else LinearLayoutManager(requireContext())
        b.productsList.adapter = adapter
        val side = if (gridMode) resources.getDimensionPixelSize(R.dimen.spacing_s) else 0
        b.productsList.setPadding(side, b.productsList.paddingTop, side, b.productsList.paddingBottom)
        b.toolbar.menu.findItem(R.id.action_shop_layout)?.apply {
            setIcon(if (gridMode) R.drawable.ic_view_list_24 else R.drawable.ic_grid_view_24)
            setTitle(if (gridMode) R.string.shop_menu_layout_list else R.string.shop_menu_layout_grid)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding?.productsList?.adapter = null
        binding = null
    }

    private fun openForm() = findNavController().navigate(R.id.shopProductFormFragment)

    /** Renvoie le produit choisi au panier ; un produit en rupture n'est pas sélectionnable. */
    private fun pick(product: ProductSummary) {
        if (product.stockMilli <= 0L) {
            binding?.root?.let { Snackbar.make(it, R.string.shop_cart_err_out_of_stock, Snackbar.LENGTH_SHORT).show() }
            return
        }
        setFragmentResult(ShopPickResult.REQUEST_KEY, bundleOf(ShopPickResult.KEY_PRODUCT_ID to product.id))
        findNavController().navigateUp()
    }

    private fun openProduct(id: Long) =
        findNavController().navigate(R.id.shopProductDetailFragment, bundleOf("productId" to id))

    private fun onMenuItem(item: android.view.MenuItem): Boolean = when (item.itemId) {
        R.id.action_shop_layout -> true.also {
            gridMode = !gridMode
            binding?.let(::applyDisplayMode)
        }
        R.id.action_shop_sort -> true.also { showSortDialog() }
        R.id.action_shop_category -> true.also { showCategoryDialog() }
        R.id.action_shop_archived -> true.also { viewModel.onArchivedChange(!item.isChecked) }
        else -> false
    }

    private fun showSortDialog() {
        val sorts = ProductSort.entries
        val labels = sorts.map { getString(sortLabel(it)) }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.shop_menu_sort)
            .setSingleChoiceItems(labels, sorts.indexOf(viewModel.filter.value.sort)) { dialog, which ->
                viewModel.onSortChange(sorts[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showCategoryDialog() {
        val categories = viewModel.categories.value
        val labels = (listOf(getString(R.string.shop_category_all)) + categories.map { it.name }).toTypedArray()
        val selected = categories.indexOfFirst { it.id == viewModel.filter.value.categoryId } + 1
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.shop_menu_category)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                viewModel.onCategoryChange(categories.getOrNull(which - 1)?.id)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun sortLabel(sort: ProductSort): Int = when (sort) {
        ProductSort.NAME -> R.string.shop_sort_name
        ProductSort.PRICE -> R.string.shop_sort_price
        ProductSort.QUANTITY -> R.string.shop_sort_quantity
        ProductSort.NEWEST -> R.string.shop_sort_newest
    }

    private fun renderFilter(filter: ProductFilter) {
        val b = binding ?: return
        val chipId = chipToAvailability.entries.first { it.value == filter.availability }.key
        if (b.availabilityChips.checkedChipId != chipId) b.availabilityChips.check(chipId)
        b.toolbar.menu.findItem(R.id.action_shop_archived)?.isChecked = filter.archived
    }

    private fun render(state: ProductListUiState) {
        val b = binding ?: return
        val loaded = state as? ProductListUiState.Loaded
        val items = loaded?.items.orEmpty()
        val nothingAtAll = loaded != null && items.isEmpty() && !loaded.filtersActive
        val noResults = loaded != null && items.isEmpty() && loaded.filtersActive

        adapter.submit(items, loaded?.currencyCode.orEmpty())
        b.loading.visibility = if (state is ProductListUiState.Loading) View.VISIBLE else View.GONE
        b.errorText.visibility = if (state is ProductListUiState.Error) View.VISIBLE else View.GONE
        b.emptyState.visibility = if (nothingAtAll) View.VISIBLE else View.GONE
        b.noResults.visibility = if (noResults) View.VISIBLE else View.GONE
        b.productsList.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
        b.addButton.visibility = if (nothingAtAll || pickMode) View.GONE else View.VISIBLE
        b.emptyAddButton.visibility = if (pickMode) View.GONE else View.VISIBLE
        // Pas de recherche ni de filtres tant que la boutique n'a aucun produit à chercher.
        b.searchCard.visibility = if (nothingAtAll) View.GONE else View.VISIBLE
        b.chipsScroll.visibility = if (nothingAtAll) View.GONE else View.VISIBLE
    }

    private companion object {
        const val STATE_GRID_MODE = "shop_products_grid_mode"
        const val GRID_SPAN_COUNT = 2
    }
}
