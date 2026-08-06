package com.arzikina.ne.presentation.components

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.databinding.ItemColorPickerBinding

/**
 * Sélecteur générique de couleur sous forme de grille de pastilles (voir
 * `item_color_picker.xml`), réutilisé par les formulaires Compte et Catégorie.
 * Équivalent Views de l'ancien `ColorPicker` Compose (retiré, voir
 * instructions projet). Les couleurs viennent de [ColorPalette].
 */
class ColorPickerAdapter(
    private val colors: List<Long> = ColorPalette.COLORS,
    private var selected: Long,
    private val onSelect: (Long) -> Unit
) : RecyclerView.Adapter<ColorPickerAdapter.ViewHolder>() {

    fun setSelected(value: Long) {
        if (value == selected) return
        val previousIndex = colors.indexOf(selected)
        selected = value
        val newIndex = colors.indexOf(selected)
        if (previousIndex != -1) notifyItemChanged(previousIndex)
        if (newIndex != -1) notifyItemChanged(newIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val binding = ItemColorPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val color = colors[position]
        holder.bind(color, color == selected, onSelect)
    }

    override fun getItemCount(): Int = colors.size

    class ViewHolder(private val binding: ItemColorPickerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(colorArgb: Long, isSelected: Boolean, onSelect: (Long) -> Unit) {
            binding.colorSwatch.backgroundTintList = ColorStateList.valueOf(colorArgb.toInt())
            binding.root.isSelected = isSelected
            binding.root.setOnClickListener { onSelect(colorArgb) }
        }
    }
}
