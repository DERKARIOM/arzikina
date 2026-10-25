package com.naniger.arzikina.presentation.shop.stock

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.ItemShopStockMovementBinding
import com.naniger.arzikina.domain.shop.ProductQuantityFormat
import com.naniger.arzikina.domain.shop.ProductUnit
import com.naniger.arzikina.domain.shop.StockMovement
import com.naniger.arzikina.presentation.shop.ShopFormat
import com.naniger.arzikina.presentation.shop.labelRes
import com.naniger.arzikina.presentation.shop.shortLabelRes
import com.naniger.arzikina.util.AppDateFormats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Lignes de l'historique de stock. Unité et devise sont celles du produit/de la boutique (voir [submit]). */
class ShopStockHistoryAdapter : ListAdapter<StockMovement, ShopStockHistoryAdapter.ViewHolder>(DIFF) {

    private var unit: ProductUnit = ProductUnit.PIECE
    private var currencyCode: String = ""

    init {
        setHasStableIds(true)
    }

    fun submit(items: List<StockMovement>, unit: ProductUnit, currencyCode: String) {
        val changed = unit != this.unit || currencyCode != this.currencyCode
        this.unit = unit
        this.currencyCode = currencyCode
        submitList(items) { if (changed && itemCount > 0) notifyItemRangeChanged(0, itemCount) }
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemShopStockMovementBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position), unit, currencyCode)

    class ViewHolder(private val b: ItemShopStockMovementBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: StockMovement, unit: ProductUnit, currencyCode: String) {
            val context = b.root.context
            b.movementType.setText(item.type.labelRes())

            val entering = item.deltaMilli > 0
            val sign = if (entering) "+" else "−"
            val quantity = ProductQuantityFormat.format(kotlin.math.abs(item.deltaMilli))
            b.movementDelta.text = "$sign$quantity ${context.getString(unit.shortLabelRes())}"
            b.movementDelta.setTextColor(
                ContextCompat.getColor(context, if (entering) R.color.shop_stock_normal else R.color.shop_stock_out)
            )

            val moment = Instant.ofEpochMilli(item.occurredAt).atZone(ZoneId.systemDefault())
            b.movementDate.text = "${moment.toLocalDate().format(AppDateFormats.mediumDate(context))} · ${moment.format(TIME)}"

            val details = listOfNotNull(
                item.reason,
                item.reference?.let { context.getString(R.string.shop_stock_history_reference, it) },
                item.unitCostMinor?.let {
                    context.getString(R.string.shop_stock_history_cost, ShopFormat.money(it, currencyCode))
                }
            ).joinToString(" · ")
            b.movementDetails.text = details
            b.movementDetails.visibility = if (details.isEmpty()) View.GONE else View.VISIBLE
        }

        private companion object {
            val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<StockMovement>() {
            override fun areItemsTheSame(a: StockMovement, b: StockMovement) = a.id == b.id
            override fun areContentsTheSame(a: StockMovement, b: StockMovement) = a == b
        }
    }
}
