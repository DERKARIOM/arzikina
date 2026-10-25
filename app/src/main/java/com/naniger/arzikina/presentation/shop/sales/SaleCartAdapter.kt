package com.naniger.arzikina.presentation.shop.sales

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.ItemShopCartLineBinding
import com.naniger.arzikina.domain.shop.ProductQuantityFormat
import com.naniger.arzikina.presentation.shop.ShopFormat
import com.naniger.arzikina.presentation.shop.shortLabelRes

/** Actions possibles sur une ligne du panier. */
interface CartLineActions {
    fun onIncrement(line: CartLineUi)
    fun onDecrement(line: CartLineUi)
    fun onEditQuantity(line: CartLineUi)
    fun onRemove(line: CartLineUi)
}

class SaleCartAdapter(
    private val actions: CartLineActions
) : ListAdapter<CartLineUi, SaleCartAdapter.ViewHolder>(DIFF) {

    private var currencyCode: String = ""

    init {
        setHasStableIds(true)
    }

    fun submit(items: List<CartLineUi>, currency: String) {
        val currencyChanged = currency != currencyCode
        currencyCode = currency
        submitList(items) { if (currencyChanged && itemCount > 0) notifyItemRangeChanged(0, itemCount) }
    }

    override fun getItemId(position: Int): Long = getItem(position).productId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemShopCartLineBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position), currencyCode, actions)

    class ViewHolder(private val b: ItemShopCartLineBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(line: CartLineUi, currencyCode: String, actions: CartLineActions) {
            val context = b.root.context
            b.lineName.text = line.name.ifEmpty { context.getString(R.string.shop_cart_line_unavailable_name) }
            b.lineUnitPrice.text = ShopFormat.money(line.unitPriceMinor, currencyCode) +
                " / " + context.getString(line.unit.shortLabelRes())
            b.lineTotal.text = line.lineTotalMinor?.let { ShopFormat.money(it, currencyCode) }.orEmpty()
            b.lineQuantity.text = ProductQuantityFormat.format(line.quantityMilli)

            val problemText = when (line.problem) {
                CartLineProblem.UNAVAILABLE -> context.getString(R.string.shop_cart_problem_unavailable)
                CartLineProblem.INSUFFICIENT_STOCK -> context.getString(
                    R.string.shop_cart_problem_stock, ShopFormat.quantity(context, line.stockMilli, line.unit)
                )
                CartLineProblem.INVALID_QUANTITY -> context.getString(R.string.shop_cart_problem_quantity)
                null -> null
            }
            b.lineProblem.text = problemText
            b.lineProblem.visibility = if (problemText == null) View.GONE else View.VISIBLE

            val editable = line.problem != CartLineProblem.UNAVAILABLE
            b.lineMinus.isEnabled = editable
            b.linePlus.isEnabled = editable
            b.lineQuantity.isEnabled = editable
            b.lineMinus.setOnClickListener { actions.onDecrement(line) }
            b.linePlus.setOnClickListener { actions.onIncrement(line) }
            b.lineQuantity.setOnClickListener { actions.onEditQuantity(line) }
            b.lineRemove.setOnClickListener { actions.onRemove(line) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<CartLineUi>() {
            override fun areItemsTheSame(a: CartLineUi, b: CartLineUi) = a.productId == b.productId
            override fun areContentsTheSame(a: CartLineUi, b: CartLineUi) = a == b
        }
    }
}
