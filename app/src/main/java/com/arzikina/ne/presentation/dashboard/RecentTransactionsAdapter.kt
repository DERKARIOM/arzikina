package com.arzikina.ne.presentation.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.databinding.ItemTransactionCompactBinding
import com.arzikina.ne.presentation.transactions.TransactionItemBinder
import com.arzikina.ne.presentation.transactions.TransactionUiItem

/**
 * Liste compacte des dernières transactions affichée sur le tableau de bord
 * (lecture seule, pas de suppression ni de clic, ni de regroupement par
 * jour — voir [com.arzikina.ne.presentation.transactions.GroupedTransactionsAdapter]
 * pour la version complète, groupée, des écrans Transactions et "Détail du
 * compte").
 *
 * [ListAdapter]/[DiffUtil] plutôt qu'un simple [RecyclerView.Adapter] avec
 * `notifyDataSetChanged()` : la liste change à chaque nouvelle transaction
 * (flux Room réactif côté [DashboardViewModel]), il faut éviter de tout
 * redessiner à chaque émission.
 */
class RecentTransactionsAdapter :
    ListAdapter<TransactionUiItem, RecentTransactionsAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val binding = ItemTransactionCompactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemTransactionCompactBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TransactionUiItem) {
            TransactionItemBinder.bind(binding, item)
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
