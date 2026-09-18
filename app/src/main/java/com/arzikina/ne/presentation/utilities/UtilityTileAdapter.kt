package com.arzikina.ne.presentation.utilities

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R

/**
 * Adapter réutilisé par les DEUX écrans qui affichent des [UtilityItem] : le bloc "Utilitaires"
 * du Dashboard (`RecyclerView` horizontal) et l'écran "Tous les utilitaires" (`RecyclerView` en
 * grille) — seul le `LayoutManager` posé par chaque écran change, cet adapter reste identique
 * dans les deux cas.
 *
 * [useCardStyle] choisit la mise en page inflée par [ViewHolder] : `false` (défaut, Dashboard)
 * garde les tuiles compactes existantes (item_utility_tile.xml) ; `true` (voir
 * [AllUtilitiesFragment]) pose chaque tuile dans une postcard moderne
 * (item_utility_tile_card.xml, même convention que item_loan.xml/item_account.xml) — demande
 * explicitement limitée à l'écran "Tous les utilitaires", le Dashboard n'est pas concerné. Les
 * deux mises en page partagent EXACTEMENT les mêmes ID (`utilityIcon`/`utilityBadge`/
 * `utilityTitle`), [ViewHolder] les lit donc sans se soucier de laquelle a été inflée — pas de
 * `ViewBinding` ici (deux fichiers XML à racines différentes ne peuvent pas partager une classe
 * de binding générée), `findViewById` classique à la place.
 *
 * Volontairement simple (pas de `ListAdapter`/`DiffUtil`) : l'ENSEMBLE des utilitaires reste
 * statique (voir [UtilityCatalog]) — seul [UtilityItem.badgeCount] change dans le temps (voir
 * [submitItems]), jamais la liste elle-même ni son ordre. `notifyDataSetChanged()` sur une liste
 * aussi courte reste largement suffisant, pas besoin de `DiffUtil` pour un si petit gain.
 */
class UtilityTileAdapter(
    private var items: List<UtilityItem>,
    private val useCardStyle: Boolean = false,
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
        val layoutRes = if (useCardStyle) R.layout.item_utility_tile_card else R.layout.item_utility_tile
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.utilityIcon)
        private val badge: TextView = itemView.findViewById(R.id.utilityBadge)
        private val title: TextView = itemView.findViewById(R.id.utilityTitle)

        fun bind(item: UtilityItem, onClick: (UtilityItem) -> Unit) {
            icon.setImageResource(item.iconRes)
            title.setText(item.titleRes)
            val count = item.badgeCount ?: 0
            badge.visibility = if (count > 0) View.VISIBLE else View.GONE
            badge.text = if (count > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else count.toString()
            itemView.setOnClickListener { onClick(item) }
        }

        private companion object {
            /** Au-delà, "9+" plutôt qu'un nombre à deux chiffres qui déborderait de la pastille
             * (voir bg_badge_circle.xml, dimensionnée pour 1-2 caractères). */
            const val MAX_BADGE_COUNT = 9
        }
    }
}
