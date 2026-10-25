package com.naniger.arzikina.presentation.shop.sales

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentShopSaleDetailBinding
import com.naniger.arzikina.databinding.ItemShopInfoRowBinding
import com.naniger.arzikina.domain.shop.ProductQuantityFormat
import com.naniger.arzikina.domain.shop.SaleCancellation
import com.naniger.arzikina.domain.shop.SaleStatus
import com.naniger.arzikina.presentation.components.displayName
import com.naniger.arzikina.presentation.shop.ShopFormat
import com.naniger.arzikina.util.AppDateFormats
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Détail d'une vente : lignes figées, remise, client, compte crédité, reçu (généré / consulté) et annulation.
 * Les mêmes écrans servent juste après la validation et depuis l'historique.
 */
@AndroidEntryPoint
class ShopSaleDetailFragment : Fragment(R.layout.fragment_shop_sale_detail) {

    private val viewModel: ShopSaleDetailViewModel by viewModels()
    private var binding: FragmentShopSaleDetailBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentShopSaleDetailBinding.bind(view)
        binding = b
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.doneButton.setOnClickListener { findNavController().navigateUp() }
        b.newSaleButton.setOnClickListener {
            val options = NavOptions.Builder().setPopUpTo(R.id.shopSaleDetailFragment, true).build()
            findNavController().navigate(R.id.saleCartFragment, bundleOf(), options)
        }
        b.receiptButton.setOnClickListener {
            viewModel.onReceiptClicked(getString(R.string.shop_receipt_default_customer), getString(R.string.shop_receipt_description_prefix))
        }
        b.cancelSaleButton.setOnClickListener { showCancelDialog() }
        b.completeProfileButton.setOnClickListener { findNavController().navigate(R.id.issuedReceiptFormFragment) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
        viewModel.onScreenShown(getString(R.string.shop_receipt_default_customer), getString(R.string.shop_receipt_description_prefix))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun handleEvent(event: SaleDetailEvent) {
        when (event) {
            is SaleDetailEvent.OpenReceipt ->
                findNavController().navigate(R.id.issuedReceiptDetailFragment, bundleOf("receiptId" to event.receiptId))
            is SaleDetailEvent.Message -> binding?.root?.let { Snackbar.make(it, event.resId, Snackbar.LENGTH_LONG).show() }
        }
    }

    /** Confirmation explicite avant d'annuler : la vente est conservée, marquée annulée, avec motif facultatif. */
    private fun showCancelDialog() {
        val context = requireContext()
        val input = EditText(context).apply {
            setHint(R.string.shop_sale_cancel_reason_hint)
            maxLines = 3
            filters = arrayOf(android.text.InputFilter.LengthFilter(SaleCancellation.MAX_REASON_LENGTH))
        }
        val padding = resources.getDimensionPixelSize(R.dimen.spacing_m)
        val container = LinearLayout(context).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.shop_sale_cancel_title)
            .setMessage(R.string.shop_sale_cancel_message)
            .setView(container)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.shop_sale_cancel_confirm) { _, _ -> viewModel.cancelSale(input.text?.toString()) }
            .show()
    }

    private fun render(state: SaleDetailUiState) {
        val b = binding ?: return
        val loaded = state as? SaleDetailUiState.Loaded
        b.content.visibility = if (loaded != null) View.VISIBLE else View.INVISIBLE
        b.notFoundText.visibility = if (state is SaleDetailUiState.NotFound) View.VISIBLE else View.GONE
        loaded ?: return

        val sale = loaded.sale
        val currency = sale.currencyCode
        val cancelled = sale.status == SaleStatus.CANCELLED
        val context = requireContext()
        b.reference.text = sale.reference
        b.total.text = ShopFormat.money(sale.totalMinor, currency)
        b.statusBadge.showSaleStatus(sale.status)
        b.headline.setText(if (cancelled) R.string.shop_sale_cancelled_headline else R.string.shop_sale_recorded)
        val accent = ContextCompat.getColor(context, if (cancelled) R.color.shop_stock_out else R.color.shop_stock_normal)
        b.total.setTextColor(accent)
        b.successIcon.backgroundTintList = ColorStateList.valueOf(accent)

        val rows = b.rows
        rows.removeAllViews()
        sale.lines.forEach { line ->
            addRow(
                rows,
                "${ProductQuantityFormat.format(line.quantityMilli)} × ${line.productName}",
                ShopFormat.money(line.lineTotalMinor, currency)
            )
        }
        if (sale.discountMinor > 0L) {
            addRow(rows, getString(R.string.shop_cart_subtotal), ShopFormat.money(sale.subtotalMinor, currency))
            addRow(rows, getString(R.string.shop_cart_discount), "- " + ShopFormat.money(sale.discountMinor, currency))
        }
        addRow(rows, getString(R.string.shop_sale_date), formatMoment(sale.soldAt))
        sale.paymentMethod?.let { addRow(rows, getString(R.string.shop_sale_payment_method), getString(it.labelRes())) }
        loaded.account?.let { addRow(rows, getString(R.string.shop_sale_account), it.displayName(context)) }
        sale.customerName?.let { addRow(rows, getString(R.string.shop_cart_customer_name), it) }
        sale.customerPhone?.let { addRow(rows, getString(R.string.shop_cart_customer_phone), it) }
        sale.cancelledAt?.let { addRow(rows, getString(R.string.shop_sale_cancelled_on), formatMoment(it)) }
        sale.cancelReason?.let { addRow(rows, getString(R.string.shop_sale_cancel_reason), it) }

        val hasReceipt = sale.issuedReceiptId != null
        b.receiptButton.setText(if (hasReceipt) R.string.shop_receipt_view else R.string.shop_receipt_generate)
        // Pas de nouveau reçu pour une vente annulée ; un reçu déjà émis reste consultable.
        b.receiptButton.visibility = if (hasReceipt || loaded.canIssueReceipt) View.VISIBLE else View.GONE
        b.receiptButton.isEnabled = !loaded.busy
        b.cancelSaleButton.visibility = if (loaded.canCancel) View.VISIBLE else View.GONE
        b.cancelSaleButton.isEnabled = !loaded.busy
        val showProfileHint = loaded.profileIncomplete && loaded.canIssueReceipt
        b.profileHint.visibility = if (showProfileHint) View.VISIBLE else View.GONE
        b.completeProfileButton.visibility = if (showProfileHint) View.VISIBLE else View.GONE
        b.newSaleButton.visibility = if (cancelled) View.GONE else View.VISIBLE
    }

    private fun formatMoment(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(AppDateFormats.mediumDate(requireContext()))

    private fun addRow(container: LinearLayout, label: String, value: String) {
        val row = ItemShopInfoRowBinding.inflate(LayoutInflater.from(requireContext()), container, false)
        row.rowLabel.text = label
        row.rowValue.text = value
        container.addView(row.root)
    }
}
