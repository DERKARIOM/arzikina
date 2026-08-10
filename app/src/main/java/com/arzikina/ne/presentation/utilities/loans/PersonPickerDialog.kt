package com.arzikina.ne.presentation.utilities.loans

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.DialogNewPersonBinding
import com.arzikina.ne.databinding.ItemPersonPickerAddNewBinding
import com.arzikina.ne.databinding.ItemPersonPickerBinding
import com.arzikina.ne.domain.model.Person
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Dialogue de sélection d'une personne (voir maquette, champ "Bénéficiaire / Prêteur"), avec une
 * dernière ligne "Ajouter une nouvelle personne" — même principe que [AccountPickerDialog], mais
 * à deux `viewType` (voir [LoansAdapter] pour le même raisonnement) puisque cette ligne
 * supplémentaire n'a pas la même structure qu'une ligne "personne".
 */
object PersonPickerDialog {

    fun show(
        context: Context,
        persons: List<Person>,
        onSelectExisting: (Person) -> Unit,
        onCreateNew: (name: String, phone: String?) -> Unit
    ) {
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.loan_form_person_picker_title)
            .setView(recyclerView)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        recyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemViewType(position: Int): Int =
                if (position < persons.size) VIEW_TYPE_PERSON else VIEW_TYPE_ADD_NEW

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val inflater = LayoutInflater.from(parent.context)
                return if (viewType == VIEW_TYPE_PERSON) {
                    PersonViewHolder(ItemPersonPickerBinding.inflate(inflater, parent, false))
                } else {
                    AddNewViewHolder(ItemPersonPickerAddNewBinding.inflate(inflater, parent, false))
                }
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                when (holder) {
                    is PersonViewHolder -> holder.bind(persons[position]) {
                        onSelectExisting(persons[position])
                        dialog.dismiss()
                    }
                    is AddNewViewHolder -> holder.bind {
                        dialog.dismiss()
                        showNewPersonDialog(context, onCreateNew)
                    }
                }
            }

            override fun getItemCount(): Int = persons.size + 1
        }

        dialog.show()
    }

    private fun showNewPersonDialog(context: Context, onCreateNew: (name: String, phone: String?) -> Unit) {
        val binding = DialogNewPersonBinding.inflate(LayoutInflater.from(context))
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.loan_form_new_person_title)
            .setView(binding.root)
            .setPositiveButton(R.string.action_add, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            // Positive button gérée manuellement (plutôt que via le lambda de setPositiveButton,
            // qui ferme toujours le dialogue) pour valider le nom avant de fermer — même principe
            // que la validation d'un formulaire classique, sans quitter le dialogue en cas d'erreur.
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = binding.newPersonNameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    binding.newPersonNameLayout.error = context.getString(R.string.loan_form_new_person_name_required)
                    return@setOnClickListener
                }
                val phone = binding.newPersonPhoneInput.text?.toString()?.trim()
                onCreateNew(name, phone)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private class PersonViewHolder(private val binding: ItemPersonPickerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(person: Person, onClick: () -> Unit) {
            binding.personAvatar.text = personAvatarInitial(person.name)
            binding.personAvatar.backgroundTintList = ColorStateList.valueOf(personAvatarColorArgb(person.name).toInt())
            binding.personName.text = person.name
            binding.personPhone.text = person.phone
            binding.personPhone.visibility = if (person.phone.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.root.setOnClickListener { onClick() }
        }
    }

    private class AddNewViewHolder(private val binding: ItemPersonPickerAddNewBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(onClick: () -> Unit) {
            binding.root.setOnClickListener { onClick() }
        }
    }

    private const val VIEW_TYPE_PERSON = 0
    private const val VIEW_TYPE_ADD_NEW = 1
}
