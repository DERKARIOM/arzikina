package com.arzikina.ne.presentation.utilities

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.databinding.ItemUtilityTileBinding

/**
 * Adapter réutilisé par les DEUX écrans qui affichent des [UtilityItem] : le bloc "Utilitaires"
 * du Dashboard (`RecyclerView` horizontal) et l'écran "Tous les utilitaires" (`RecyclerView` en
 * grille) — seul le `LayoutManager` posé par chaque écran change, cet adapter et
 * `item_utility_tile.xml` restent identiques dans les deux cas.
 *
 * Volontairement simple (pas de `ListAdapter`/`DiffUtil`, même raisonnement que
 * [com.arzikina.ne.presentation.more.MoreMenuAdapter]) : l'ENSEMBLE des utilitaires reste statique
 * (4 entrées fixes, voir [UtilityCatalog]) — seul [UtilityItem.badgeCount] change dans le temps
 * (voir [submitItems]), jamais la liste elle-même ni son ordre. `notifyDataSetChanged()` sur une
 * liste de 4 éléments reste largement suffisant, pas besoin de `DiffUtil` pour un si petit gain.
 */
class UtilityTileAdapter(
    private var items: List<UtilityItem>,
    private val onClick: (UtilityItem) -> Unit
) : RecyclerView.Adapter<UtilityTileAdapter.ViewHolder>() {

    /** Remplace la liste affichée (voir la doc de la classe) — appelé par `DashboardFragment.render`
     * à chaque émission de `DashboardViewModel.uiState`, pour refléter [UtilityItem.badgeCount] à
     * jour sans reconstruire l'adapter lui-même. */
    fun submitItems(newItems: List<UtilityItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val binding = ItemUtilityTileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    class ViewHolder(private val binding: ItemUtilityTileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: UtilityItem, onClick: (UtilityItem) -> Unit) {
            binding.utilityIcon.setImageResource(item.iconRes)
            binding.utilityTitle.setText(item.titleRes)
            val count = item.badgeCount ?: 0
            binding.utilityBadge.visibility = if (count > 0) View.VISIBLE else View.GONE
            binding.utilityBadge.text = if (count > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else count.toString()
            binding.root.setOnClickListener { onClick(item) }
        }

        private companion object {
            /** Au-delà, "9+" plutôt qu'un nombre à deux chiffres qui déborderait de la pastille
             * (voir bg_badge_circle.xml, dimensionnée pour 1-2 caractères). */
            const val MAX_BADGE_COUNT = 9
        }
    }
}
