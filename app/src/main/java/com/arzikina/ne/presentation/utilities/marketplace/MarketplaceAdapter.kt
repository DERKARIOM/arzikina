package com.arzikina.ne.presentation.utilities.marketplace

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemMarketplaceNoResultsBinding
import com.arzikina.ne.databinding.ItemMarketplaceSectionHeaderBinding
import com.arzikina.ne.databinding.ItemMarketplaceTemplateBinding
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.presentation.categories.CategoryIconMapper
import com.arzikina.ne.util.Money

/**
 * Liste "Marketplace personnelle" (voir [MarketplaceListRow]/[MarketplaceFragment]) : en-têtes de
 * section, Cards de modèle, ou ligne "Aucun résultat" — même principe multi-type que `LoansAdapter`.
 */
class MarketplaceAdapter(
    private val onBuyClick: (TransactionTemplateListItem) -> Unit,
    private val onEdit: (TransactionTemplateListItem) -> Unit,
    private val onToggleFavorite: (TransactionTemplateListItem) -> Unit,
    private val onDuplicate: (TransactionTemplateListItem) -> Unit,
    private val onDelete: (TransactionTemplateListItem) -> Unit
) : ListAdapter<MarketplaceListRow, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is MarketplaceListRow.SectionHeader -> VIEW_TYPE_SECTION_HEADER
        is MarketplaceListRow.TemplateRow -> VIEW_TYPE_TEMPLATE
        is MarketplaceListRow.NoResults -> VIEW_TYPE_NO_RESULTS
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SECTION_HEADER ->
                SectionHeaderViewHolder(ItemMarketplaceSectionHeaderBinding.inflate(inflater, parent, false))
            VIEW_TYPE_TEMPLATE ->
                TemplateViewHolder(ItemMarketplaceTemplateBinding.inflate(inflater, parent, false))
            else ->
                NoResultsViewHolder(ItemMarketplaceNoResultsBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is MarketplaceListRow.SectionHeader -> (holder as SectionHeaderViewHolder).bind(row.titleRes)
            is MarketplaceListRow.TemplateRow ->
                (holder as TemplateViewHolder).bind(row.item, onBuyClick, onEdit, onToggleFavorite, onDuplicate, onDelete)
            is MarketplaceListRow.NoResults -> Unit
        }
    }

    class SectionHeaderViewHolder(private val binding: ItemMarketplaceSectionHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(@StringRes titleRes: Int) {
            binding.sectionTitle.setText(titleRes)
        }
    }

    class TemplateViewHolder(private val binding: ItemMarketplaceTemplateBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: TransactionTemplateListItem,
            onBuyClick: (TransactionTemplateListItem) -> Unit,
            onEdit: (TransactionTemplateListItem) -> Unit,
            onToggleFavorite: (TransactionTemplateListItem) -> Unit,
            onDuplicate: (TransactionTemplateListItem) -> Unit,
            onDelete: (TransactionTemplateListItem) -> Unit
        ) {
            binding.templateIcon.setImageResource(CategoryIconMapper.iconFor(item.categoryIcon))
            binding.templateIcon.backgroundTintList = ColorStateList.valueOf(item.categoryColorArgb.toInt())
            binding.templateName.text = item.name
            binding.templateCategory.text = item.categoryName
            binding.templateAmount.text = Money.format(CurrencyAmount(item.currencyCode, item.amount))
            // Étoile visible UNIQUEMENT si favori (cahier des charges section 7) — contrairement au
            // menu ⋮ (voir showActionsMenu), qui propose TOUJOURS le bascule dans les deux sens.
            binding.favoriteIcon.visibility = if (item.isFavorite) View.VISIBLE else View.GONE

            binding.buyButton.setOnClickListener { onBuyClick(item) }
            binding.menuButton.setOnClickListener { anchor ->
                showActionsMenu(anchor, item, onEdit, onToggleFavorite, onDuplicate, onDelete)
            }
        }

        /** Voir `BudgetModernAdapter.showActionsMenu` pour le même principe de `PopupMenu`. Le
         * libellé de [R.id.action_toggle_favorite] est déterminé au moment de l'ouverture (jamais
         * dans le menu XML statique) : bascule dans les DEUX sens selon [item.isFavorite]. */
        private fun showActionsMenu(
            anchor: View,
            item: TransactionTemplateListItem,
            onEdit: (TransactionTemplateListItem) -> Unit,
            onToggleFavorite: (TransactionTemplateListItem) -> Unit,
            onDuplicate: (TransactionTemplateListItem) -> Unit,
            onDelete: (TransactionTemplateListItem) -> Unit
        ) {
            val context = anchor.context
            val popup = PopupMenu(context, anchor)
            popup.inflate(R.menu.marketplace_template_actions_menu)
            popup.menu.findItem(R.id.action_toggle_favorite).title = context.getString(
                if (item.isFavorite) R.string.marketplace_action_remove_favorite else R.string.marketplace_action_add_favorite
            )
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_edit -> {
                        onEdit(item)
                        true
                    }
                    R.id.action_toggle_favorite -> {
                        onToggleFavorite(item)
                        true
                    }
                    R.id.action_duplicate -> {
                        onDuplicate(item)
                        true
                    }
                    R.id.action_delete -> {
                        onDelete(item)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    /** Contenu entièrement statique (voir `item_marketplace_no_results.xml`) : rien à lier. */
    class NoResultsViewHolder(binding: ItemMarketplaceNoResultsBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val VIEW_TYPE_SECTION_HEADER = 0
        const val VIEW_TYPE_TEMPLATE = 1
        const val VIEW_TYPE_NO_RESULTS = 2

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<MarketplaceListRow>() {
            override fun areItemsTheSame(oldItem: MarketplaceListRow, newItem: MarketplaceListRow): Boolean = when {
                oldItem is MarketplaceListRow.SectionHeader && newItem is MarketplaceListRow.SectionHeader ->
                    oldItem.titleRes == newItem.titleRes
                oldItem is MarketplaceListRow.TemplateRow && newItem is MarketplaceListRow.TemplateRow ->
                    oldItem.item.id == newItem.item.id
                oldItem is MarketplaceListRow.NoResults && newItem is MarketplaceListRow.NoResults -> true
                else -> false
            }

            override fun areContentsTheSame(oldItem: MarketplaceListRow, newItem: MarketplaceListRow): Boolean =
                oldItem == newItem
        }
    }
}
