package com.naniger.arzikina.presentation.shop.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentShopProductDetailBinding
import com.naniger.arzikina.databinding.ItemShopInfoRowBinding
import com.naniger.arzikina.domain.shop.StockMovementPolicy
import com.naniger.arzikina.presentation.components.ConfirmDialogs
import com.naniger.arzikina.presentation.shop.ShopFormat
import com.naniger.arzikina.presentation.shop.labelRes
import com.naniger.arzikina.presentation.shop.showProductImage
import com.naniger.arzikina.presentation.shop.showStockStatus
import com.naniger.arzikina.util.AppDateFormats
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Fiche détaillée d'un produit. N'affiche que les informations renseignées (pas de lignes vides). */
@AndroidEntryPoint
class ShopProductDetailFragment : Fragment(R.layout.fragment_shop_product_detail) {

    private val viewModel: ShopProductDetailViewModel by viewModels()
    private var binding: FragmentShopProductDetailBinding? = null
    private var loaded: ProductDetailUiState.Loaded? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentShopProductDetailBinding.bind(view)
        binding = b

        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_shop_edit -> true.also { edit() }
                R.id.action_shop_archive_toggle -> true.also { toggleArchive() }
                else -> false
            }
        }
        b.editButton.setOnClickListener { edit() }
        b.stockButton.setOnClickListener { chooseStockMovement() }
        b.historyButton.setOnClickListener {
            findNavController().navigate(R.id.shopStockHistoryFragment, bundleOf("productId" to viewModel.productId))
        }
        b.archiveButton.setOnClickListener { toggleArchive() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch {
                    viewModel.events.collect { event ->
                        if (event is ProductDetailEvent.Message) {
                            binding?.root?.let { Snackbar.make(it, event.messageRes, Snackbar.LENGTH_SHORT).show() }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun edit() =
        findNavController().navigate(R.id.shopProductFormFragment, bundleOf("productId" to viewModel.productId))

    /** Choix du type de mouvement manuel, puis ouverture du formulaire correspondant. */
    private fun chooseStockMovement() {
        val types = StockMovementPolicy.MANUAL_TYPES
        val labels = types.map { getString(it.labelRes()) }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.shop_stock_move_choose)
            .setItems(labels) { _, which ->
                findNavController().navigate(
                    R.id.shopStockMovementFragment,
                    bundleOf("productId" to viewModel.productId, "type" to types[which].name)
                )
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun toggleArchive() {
        val product = loaded?.product ?: return
        if (product.isArchived) {
            viewModel.onSetArchived(false)
        } else {
            ConfirmDialogs.confirm(
                context = requireContext(),
                title = getString(R.string.shop_detail_archive_title, product.name),
                message = getString(R.string.shop_detail_archive_message),
                confirmLabel = getString(R.string.shop_detail_archive)
            ) { viewModel.onSetArchived(true) }
        }
    }

    private fun render(state: ProductDetailUiState) {
        val b = binding ?: return
        loaded = state as? ProductDetailUiState.Loaded
        b.loading.visibility = if (state is ProductDetailUiState.Loading) View.VISIBLE else View.GONE
        b.notFoundText.visibility = if (state is ProductDetailUiState.NotFound) View.VISIBLE else View.GONE
        b.content.visibility = if (state is ProductDetailUiState.Loaded) View.VISIBLE else View.GONE
        b.toolbar.menu.findItem(R.id.action_shop_edit)?.isVisible = state is ProductDetailUiState.Loaded
        b.toolbar.menu.findItem(R.id.action_shop_archive_toggle)?.isVisible = state is ProductDetailUiState.Loaded
        if (state !is ProductDetailUiState.Loaded) return

        val product = state.product
        b.productImageCard.visibility = if (product.imageFile != null) View.VISIBLE else View.GONE
        b.productImage.showProductImage(product.imageFile)
        b.productName.text = product.name
        b.productStatus.showStockStatus(product.stockLevel, product.isArchived)
        b.productStock.text = ShopFormat.quantity(requireContext(), product.stockMilli, product.unit)

        // Un produit archivé n'est plus approvisionné : on le restaure d'abord.
        b.stockButton.isEnabled = !product.isArchived
        val archiveLabel = if (product.isArchived) R.string.shop_detail_restore else R.string.shop_detail_archive
        b.archiveButton.setText(archiveLabel)
        b.toolbar.menu.findItem(R.id.action_shop_archive_toggle)?.setTitle(archiveLabel)
        renderInfoRows(b, state)
    }

    private fun renderInfoRows(b: FragmentShopProductDetailBinding, state: ProductDetailUiState.Loaded) {
        val product = state.product
        val currency = state.currencyCode
        val container = b.infoRows
        container.removeAllViews()
        fun row(labelRes: Int, value: String?) {
            if (value.isNullOrBlank()) return
            val row = ItemShopInfoRowBinding.inflate(LayoutInflater.from(requireContext()), container, false)
            row.rowLabel.setText(labelRes)
            row.rowValue.text = value
            container.addView(row.root)
        }

        row(R.string.shop_detail_sale_price, ShopFormat.money(product.salePriceMinor, currency))
        row(
            R.string.shop_detail_purchase_price,
            product.purchasePriceMinor?.let { ShopFormat.money(it, currency) }
                ?: getString(R.string.shop_detail_purchase_unknown)
        )
        // La marge n'est affichée que si le prix d'achat est connu : jamais calculée sur une valeur inventée.
        row(R.string.shop_detail_margin, product.unitMarginMinor?.let { ShopFormat.money(it, currency) })
        row(
            R.string.shop_detail_min_stock,
            if (product.minStockMilli > 0L) {
                ShopFormat.quantity(requireContext(), product.minStockMilli, product.unit)
            } else {
                getString(R.string.shop_detail_no_min_stock)
            }
        )
        row(R.string.shop_detail_category, state.categoryName)
        row(R.string.shop_detail_sku, product.sku)
        row(R.string.shop_detail_barcode, product.barcode)
        row(R.string.shop_detail_expiry, formatDay(product.expiryDate, ZoneOffset.UTC))
        row(R.string.shop_detail_created, formatDay(product.createdAt, ZoneId.systemDefault()))
        row(R.string.shop_detail_description, product.description)
    }

    private fun formatDay(millis: Long?, zone: ZoneId): String? =
        millis?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(AppDateFormats.mediumDate(requireContext()))
        }
}
