package com.naniger.arzikina.presentation.accounts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.naniger.arzikina.databinding.ItemAccountBinding
import com.naniger.arzikina.databinding.ItemAccountCreditCardBinding
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.AccountType
import java.util.Collections

/**
 * Liste des comptes : une carte dégradée façon carte bancaire par compte
 * classique (voir `item_account.xml`/[AccountCardGradient]), une carte
 * VIRTUELLE façon VISA/Mastercard pour un compte [AccountType.CREDIT_CARD]
 * (voir `item_account_credit_card.xml`/[AccountCardCreditBinder]) — deux
 * `viewType` RecyclerView plutôt qu'un seul layout avec des vues masquées :
 * les deux rendus sont trop différents pour partager une structure commune
 * sans la complexifier inutilement. Voir
 * [com.naniger.arzikina.presentation.dashboard.RecentTransactionsAdapter] pour le
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

    /** Copie de travail utilisée PENDANT un glisser-déposer (voir [beginReorder]/[moveItem]/
     * [endReorder]) — `submitList` n'est PAS appelé à chaque étape intermédiaire du glisser : un
     * recalcul complet de `DiffUtil` à chaque frame de déplacement serait inutile (la position
     * change, jamais le contenu) et pourrait réordonner/animer différemment de ce que l'utilisateur
     * est en train de faire au doigt. [notifyItemMoved] directement pendant le glisser, `submitList`
     * une seule fois à la fin ([endReorder]) pour réconcilier proprement l'état interne de cette
     * `ListAdapter` (`AsyncListDiffer`) avec ce qui est déjà affiché à l'écran. */
    private var workingList: MutableList<AccountUiItem> = mutableListOf()

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

    /** Démarre un déplacement : clone la liste actuellement affichée dans [workingList] — appelé
     * par `AccountsFragment` au tout début d'un glisser (`ItemTouchHelper.Callback.onSelectedChanged`,
     * `ACTION_STATE_DRAG`), jamais rappelé en boucle pendant le glisser lui-même. */
    fun beginReorder() {
        workingList = currentList.toMutableList()
    }

    /**
     * Déplace l'item de [from] vers [to] dans [workingList] — suite d'échanges adjacents (PAS un
     * simple `Collections.swap(from, to)` : `to` peut être à plus d'une position de `from` lors
     * d'un glisser rapide qui saute plusieurs cartes en un seul appel d'`ItemTouchHelper.onMove`),
     * même algorithme que l'exemple officiel `ItemTouchHelper.Callback`. [notifyItemMoved]
     * directement (PAS `submitList`, voir la doc de [workingList]).
     */
    fun moveItem(from: Int, to: Int) {
        if (from < to) {
            for (i in from until to) Collections.swap(workingList, i, i + 1)
        } else {
            for (i in from downTo to + 1) Collections.swap(workingList, i, i - 1)
        }
        notifyItemMoved(from, to)
    }

    /** Fin du glisser (dépôt) : réconcilie l'état officiel de la `ListAdapter` avec [workingList]
     * (voir sa doc) et retourne l'ordre final des ids de compte, pour persistance (voir
     * `AccountsFragment.itemTouchHelper`/`AccountsViewModel.reorderAccounts`). */
    fun endReorder(): List<Long> {
        val orderedIds = workingList.map { it.account.id }
        submitList(workingList.toList())
        return orderedIds
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
