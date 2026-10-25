package com.naniger.arzikina.presentation.shop.form

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentShopProductFormBinding
import com.naniger.arzikina.domain.shop.ProductCategory
import com.naniger.arzikina.domain.shop.ProductError
import com.naniger.arzikina.domain.shop.ProductImageException
import com.naniger.arzikina.domain.shop.ProductUnit
import com.naniger.arzikina.presentation.shop.labelRes
import com.naniger.arzikina.presentation.shop.showPickedImage
import com.naniger.arzikina.presentation.shop.showPlaceholder
import com.naniger.arzikina.presentation.shop.showProductImage
import com.naniger.arzikina.util.AppDateFormats
import com.naniger.arzikina.util.Money
import com.naniger.arzikina.util.MoneyInputFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

/**
 * Création / modification d'un produit. La validation est faite par [ProductFormParser] (et, au final,
 * par le repository) ; ce fragment ne fait qu'afficher les erreurs sur les champs concernés.
 */
@AndroidEntryPoint
class ShopProductFormFragment : Fragment(R.layout.fragment_shop_product_form) {

    private val viewModel: ShopProductFormViewModel by viewModels()
    private var binding: FragmentShopProductFormBinding? = null

    // Dernier état affiché : sert aux listeners des menus déroulants (position → identifiant).
    private var categories: List<ProductCategory> = emptyList()
    private var currentState: ProductFormUiState? = null

    /**
     * Fichier de l'appareil photo, conservé dans [onSaveInstanceState] : Android peut détruire le processus
     * pendant que l'appareil photo est au premier plan, et le résultat de [takePhoto] ne contient pas l'URI.
     */
    private var pendingCaptureUri: Uri? = null

