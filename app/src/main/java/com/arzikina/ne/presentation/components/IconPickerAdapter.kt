package com.arzikina.ne.presentation.components

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.databinding.ItemIconPickerBinding

/**
 * Sélecteur générique d'icône sous forme de grille (voir
 * `item_icon_picker.xml`), réutilisé par les formulaires Compte et Catégorie.
 * Équivalent Views de l'ancien `IconPicker<T>` Compose (retiré, voir
 * instructions projet) : générique sur [T] pour couvrir [com.arzikina.ne.domain.model.AccountIcon]
 * et [com.arzikina.ne.domain.model.CategoryIcon] sans dupliquer l'adapter.
 */
class IconPickerAdapter<T>(
    private val items: List<T>,
    private val iconRes: (T) -> Int,
    private var selected: T,
    private val onSelect: (T) -> Unit
) : RecyclerView.Adapter<IconPickerAdapter.ViewHolder>() {

    fun setSelected(value: T) {
        if (value == selected) return
        val previousIndex = items.indexOf(selected)
        selected = value
        val newIndex = items.indexOf(selected)
        if (previousIndex != -1) notifyItemChanged(previousIndex)
        if (newIndex != -1) notifyItemChanged(newIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val binding = ItemIconPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, iconRes(item), item == selected, onSelect)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(private val binding: ItemIconPickerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun <T> bind(item: T, @DrawableRes iconRes: Int, isSelected: Boolean, onSelect: (T) -> Unit) {
            binding.iconImage.setImageResource(iconRes)
            binding.iconImage.isSelected = isSelected
            binding.iconImage.setOnClickListener { onSelect(item) }
        }
    }
}
