package com.naniger.arzikina.presentation.shop.stock

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentShopStockMovementBinding
import com.naniger.arzikina.domain.shop.StockChangeError
import com.naniger.arzikina.domain.shop.StockMovementType
import com.naniger.arzikina.presentation.shop.ShopFormat
import com.naniger.arzikina.presentation.shop.labelRes
import com.naniger.arzikina.util.Money
import com.naniger.arzikina.util.MoneyInputFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Saisie d'un mouvement de stock manuel (réapprovisionnement, retour, perte, correction). */
@AndroidEntryPoint
class ShopStockMovementFragment : Fragment(R.layout.fragment_shop_stock_movement) {

    private val viewModel: ShopStockMovementViewModel by viewModels()
    private var binding: FragmentShopStockMovementBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentShopStockMovementBinding.bind(view)
        binding = b
        val type = viewModel.type

        b.toolbar.setTitle(type.labelRes())
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.quantityLayout.hint = getString(quantityLabel(type))
        b.reasonLayout.hint = getString(
            if (type.requiresReason) R.string.shop_stock_reason else R.string.shop_stock_reason_optional
        )
        // Coût et référence n'ont de sens que pour une livraison.
        val isRestock = type == StockMovementType.RESTOCK
        b.unitCostCard.visibility = if (isRestock) View.VISIBLE else View.GONE
        b.updatePriceCheck.visibility = if (isRestock) View.VISIBLE else View.GONE
        b.referenceCard.visibility = if (isRestock) View.VISIBLE else View.GONE
        MoneyInputFormatter.attach(b.unitCostInput) { }

        b.saveButton.setOnClickListener {
            viewModel.onSave(
                StockMovementFormInput(
                    type = type,
                    quantity = b.quantityInput.text.toString(),
                    reason = b.reasonInput.text.toString(),
                    reference = b.referenceInput.text.toString(),
                    unitCost = b.unitCostInput.text.toString(),
                    updatePurchasePrice = b.updatePriceCheck.isChecked
                )
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun quantityLabel(type: StockMovementType): Int = when (type) {
        StockMovementType.CUSTOMER_RETURN -> R.string.shop_stock_qty_return
        StockMovementType.DAMAGED_OR_LOST -> R.string.shop_stock_qty_damaged
        StockMovementType.ADJUSTMENT -> R.string.shop_stock_qty_adjustment
        else -> R.string.shop_stock_qty_restock
    }

    private fun render(state: StockMovementFormState) {
        val b = binding ?: return
        val product = state.product
        b.productName.text = product?.name.orEmpty()
        b.currentStock.text = product?.let {
            getString(R.string.shop_stock_current, ShopFormat.quantity(requireContext(), it.stockMilli, it.unit))
        }.orEmpty()
        b.unitCostLayout.suffixText = state.currencyCode.takeIf { it.isNotEmpty() }?.let(Money::symbolOf)
        b.saveButton.isEnabled = !state.saving && product != null
        b.progress.visibility = if (state.saving) View.VISIBLE else View.INVISIBLE

        val errors = state.errors
        b.quantityLayout.error = quantityError(state.type, errors)?.let(::getString)
        b.reasonLayout.error = if (StockChangeError.REASON_REQUIRED in errors) getString(R.string.shop_stock_err_reason) else null
        b.unitCostLayout.error = if (StockChangeError.COST_INVALID in errors) getString(R.string.shop_stock_err_cost) else null
    }

    /** Message du champ quantité pour la première règle violée, ou `null`. */
    private fun quantityError(type: StockMovementType, errors: Set<StockChangeError>): Int? = when {
        StockChangeError.ZERO_QUANTITY in errors ->
            if (type == StockMovementType.ADJUSTMENT) R.string.shop_stock_err_adjust_same else R.string.shop_stock_err_quantity
        StockChangeError.INSUFFICIENT_STOCK in errors -> R.string.shop_stock_err_insufficient
        StockChangeError.FRACTION_NOT_ALLOWED in errors -> R.string.shop_stock_err_fraction
        StockChangeError.QUANTITY_TOO_LARGE in errors -> R.string.shop_stock_err_too_large
        StockChangeError.STOCK_OVERFLOW in errors -> R.string.shop_stock_err_overflow
        StockChangeError.WRONG_DIRECTION in errors -> R.string.shop_stock_err_quantity
        else -> null
    }

    private fun handleEvent(event: StockMovementFormEvent) {
        val b = binding ?: return
        when (event) {
            StockMovementFormEvent.Saved -> findNavController().navigateUp()
            StockMovementFormEvent.Failed -> Snackbar.make(b.root, R.string.shop_stock_failed, Snackbar.LENGTH_LONG).show()
        }
    }
}
