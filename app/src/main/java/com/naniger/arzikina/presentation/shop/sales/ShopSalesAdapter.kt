package com.naniger.arzikina.presentation.shop.sales

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.ItemShopSaleBinding
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.shop.SaleSummary
import com.naniger.arzikina.presentation.components.displayName
import com.naniger.arzikina.presentation.shop.ShopFormat
import com.naniger.arzikina.util.AppDateFormats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ShopSalesAdapter(
    private val onClick: (SaleSummary) -> Unit
) : ListAdapter<SaleSummary, ShopSalesAdapter.ViewHolder>(DIFF) {

    private var accounts: Map<Long, Account> = emptyMap()

    init {
        setHasStableIds(true)
    }

    fun submit(items: List<SaleSummary>, accountsById: Map<Long, Account>) {
        val accountsChanged = accountsById != accounts
        accounts = accountsById
        submitList(items) { if (accountsChanged && itemCount > 0) notifyItemRangeChanged(0, itemCount) }
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemShopSaleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position), accounts, onClick)

    class ViewHolder(private val b: ItemShopSaleBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: SaleSummary, accounts: Map<Long, Account>, onClick: (SaleSummary) -> Unit) {
            val context = b.root.context
            b.saleReference.text = item.reference
            b.saleTotal.text = ShopFormat.money(item.totalMinor, item.currencyCode)
            val moment = Instant.ofEpochMilli(item.soldAt).atZone(ZoneId.systemDefault())
            b.saleDate.text = "${moment.toLocalDate().format(AppDateFormats.mediumDate(context))} · ${moment.format(TIME)}"
            b.saleStatus.showSaleStatus(item.status)
            b.saleMeta.text = listOfNotNull(
                item.customerName,
                item.paymentMethod?.let { context.getString(it.labelRes()) },
                item.accountId?.let { accounts[it]?.displayName(context) },
                if (item.hasReceipt) context.getString(R.string.shop_history_has_receipt) else null
            ).joinToString(" · ").ifEmpty { context.getString(R.string.shop_history_no_details) }
            b.saleCard.setOnClickListener { onClick(item) }
        }
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DIFF = object : DiffUtil.ItemCallback<SaleSummary>() {
            override fun areItemsTheSame(a: SaleSummary, b: SaleSummary) = a.id == b.id
            override fun areContentsTheSame(a: SaleSummary, b: SaleSummary) = a == b
        }
    }
}