    /** Galerie : sélecteur de photos du système, aucune permission. */
    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onImagePicked(it.toString()) }
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val captureUri = pendingCaptureUri
        pendingCaptureUri = null
        if (saved && captureUri != null) viewModel.onImagePicked(captureUri.toString())
    }

    /** Demandée seulement quand l'utilisateur choisit « Appareil photo ». */
    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCamera()
        } else {
            binding?.let { Snackbar.make(it.root, R.string.profile_photo_camera_permission_denied, Snackbar.LENGTH_LONG).show() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingCaptureUri = savedInstanceState?.let { BundleCompat.getParcelable(it, STATE_CAPTURE_URI, Uri::class.java) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingCaptureUri?.let { outState.putParcelable(STATE_CAPTURE_URI, it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentShopProductFormBinding.bind(view)
        binding = b

        b.toolbar.setTitle(if (viewModel.isEdit) R.string.shop_form_title_edit else R.string.shop_form_title_add)
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        // Création uniquement : le stock d'un produit existant ne se modifie jamais depuis la fiche.
        (b.initialStockLayout.parent as View).visibility = if (viewModel.isEdit) View.GONE else View.VISIBLE

        MoneyInputFormatter.attach(b.salePriceInput) { }
        MoneyInputFormatter.attach(b.purchasePriceInput) { }
        b.categoryField.dropdownLayout.hint = getString(R.string.shop_form_category)
        b.unitField.dropdownLayout.hint = getString(R.string.shop_form_unit)
        setUpUnitDropdown(b)

        b.photoPreview.setOnClickListener { showPhotoChoices() }
        b.photoChooseButton.setOnClickListener { showPhotoChoices() }
        b.photoRemoveButton.setOnClickListener { viewModel.onImageRemoved() }
        b.expiryButton.setOnClickListener { pickExpiry() }
        b.expiryClearButton.setOnClickListener { viewModel.onExpirySelected(null) }
        b.saveButton.setOnClickListener { viewModel.onSave(readInput(b)) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
                launch {
                    viewModel.prefill.collect {
                        if (it != null) viewModel.consumePrefill()?.let { input -> fill(input) }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun showPhotoChoices() {
        val labels = arrayOf(getString(R.string.shop_photo_gallery), getString(R.string.shop_photo_camera))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.shop_photo_title)
            .setItems(labels) { _, which ->
                if (which == 0) {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                } else {
                    onTakePhotoSelected()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun onTakePhotoSelected() {
        val granted = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val captureUri = ProductPhotoCapture.createUri(requireContext())
        pendingCaptureUri = captureUri
        try {
            takePhoto.launch(captureUri)
        } catch (e: ActivityNotFoundException) {
            pendingCaptureUri = null
            binding?.let { Snackbar.make(it.root, R.string.shop_photo_no_camera, Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun renderPhoto(b: FragmentShopProductFormBinding, image: ProductFormImage) {
        when (image) {
            ProductFormImage.None -> b.photoPreview.showPlaceholder()
            is ProductFormImage.Picked -> b.photoPreview.showPickedImage(image.uri)
            is ProductFormImage.Stored -> b.photoPreview.showProductImage(image.file)
        }
        val hasPhoto = image !is ProductFormImage.None
        b.photoChooseButton.setText(if (hasPhoto) R.string.shop_photo_change else R.string.shop_photo_add)
        b.photoRemoveButton.visibility = if (hasPhoto) View.VISIBLE else View.GONE
    }

    private fun readInput(b: FragmentShopProductFormBinding) = ProductFormInput(
        name = b.nameInput.text.toString(),
        description = b.descriptionInput.text.toString(),
        sku = b.skuInput.text.toString(),
        barcode = b.barcodeInput.text.toString(),
        purchasePrice = b.purchasePriceInput.text.toString(),
        salePrice = b.salePriceInput.text.toString(),
        initialStock = b.initialStockInput.text.toString(),
        minStock = b.minStockInput.text.toString()
    )

    private fun fill(input: ProductFormInput) {
        val b = binding ?: return
        b.nameInput.setText(input.name)
        b.descriptionInput.setText(input.description)
        b.skuInput.setText(input.sku)
        b.barcodeInput.setText(input.barcode)
        b.purchasePriceInput.setText(input.purchasePrice)
        b.salePriceInput.setText(input.salePrice)
        b.minStockInput.setText(input.minStock)
    }

    private fun setUpUnitDropdown(b: FragmentShopProductFormBinding) {
        val units = ProductUnit.entries
        val labels = units.map { getString(it.labelRes()) }
        b.unitField.dropdownInput.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
        )
        b.unitField.dropdownInput.setOnItemClickListener { _, _, position, _ -> viewModel.onUnitSelected(units[position]) }
    }

    /** Position 0 = aucune catégorie, dernière = « Nouvelle catégorie… », entre les deux = les catégories. */
    private fun setUpCategoryDropdown(b: FragmentShopProductFormBinding, state: ProductFormUiState) {
        categories = state.categories
        val labels = listOf(getString(R.string.shop_form_category_none)) +
            state.categories.map { it.name } + getString(R.string.shop_form_category_new)
        b.categoryField.dropdownInput.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
        )
        b.categoryField.dropdownInput.setOnItemClickListener { _, _, position, _ ->
            when {
                position == 0 -> viewModel.onCategorySelected(null)
                position == labels.lastIndex -> {
                    // Remet l'affichage sur le choix actuel : la création n'est pas une sélection.
                    showCategoryName(b, currentState?.categoryId)
                    promptNewCategory()
                }
                else -> viewModel.onCategorySelected(categories[position - 1].id)
            }
        }
    }

    private fun showCategoryName(b: FragmentShopProductFormBinding, categoryId: Long?) {
        val name = categories.firstOrNull { it.id == categoryId }?.name
            ?: getString(R.string.shop_form_category_none)
        b.categoryField.dropdownInput.setText(name, false)
    }

    private fun promptNewCategory() {
        val input = EditText(requireContext()).apply {
            setHint(R.string.shop_form_category_new_hint)
            setSingleLine()
        }
        val padding = resources.getDimensionPixelSize(R.dimen.spacing_m)
        val container = FrameLayout(requireContext()).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.shop_form_category_new_title)
            .setView(container)
            .setPositiveButton(R.string.action_save) { _, _ ->
                input.text.toString().trim().takeIf { it.isNotEmpty() }?.let(viewModel::onCreateCategory)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** `MaterialDatePicker` raisonne en UTC : la date est conservée comme minuit UTC du jour choisi. */
    private fun pickExpiry() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(currentState?.expiryDate ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        picker.addOnPositiveButtonClickListener { viewModel.onExpirySelected(it) }
        picker.show(childFragmentManager, "shop_expiry")
    }

    private fun render(state: ProductFormUiState) {
        val b = binding ?: return
        currentState = state
        setUpCategoryDropdown(b, state)
        showCategoryName(b, state.categoryId)
        b.unitField.dropdownInput.setText(getString(state.unit.labelRes()), false)

        val suffix = Money.symbolOf(state.currencyCode).takeIf { state.currencyCode.isNotEmpty() }
        b.salePriceLayout.suffixText = suffix
        b.purchasePriceLayout.suffixText = suffix

        b.expiryButton.text = state.expiryDate?.let {
            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(AppDateFormats.mediumDate(requireContext()))
        } ?: getString(R.string.shop_form_expiry_none)
        b.expiryClearButton.visibility = if (state.expiryDate == null) View.GONE else View.VISIBLE

        renderPhoto(b, state.image)
        b.saveButton.isEnabled = !state.saving
        b.progress.visibility = if (state.saving) View.VISIBLE else View.INVISIBLE
        showErrors(b, state.errors)
    }

    private fun showErrors(b: FragmentShopProductFormBinding, errors: Set<ProductError>) {
        fun TextInputLayout.showError(vararg matching: Pair<ProductError, Int>) {
            this.error = matching.firstOrNull { it.first in errors }?.let { getString(it.second) }
        }
        b.nameLayout.showError(
            ProductError.NAME_REQUIRED to R.string.shop_form_error_name_required,
            ProductError.NAME_TOO_LONG to R.string.shop_form_error_name_too_long
        )
        b.skuLayout.showError(ProductError.SKU_TOO_LONG to R.string.shop_form_error_code_too_long)
        b.barcodeLayout.showError(ProductError.BARCODE_TOO_LONG to R.string.shop_form_error_code_too_long)
        b.salePriceLayout.showError(ProductError.SALE_PRICE_INVALID to R.string.shop_form_error_sale_price)
        b.purchasePriceLayout.showError(ProductError.PURCHASE_PRICE_INVALID to R.string.shop_form_error_purchase_price)
        b.initialStockLayout.showError(ProductError.INITIAL_STOCK_INVALID to R.string.shop_form_error_quantity)
        b.minStockLayout.showError(ProductError.MIN_STOCK_INVALID to R.string.shop_form_error_quantity)
        b.descriptionLayout.showError(ProductError.DESCRIPTION_TOO_LONG to R.string.shop_form_error_description_too_long)
    }

    private fun handleEvent(event: ProductFormEvent) {
        val b = binding ?: return
        when (event) {
            ProductFormEvent.Saved -> findNavController().navigateUp()
            ProductFormEvent.SaveFailed -> Snackbar.make(b.root, R.string.shop_form_save_failed, Snackbar.LENGTH_LONG).show()
            ProductFormEvent.NotFound -> {
                Snackbar.make(b.root, R.string.shop_form_not_found, Snackbar.LENGTH_LONG).show()
                findNavController().navigateUp()
            }
            is ProductFormEvent.ImageFailed -> {
                val message = when (event.reason) {
                    ProductImageException.Reason.UNSUPPORTED_TYPE -> R.string.shop_photo_error_type
                    ProductImageException.Reason.TOO_LARGE -> R.string.shop_photo_error_large
                    ProductImageException.Reason.UNREADABLE -> R.string.shop_photo_error_unreadable
                }
                Snackbar.make(b.root, message, Snackbar.LENGTH_LONG).show()
            }
            is ProductFormEvent.DuplicateBarcode -> showDuplicate(
                b, R.string.shop_form_error_duplicate_barcode, event.existingProductId
            )
            is ProductFormEvent.DuplicateSku -> showDuplicate(
                b, R.string.shop_form_error_duplicate_sku, event.existingProductId
            )
        }
    }

    /** Signale le doublon et propose d'ouvrir le produit existant. */
    private fun showDuplicate(b: FragmentShopProductFormBinding, messageRes: Int, existingId: Long) {
        Snackbar.make(b.root, messageRes, Snackbar.LENGTH_LONG)
            .setAction(R.string.shop_form_open_existing) {
                findNavController().navigate(R.id.shopProductDetailFragment, bundleOf("productId" to existingId))
            }
            .show()
    }

    private companion object {
        const val STATE_CAPTURE_URI = "shop_product_capture_uri"
    }
}
