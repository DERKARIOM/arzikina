package com.naniger.arzikina.presentation.shop.products

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.naniger.arzikina.databinding.ItemShopProductBinding
import com.naniger.arzikina.databinding.ItemShopProductGridBinding
import com.naniger.arzikina.domain.shop.ProductSummary
import com.naniger.arzikina.presentation.shop.ShopFormat
import com.naniger.arzikina.presentation.shop.showProductImage
import com.naniger.arzikina.presentation.shop.showStockStatus

/** Présentation des produits : lignes compactes avec miniature, ou cartes en grille avec grande photo. */
enum class ProductDisplayMode { LIST, GRID }

/**
 * Produits en liste (alertes du tableau de bord, mode liste) ou en grille (écran Produits). `ListAdapter`/
 * `DiffUtil` avec identifiants stables : seules les lignes modifiées sont rafraîchies, la liste reste fluide
 * à plusieurs milliers de produits. Changer [mode] impose de réassigner l'adapter au RecyclerView
 * (voir `ShopProductsFragment`) : les deux modes ont des ViewHolders différents.
 */
class ShopProductAdapter(
    var mode: ProductDisplayMode = ProductDisplayMode.LIST,
    private val onClick: (ProductSummary) -> Unit
) : ListAdapter<ProductSummary, RecyclerView.ViewHolder>(DIFF) {

    private var currencyCode: String = ""

    init {
        setHasStableIds(true)
    }

    /** Publie [items]. Un changement de devise rafraîchit toutes les lignes (les objets, eux, sont inchangés). */
    fun submit(items: List<ProductSummary>, currency: String) {
        val currencyChanged = currency != currencyCode
        currencyCode = currency
        submitList(items) { if (currencyChanged && itemCount > 0) notifyItemRangeChanged(0, itemCount) }
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun getItemViewType(position: Int): Int = mode.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == ProductDisplayMode.GRID.ordinal) {
            GridHolder(ItemShopProductGridBinding.inflate(inflater, parent, false))
        } else {
            ListHolder(ItemShopProductBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is GridHolder -> holder.bind(item, currencyCode, onClick)
            is ListHolder -> holder.bind(item, currencyCode, onClick)
        }
    }

    class ListHolder(private val b: ItemShopProductBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ProductSummary, currencyCode: String, onClick: (ProductSummary) -> Unit) {
            val context = b.root.context
            b.productImage.showProductImage(item.imageFile)
            b.productName.text = item.name
            val reference = listOfNotNull(item.sku, item.barcode).joinToString(" · ")
            b.productReference.text = reference
            b.productReference.visibility = if (reference.isEmpty()) View.GONE else View.VISIBLE
            b.productPrice.text = ShopFormat.money(item.salePriceMinor, currencyCode)
            b.productQuantity.text = ShopFormat.quantity(context, item.stockMilli, item.unit)
            b.productStatus.showStockStatus(item.stockLevel, item.isArchived)
            b.productCard.setOnClickListener { onClick(item) }
        }
    }

    class GridHolder(private val b: ItemShopProductGridBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ProductSummary, currencyCode: String, onClick: (ProductSummary) -> Unit) {
            val context = b.root.context
            b.productImage.showProductImage(item.imageFile)
            b.productName.text = item.name
            b.productPrice.text = ShopFormat.money(item.salePriceMinor, currencyCode)
            b.productQuantity.text = ShopFormat.quantity(context, item.stockMilli, item.unit)
            b.productStatus.showStockStatus(item.stockLevel, item.isArchived)
            b.productCard.setOnClickListener { onClick(item) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ProductSummary>() {
            override fun areItemsTheSame(a: ProductSummary, b: ProductSummary) = a.id == b.id
            override fun areContentsTheSame(a: ProductSummary, b: ProductSummary) = a == b
        }
    }
}
