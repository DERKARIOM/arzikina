package com.arzikina.ne.presentation.components

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.DialogExternalAppPickerBinding
import com.arzikina.ne.databinding.ItemExternalAppPickerBinding
import com.arzikina.ne.util.external.ExternalAppInfo
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Dialogue de sélection d'une application externe détectée sur l'appareil (voir cahier des
 * charges, "Sélectionner une application" — formulaire de compte Mobile Money). Même principe
 * générique/sans état propre qu'[AccountPickerDialog] : ni Fragment ni ViewModel dédiés pour un
 * simple `AlertDialog`.
 *
 * [apps] est déjà résolu par l'appelant (voir `AccountFormViewModel.onSelectMobileMoneyAppClicked`,
 * qui interroge [com.arzikina.ne.util.external.ExternalAppLauncher] hors du thread principal) —
 * ce dialogue ne touche jamais lui-même au `PackageManager`, il ne fait qu'afficher/filtrer une
 * liste déjà construite. Le filtrage par texte reste local (voir cahier des charges, section 9 :
 * "ne pas dépendre uniquement d'une liste codée en dur" — [apps] vient entièrement de
 * `PackageManager`, la recherche n'est qu'un confort pour naviguer une liste potentiellement
 * longue, pas un mécanisme de détection).
 */
object ExternalAppPickerDialog {
    fun show(
        context: Context,
        apps: List<ExternalAppInfo>,
        onSelect: (ExternalAppInfo) -> Unit
    ) {
        val dialogBinding = DialogExternalAppPickerBinding.inflate(LayoutInflater.from(context))
        dialogBinding.appsList.layoutManager = LinearLayoutManager(context)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.external_app_picker_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        val adapter = ExternalAppAdapter(apps) { app ->
            onSelect(app)
            dialog.dismiss()
        }
        dialogBinding.appsList.adapter = adapter

        // État initial explicite (voir la doc de `filter`) : `doAfterTextChanged` ne se déclenche
        // qu'à la PROCHAINE frappe, jamais à l'attache — sans cet appel, une liste [apps] déjà
        // vide à l'ouverture (aucune application détectée) laisserait la RecyclerView vide SANS
        // afficher [emptyState], avant la moindre saisie dans le champ de recherche.
        dialogBinding.emptyState.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        dialogBinding.appsList.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE

        dialogBinding.searchInput.doAfterTextChanged { text ->
            adapter.filter(text?.toString().orEmpty())
            val isEmpty = adapter.itemCount == 0
            dialogBinding.appsList.visibility = if (isEmpty) View.GONE else View.VISIBLE
            dialogBinding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        }

        dialog.show()
    }

    /** Filtrage LOCAL (voir la doc de classe) : [allApps] reste la référence complète, [shown]
     * la sous-liste actuellement affichée après filtre — évite de raméner la liste complète à
     * chaque frappe si l'appelant voulait la ré-interroger. */
    private class ExternalAppAdapter(
        private val allApps: List<ExternalAppInfo>,
        private val onClick: (ExternalAppInfo) -> Unit
    ) : RecyclerView.Adapter<ExternalAppViewHolder>() {

        private var shown: List<ExternalAppInfo> = allApps

        fun filter(query: String) {
            shown = if (query.isBlank()) {
                allApps
            } else {
                allApps.filter {
                    it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
                }
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExternalAppViewHolder {
            val binding = ItemExternalAppPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ExternalAppViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ExternalAppViewHolder, position: Int) {
            holder.bind(shown[position], onClick)
        }

        override fun getItemCount(): Int = shown.size
    }

    private class ExternalAppViewHolder(private val binding: ItemExternalAppPickerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(app: ExternalAppInfo, onClick: (ExternalAppInfo) -> Unit) {
            binding.appIcon.setImageDrawable(app.icon)
            binding.appLabel.text = app.label
            binding.appPackageName.text = app.packageName
            binding.root.setOnClickListener { onClick(app) }
        }
    }
}
