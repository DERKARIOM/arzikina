package com.naniger.arzikina.presentation.auth.currency

import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.ViewCurrencyPageBinding
import com.naniger.arzikina.domain.currency.CurrencyCatalog

/**
 * Pilote la page « Choisissez votre devise » ([ViewCurrencyPageBinding]), partagée par l'inscription classique et
 * l'écran Google. SANS état propre : l'appelant détient la [CurrencySelection] et appelle [render].
 */
class CurrencyPageController(
    private val binding: ViewCurrencyPageBinding,
    onSelected: (String) -> Unit,
    private val onQueryChange: (String) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    private val adapter = CurrencyAdapter(onSelected)

    init {
        binding.currencyList.layoutManager = LinearLayoutManager(binding.root.context)
        binding.currencyList.adapter = adapter
        binding.currencyList.itemAnimator = null
        binding.currencySearchInput.doAfterTextChanged { onQueryChange(it?.toString().orEmpty()) }
        binding.currencyContinueButton.setOnClickListener { onContinue() }
        binding.currencyToolbar.setNavigationOnClickListener { onBack() }
    }

    fun render(selection: CurrencySelection, busy: Boolean) {
        val context = binding.root.context
        if (binding.currencySearchInput.text?.toString() != selection.query) {
            binding.currencySearchInput.setText(selection.query)
        }
        val rows = CurrencyCatalog.search(selection.query) { it.displayName(context) }.map {
            CurrencyRow(it.code, it.displayName(context), it.symbol, selected = it.code == selection.code)
        }
        adapter.submitList(rows)
        binding.currencyEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        binding.currencySearchLayout.isEnabled = !busy
        binding.currencyList.alpha = if (busy) DISABLED_ALPHA else 1f
        binding.currencyList.isEnabled = !busy
        binding.currencyContinueButton.isEnabled = !busy
        binding.currencyContinueButton.text = if (busy) "" else context.getString(R.string.currency_continue)
        binding.currencyProgress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.currencyToolbar.navigationIcon?.alpha = if (busy) 80 else 255
    }

    private companion object {
        const val DISABLED_ALPHA = 0.5f
    }
}
