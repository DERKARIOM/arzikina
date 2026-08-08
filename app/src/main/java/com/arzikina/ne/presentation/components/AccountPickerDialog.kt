package com.arzikina.ne.presentation.components

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arzikina.ne.R
import com.arzikina.ne.databinding.ItemAccountPickerBinding
import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.CurrencyAmount
import com.arzikina.ne.presentation.accounts.AccountIconMapper
import com.arzikina.ne.util.Money
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Dialogue de sélection d'un compte (icône + solde courant), utilisé par le
 * formulaire de transaction à la place d'un menu déroulant classique — voir
 * maquette "PERSONNALISATION – AJOUT DE TRANSACTION". Générique/sans état
 * propre (comme [ConfirmDialogs]) : pas besoin d'un composant réifié pour un
 * simple `AlertDialog`.
 *
 * [balanceFor] fournit le solde COURANT de chaque compte (pas
 * [Account.initialBalance]) : ce composant, générique et réutilisable, ne
 * doit pas dépendre de [com.arzikina.ne.domain.repository.TransactionRepository]
 * pour le calculer lui-même — c'est à l'appelant (déjà en possession de ce
 * calcul, voir [com.arzikina.ne.presentation.accounts.computeCurrentBalances])
 * de le fournir.
 */
object AccountPickerDialog {
    fun show(
        context: Context,
        accounts: List<Account>,
        balanceFor: (Account) -> Long,
        onSelect: (Account) -> Unit
    ) {
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.transaction_form_account_picker_title)
            .setView(recyclerView)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        recyclerView.adapter = object : RecyclerView.Adapter<AccountViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
                val binding = ItemAccountPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return AccountViewHolder(binding)
            }

            override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
                val account = accounts[position]
                holder.bind(account, balanceFor(account)) {
                    onSelect(account)
                    dialog.dismiss()
                }
            }

            override fun getItemCount(): Int = accounts.size
        }

        dialog.show()
    }

    private class AccountViewHolder(private val binding: ItemAccountPickerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(account: Account, currentBalance: Long, onClick: () -> Unit) {
            val context = binding.root.context
            binding.accountIcon.setImageResource(AccountIconMapper.iconFor(account.icon))
            binding.accountIcon.backgroundTintList = ColorStateList.valueOf(account.colorArgb.toInt())
            binding.accountName.text = account.name
            binding.accountBalance.text = context.getString(
                R.string.transaction_form_account_balance,
                Money.format(CurrencyAmount(account.currencyCode, currentBalance))
            )
            binding.root.setOnClickListener { onClick() }
        }
    }
}
