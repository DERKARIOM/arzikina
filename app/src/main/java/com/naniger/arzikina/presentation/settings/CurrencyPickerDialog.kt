package com.naniger.arzikina.presentation.settings

import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.View
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.DialogCurrencyPickerBinding
import com.naniger.arzikina.domain.currency.CurrencyCatalog
import com.naniger.arzikina.presentation.auth.currency.CurrencyAdapter
import com.naniger.arzikina.presentation.auth.currency.CurrencyRow
import com.naniger.arzikina.presentation.auth.currency.CurrencySelection
import com.naniger.arzikina.presentation.auth.currency.displayName

/**
 * Changement de la devise principale depuis Paramètres : sélecteur avec recherche (même catalogue, même liste et
 * même adaptateur que l'étape d'inscription), puis AVERTISSEMENT obligatoire avant d'appliquer ([showCurrencyChangeWarning]).
 * Retoucher la devise déjà active ne déclenche rien.
 */
fun Fragment.showCurrencyChangeFlow(current: String, onConfirmed: (String) -> Unit) {
    val context = requireContext()
    val binding = DialogCurrencyPickerBinding.inflate(layoutInflater)
    var selection = CurrencySelection(code = current)
    var dialog: AlertDialog? = null

    val adapter = CurrencyAdapter { code ->
        dialog?.dismiss()
        if (code != current) showCurrencyChangeWarning(current, code, onConfirmed)
    }
    binding.currencyPickerList.layoutManager = LinearLayoutManager(context)
    binding.currencyPickerList.adapter = adapter
    binding.currencyPickerList.itemAnimator = null

    fun render() {
        val rows = CurrencyCatalog.search(selection.query) { it.displayName(context) }.map {
            CurrencyRow(it.code, it.displayName(context), it.symbol, selected = it.code == current)
        }
        adapter.submitList(rows)
        binding.currencyPickerEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }
    binding.currencyPickerSearchInput.doAfterTextChanged {
        selection = selection.search(it?.toString().orEmpty())
        render()
    }
    render()

    dialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.settings_dialog_currency_title)
        .setView(binding.root)
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}

/**
 * Explique, AVANT de modifier quoi que ce soit, que changer de devise ne convertit aucun montant (cahier des charges
 * « Modification ultérieure de la devise »).
 */
private fun Fragment.showCurrencyChangeWarning(from: String, to: String, onConfirmed: (String) -> Unit) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.settings_currency_change_title)
        .setMessage(getString(R.string.settings_currency_change_message, from, to))
        .setNegativeButton(R.string.action_cancel, null)
        .setPositiveButton(R.string.settings_currency_change_confirm) { _, _ -> onConfirmed(to) }
        .show()
}
