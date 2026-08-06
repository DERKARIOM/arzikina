package com.arzikina.ne.presentation.transactions

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.databinding.ItemTransactionCompactBinding

/**
 * Liste complète des transactions (écran Transactions), avec clic pour
 * éditer et bouton de suppression — voir [TransactionItemBinder] pour la
 * mise en forme partagée avec [com.arzikina.ne.presentation.dashboard.RecentTransactionsAdapter].
 */
class TransactionsAdapter(
    private val onClick: (TransactionUiItem) -> Unit,
    private val onDeleteClick: (TransactionUiItem) -> Unit
) : ListAdapter<TransactionUiItem, TransactionsAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val binding = ItemTransactionCompactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick, onDeleteClick)
    }

    class ViewHolder(private val binding: ItemTransactionCompactBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            item: TransactionUiItem,
            onClick: (TransactionUiItem) -> Unit,
            onDeleteClick: (TransactionUiItem) -> Unit
        ) {
            TransactionItemBinder.bind(binding, item)
            binding.deleteButton.visibility = View.VISIBLE
            binding.root.setOnClickListener { onClick(item) }
            binding.deleteButton.setOnClickListener { onDeleteClick(item) }
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<TransactionUiItem>() {
            override fun areItemsTheSame(oldItem: TransactionUiItem, newItem: TransactionUiItem): Boolean =
                oldItem.transaction.id == newItem.transaction.id

            override fun areContentsTheSame(oldItem: TransactionUiItem, newItem: TransactionUiItem): Boolean =
                oldItem == newItem
        }
    }
}
