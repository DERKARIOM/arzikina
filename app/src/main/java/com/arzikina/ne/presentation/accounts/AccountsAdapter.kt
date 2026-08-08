package com.arzikina.ne.presentation.accounts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.databinding.ItemAccountBinding
import com.arzikina.ne.domain.model.Account

/**
 * Liste des comptes, une carte dégradée par compte façon carte bancaire (voir
 * `item_account.xml`/[AccountCardGradient]). Voir
 * [com.arzikina.ne.presentation.dashboard.RecentTransactionsAdapter] pour le
 * raisonnement (`ListAdapter`/`DiffUtil` plutôt que `notifyDataSetChanged`).
 *
 * Prend des [AccountUiItem] (solde COURANT) plutôt que des [Account] bruts
 * (solde initial) depuis la réorganisation de l'écran "Mes comptes" — voir
 * [AccountsViewModel]. Pas de suppression depuis la liste (voir maquette, qui
 * n'en montre pas non plus) : modifier/supprimer se fait depuis le menu "⋮"
 * de "Détail du compte", atteint via [onClick].
 */
class AccountsAdapter(
    private val onClick: (Account) -> Unit
) : ListAdapter<AccountUiItem, AccountsAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val binding = ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onClick)
    }

    class ViewHolder(private val binding: ItemAccountBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AccountUiItem, onClick: (Account) -> Unit) {
            val account = item.account
            AccountCardBinder.bind(binding, account, item.currentBalance)
            binding.accountCard.setOnClickListener { onClick(account) }
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
