package com.arzikina.ne.presentation.accounts

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.databinding.ItemAccountBinding
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.util.Money

/**
 * Liste des comptes. Voir [com.arzikina.ne.presentation.dashboard.RecentTransactionsAdapter]
 * pour le raisonnement (`ListAdapter`/`DiffUtil` plutôt que `notifyDataSetChanged`).
 *
 * Prend des [AccountUiItem] (solde COURANT) plutôt que des [Account] bruts
 * (solde initial) depuis la réorganisation de l'écran "Mes comptes" — voir
 * [AccountsViewModel].
 */
class AccountsAdapter(
    private val onClick: (Account) -> Unit,
    private val onDeleteClick: (Account) -> Unit
) : ListAdapter<AccountUiItem, AccountsAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val binding = ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick, onDeleteClick)
    }

    class ViewHolder(private val binding: ItemAccountBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AccountUiItem, onClick: (Account) -> Unit, onDeleteClick: (Account) -> Unit) {
            val account = item.account

            binding.accountIcon.setImageResource(AccountIconMapper.iconFor(account.icon))
            binding.accountIcon.backgroundTintList = ColorStateList.valueOf(account.colorArgb.toInt())
            binding.accountName.text = account.name
            binding.accountBalance.text = Money.format(CurrencyAmount(account.currencyCode, item.currentBalance))

            binding.root.setOnClickListener { onClick(account) }
            binding.deleteButton.setOnClickListener { onDeleteClick(account) }
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AccountUiItem>() {
            override fun areItemsTheSame(oldItem: AccountUiItem, newItem: AccountUiItem): Boolean =
                oldItem.account.id == newItem.account.id
            override fun areContentsTheSame(oldItem: AccountUiItem, newItem: AccountUiItem): Boolean =
                oldItem == newItem
        }
    }
}
