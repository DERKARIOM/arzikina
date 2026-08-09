package com.arzikina.ne.presentation.accounts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.databinding.ItemAccountBinding
import com.arzikina.ne.databinding.ItemAccountCreditCardBinding
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.AccountType

/**
 * Liste des comptes : une carte dégradée façon carte bancaire par compte
 * classique (voir `item_account.xml`/[AccountCardGradient]), une carte
 * VIRTUELLE façon VISA/Mastercard pour un compte [AccountType.CREDIT_CARD]
 * (voir `item_account_credit_card.xml`/[AccountCardCreditBinder]) — deux
 * `viewType` RecyclerView plutôt qu'un seul layout avec des vues masquées :
 * les deux rendus sont trop différents pour partager une structure commune
 * sans la complexifier inutilement. Voir
 * [com.arzikina.ne.presentation.dashboard.RecentTransactionsAdapter] pour le
 * raisonnement `ListAdapter`/`DiffUtil` plutôt que `notifyDataSetChanged`.
 *
 * Prend des [AccountUiItem] (solde COURANT) plutôt que des [Account] bruts
 * (solde initial) depuis la réorganisation de l'écran "Mes comptes" — voir
 * [AccountsViewModel]. Pas de suppression depuis la liste (voir maquette, qui
 * n'en montre pas non plus) : modifier/supprimer se fait depuis le menu "⋮"
 * de "Détail du compte", atteint via [onClick].
 */
class AccountsAdapter(
    private val onClick: (Account) -> Unit
) : ListAdapter<AccountUiItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).account.type == AccountType.CREDIT_CARD) VIEW_TYPE_CREDIT_CARD else VIEW_TYPE_CLASSIC

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_CREDIT_CARD) {
            CreditCardViewHolder(ItemAccountCreditCardBinding.inflate(inflater, parent, false))
        } else {
            ClassicViewHolder(ItemAccountBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is ClassicViewHolder -> holder.bind(item, onClick)
            is CreditCardViewHolder -> holder.bind(item, onClick)
        }
    }

    class ClassicViewHolder(private val binding: ItemAccountBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AccountUiItem, onClick: (Account) -> Unit) {
            val account = item.account
            AccountCardBinder.bind(binding, account, item.currentBalance)
            binding.accountCard.setOnClickListener { onClick(account) }
        }
    }

    class CreditCardViewHolder(private val binding: ItemAccountCreditCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AccountUiItem, onClick: (Account) -> Unit) {
            val account = item.account
            AccountCardCreditBinder.bind(binding, account, item.currentBalance, item.cardHolderName)
            binding.accountCard.setOnClickListener { onClick(account) }
        }
    }

    private companion object {
        const val VIEW_TYPE_CLASSIC = 0
        const val VIEW_TYPE_CREDIT_CARD = 1

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AccountUiItem>() {
            override fun areItemsTheSame(oldItem: AccountUiItem, newItem: AccountUiItem): Boolean =
                oldItem.account.id == newItem.account.id
            override fun areContentsTheSame(oldItem: AccountUiItem, newItem: AccountUiItem): Boolean =
                oldItem == newItem
        }
    }
}
