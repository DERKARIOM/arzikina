package com.naniger.arzikina.presentation.shop.sales

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentShopSaleCartBinding
import com.naniger.arzikina.domain.shop.CreateShopAccountError
import com.naniger.arzikina.domain.shop.ProductQuantityFormat
import com.naniger.arzikina.presentation.components.displayName
import com.naniger.arzikina.presentation.shop.ShopFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Panier de vente. Écran « passif » : toute la logique est dans [SaleCartViewModel]. Les champs texte
 * (remise, client) ne sont écrits qu'une fois à l'ouverture pour ne jamais déplacer le curseur pendant la saisie.
 */
@AndroidEntryPoint
class SaleCartFragment : Fragment(R.layout.fragment_shop_sale_cart), CartLineActions {

    private val viewModel: SaleCartViewModel by viewModels()
    private var binding: FragmentShopSaleCartBinding? = null
    private val adapter = SaleCartAdapter(this)

    /** Fenêtre « Nouveau compte Boutique » en cours d'affichage, et son champ (pour y afficher une erreur de nom). */
    private var createAccountDialog: AlertDialog? = null
    private var accountNameLayout: TextInputLayout? = null

    private val chipToMode by lazy {
        mapOf(
            R.id.chipDiscountNone to DiscountMode.NONE,
            R.id.chipDiscountPercent to DiscountMode.PERCENT,
            R.id.chipDiscountFixed to DiscountMode.FIXED
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentShopSaleCartBinding.bind(view)
        binding = b

        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.linesList.layoutManager = LinearLayoutManager(requireContext())
        b.linesList.adapter = adapter
        b.linesList.itemAnimator = null
        b.addProductButton.setOnClickListener { pickProduct() }

        if (b.discountInput.text.isNullOrEmpty()) b.discountInput.setText(viewModel.initialDiscountInput)
        if (b.customerNameInput.text.isNullOrEmpty()) b.customerNameInput.setText(viewModel.initialCustomerName)
        if (b.customerPhoneInput.text.isNullOrEmpty()) b.customerPhoneInput.setText(viewModel.initialCustomerPhone)
        b.discountInput.doAfterTextChanged { viewModel.onDiscountInputChange(it?.toString().orEmpty()) }
        b.customerNameInput.doAfterTextChanged { viewModel.onCustomerNameChange(it?.toString().orEmpty()) }
        b.customerPhoneInput.doAfterTextChanged { viewModel.onCustomerPhoneChange(it?.toString().orEmpty()) }
        b.discountChips.setOnCheckedStateChangeListener { _, checkedIds ->
            checkedIds.firstOrNull()?.let { id -> chipToMode[id]?.let(viewModel::onDiscountModeChange) }
        }
        b.accountCard.setOnClickListener { chooseAccount() }
        b.createShopAccountButton.setOnClickListener { showCreateAccountDialog() }
        b.confirmButton.setOnClickListener { viewModel.confirm() }

        setFragmentResultListener(ShopPickResult.REQUEST_KEY) { _, result ->
            val productId = result.getLong(ShopPickResult.KEY_PRODUCT_ID, 0L)
            if (productId != 0L) viewModel.addProduct(productId)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
                launch {
                    viewModel.isCreatingAccount.collect { creating ->
                        createAccountDialog?.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = !creating
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        createAccountDialog?.dismiss()
        createAccountDialog = null
        accountNameLayout = null
        binding?.linesList?.adapter = null
        binding = null
    }

    // --- CartLineActions -------------------------------------------------------------------------

    override fun onIncrement(line: CartLineUi) = viewModel.increment(line.productId)

    override fun onDecrement(line: CartLineUi) = viewModel.decrement(line.productId)

    override fun onRemove(line: CartLineUi) = viewModel.remove(line.productId)

    override fun onEditQuantity(line: CartLineUi) {
        val context = requireContext()
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(ProductQuantityFormat.formatForInput(line.quantityMilli))
            setSelection(text.length)
        }
        val padding = resources.getDimensionPixelSize(R.dimen.spacing_m)
        val container = FrameLayout(context).apply { setPadding(padding, padding / 2, padding, 0); addView(input) }
        MaterialAlertDialogBuilder(context)
            .setTitle(line.name)
            .setMessage(R.string.shop_cart_quantity_dialog_message)
            .setView(container)
            .setPositiveButton(R.string.shop_cart_quantity_apply) { _, _ ->
                val quantity = ProductQuantityFormat.parse(input.text.toString())
                if (quantity == null) {
                    showError(SaleCartError.QUANTITY)
                } else {
                    viewModel.setQuantity(line.productId, quantity)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    // --- Rendu -----------------------------------------------------------------------------------

    private fun pickProduct() =
        findNavController().navigate(R.id.shopProductsFragment, bundleOf(ShopPickResult.ARG_PICK_MODE to true))

    private fun chooseAccount() {
        val accounts = viewModel.uiState.value.accounts
        if (accounts.isEmpty()) return
        val labels = accounts.map { it.displayName(requireContext()) }.toTypedArray()
        val selected = accounts.indexOfFirst { it.id == viewModel.uiState.value.selectedAccount?.id }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.shop_cart_account)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                viewModel.onAccountChosen(accounts[which].id)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun render(state: SaleCartUiState) {
        val b = binding ?: return
        val currency = state.currencyCode
        adapter.submit(state.lines, currency)
        val hasLines = state.lines.isNotEmpty()
        b.emptyText.visibility = if (hasLines || !state.ready) View.GONE else View.VISIBLE
        b.linesList.visibility = if (hasLines) View.VISIBLE else View.GONE
        b.optionsGroup.visibility = if (hasLines) View.VISIBLE else View.GONE

        val mode = state.discountMode
        val chipId = chipToMode.entries.first { it.value == mode }.key
        if (b.discountChips.checkedChipId != chipId) b.discountChips.check(chipId)
        b.discountCard.visibility = if (mode == DiscountMode.NONE) View.GONE else View.VISIBLE
        b.discountLayout.suffixText = when (mode) {
            DiscountMode.PERCENT -> "%"
            DiscountMode.FIXED -> if (currency.isEmpty()) null else com.naniger.arzikina.util.Money.symbolOf(currency)
            DiscountMode.NONE -> null
        }
        b.discountLayout.error = if (state.discountInvalid) getString(R.string.shop_cart_discount_invalid) else null

        b.accountName.text = state.selectedAccount?.displayName(requireContext()).orEmpty()
        b.accountCard.visibility = if (state.selectedAccount != null) View.VISIBLE else View.GONE
        // Aucun compte Boutique éligible : on propose d'en créer un ici même (seul un compte Boutique encaisse).
        b.noAccountGroup.visibility = if (state.ready && state.needsShopAccount) View.VISIBLE else View.GONE

        b.subtotalValue.text = ShopFormat.money(state.subtotalMinor, currency)
        val hasDiscount = state.discountMinor > 0L
        b.discountRow.visibility = if (hasDiscount) View.VISIBLE else View.GONE
        b.discountValue.text = "- " + ShopFormat.money(state.discountMinor, currency)
        b.totalValue.text = ShopFormat.money(state.totalMinor, currency)
        b.confirmButton.isEnabled = state.canConfirm
        b.progress.visibility = if (state.submitting) View.VISIBLE else View.INVISIBLE
    }

    private fun handleEvent(event: SaleCartEvent) {
        when (event) {
            is SaleCartEvent.Confirmed -> {
                // Le panier est retiré de la pile : « retour » depuis la confirmation ne revalide jamais la vente.
                val options = NavOptions.Builder().setPopUpTo(R.id.saleCartFragment, true).build()
                findNavController().navigate(R.id.shopSaleDetailFragment, bundleOf("saleId" to event.saleId, "autoReceipt" to true), options)
            }
            is SaleCartEvent.Failed -> showError(event.error)
            is SaleCartEvent.ShopAccountCreated -> {
                createAccountDialog?.dismiss()
                binding?.let { Snackbar.make(it.root, getString(R.string.shop_account_created, event.name), Snackbar.LENGTH_LONG).show() }
            }
            is SaleCartEvent.ShopAccountRejected -> showAccountNameError(event.error)
        }
    }

    /** Fenêtre de création du compte Boutique : nom pré-rempli, erreur de nom affichée dans le champ sans fermer la fenêtre. */
    private fun showCreateAccountDialog() {
        if (createAccountDialog?.isShowing == true) return
        val context = requireContext()
        val layout = TextInputLayout(context).apply { hint = getString(R.string.shop_account_dialog_hint) }
        val input = TextInputEditText(layout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(R.string.shop_account_default_name)
            setSelection(text?.length ?: 0)
            doAfterTextChanged { layout.error = null }
        }
        layout.addView(input)
        val padding = resources.getDimensionPixelSize(R.dimen.spacing_m)
        val container = FrameLayout(context).apply { setPadding(padding, padding / 2, padding, 0); addView(layout) }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.shop_account_dialog_title)
            .setMessage(R.string.shop_account_dialog_message)
            .setView(container)
            .setPositiveButton(R.string.shop_account_create, null)
            .setNegativeButton(R.string.action_cancel, null)
            .setOnDismissListener {
                createAccountDialog = null
                accountNameLayout = null
            }
            .create()
        // Écouteur posé APRÈS l'affichage : sinon le bouton ferme la fenêtre avant que le nom soit validé.
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                viewModel.createShopAccount(input.text?.toString().orEmpty())
            }
        }
        createAccountDialog = dialog
        accountNameLayout = layout
        dialog.show()
    }

    private fun showAccountNameError(error: CreateShopAccountError) {
        val message = getString(
            when (error) {
                CreateShopAccountError.NAME_REQUIRED -> R.string.shop_account_err_name_required
                CreateShopAccountError.NAME_TOO_LONG -> R.string.shop_account_err_name_too_long
                CreateShopAccountError.NAME_DUPLICATE -> R.string.shop_account_err_name_duplicate
                CreateShopAccountError.SHOP_NOT_READY -> R.string.shop_cart_err_shop
                CreateShopAccountError.FAILED -> R.string.shop_account_err_failed
            }
        )
        val layout = accountNameLayout
        if (layout != null) layout.error = message else binding?.let { Snackbar.make(it.root, message, Snackbar.LENGTH_LONG).show() }
    }

    private fun showError(error: SaleCartError) {
        val b = binding ?: return
        val message = when (error) {
            SaleCartError.UNAVAILABLE -> R.string.shop_cart_err_unavailable
            SaleCartError.OUT_OF_STOCK -> R.string.shop_cart_err_out_of_stock
            SaleCartError.STOCK_LIMIT -> R.string.shop_cart_err_stock_limit
            SaleCartError.INSUFFICIENT_STOCK -> R.string.shop_cart_err_insufficient
            SaleCartError.QUANTITY -> R.string.shop_cart_err_quantity
            SaleCartError.AMOUNT -> R.string.shop_cart_err_amount
            SaleCartError.ZERO_TOTAL -> R.string.shop_cart_err_zero_total
            SaleCartError.ACCOUNT -> R.string.shop_cart_err_account
            SaleCartError.ACCOUNT_TYPE -> R.string.shop_cart_err_account_type
            SaleCartError.CURRENCY -> R.string.shop_cart_err_currency
            SaleCartError.SHOP -> R.string.shop_cart_err_shop
            SaleCartError.GENERIC -> R.string.shop_cart_err_generic
        }
        Snackbar.make(b.root, message, Snackbar.LENGTH_LONG).show()
    }
}
