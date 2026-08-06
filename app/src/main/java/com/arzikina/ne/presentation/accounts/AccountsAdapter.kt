package com.arzikina.ne.presentation.accounts

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemAccountBinding
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.util.Money

/**
 * Liste des comptes. Voir [com.arzikina.ne.presentation.dashboard.RecentTransactionsAdapter]
 * pour le raisonnement (`ListAdapter`/`DiffUtil` plutôt que `notifyDataSetChanged`).
 */
class AccountsAdapter(
    private val onClick: (Account) -> Unit,
    private val onDeleteClick: (Account) -> Unit
) : ListAdapter<Account, AccountsAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val binding = ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick, onDeleteClick)
    }

    class ViewHolder(private val binding: ItemAccountBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(account: Account, onClick: (Account) -> Unit, onDeleteClick: (Account) -> Unit) {
            val context = binding.root.context

            binding.accountIcon.setImageResource(AccountIconMapper.iconFor(account.icon))
            binding.accountIcon.backgroundTintList = ColorStateList.valueOf(account.colorArgb.toInt())
            binding.accountName.text = account.name
            binding.accountBalance.text = context.getString(
                R.string.account_item_initial_balance,
                Money.format(CurrencyAmount(account.currencyCode, account.initialBalance))
            )

            binding.root.setOnClickListener { onClick(account) }
            binding.deleteButton.setOnClickListener { onDeleteClick(account) }
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Account>() {
            override fun areItemsTheSame(oldItem: Account, newItem: Account): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Account, newItem: Account): Boolean = oldItem == newItem
        }
    }
}
