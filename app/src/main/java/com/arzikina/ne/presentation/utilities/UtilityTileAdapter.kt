package com.arzikina.ne.presentation.utilities

import android.view.LayoutInflater
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
 * [com.arzikina.ne.presentation.more.MoreMenuAdapter]) : la liste des utilitaires est statique,
 * définie une seule fois par l'écran appelant et ne change jamais pendant sa durée de vie.
 */
class UtilityTileAdapter(
    private val items: List<UtilityItem>,
    private val onClick: (UtilityItem) -> Unit
) : RecyclerView.Adapter<UtilityTileAdapter.ViewHolder>() {

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
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
