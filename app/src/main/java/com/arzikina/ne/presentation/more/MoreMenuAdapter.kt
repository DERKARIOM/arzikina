package com.arzikina.ne.presentation.more

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.databinding.ItemMoreMenuBinding

/**
 * Adapter volontairement simple (pas de `ListAdapter`/`DiffUtil`, voir
 * [com.arzikina.ne.presentation.dashboard.RecentTransactionsAdapter] pour la
 * justification de ce choix habituel dans le projet) : la liste des entrées
 * de l'écran "Autre" est statique, définie une seule fois dans [MoreFragment]
 * et ne change jamais pendant la durée de vie de l'écran — un DiffUtil serait
 * ici une complexité sans bénéfice.
 */
class MoreMenuAdapter(
    private val items: List<MoreMenuItem>,
    private val onClick: (MoreMenuItem) -> Unit
) : RecyclerView.Adapter<MoreMenuAdapter.ViewHolder>() {

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val binding = ItemMoreMenuBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], onClick)
    }

    class ViewHolder(private val binding: ItemMoreMenuBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MoreMenuItem, onClick: (MoreMenuItem) -> Unit) {
            binding.menuIcon.setImageResource(item.iconRes)
            binding.menuTitle.setText(item.titleRes)
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
