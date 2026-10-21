package com.naniger.arzikina.presentation.utilities.issuedreceipts

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.DialogSignaturePadBinding
import com.naniger.arzikina.databinding.FragmentIssuedReceiptFormBinding
import com.naniger.arzikina.databinding.ItemIssuedReceiptLineBinding
import com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptError
import com.naniger.arzikina.domain.issuedreceipt.ReceiptCalculator
import com.naniger.arzikina.domain.issuedreceipt.pdf.PdfLogo
import com.naniger.arzikina.domain.issuedreceipt.pdf.ReceiptAmountFormatter
import com.naniger.arzikina.domain.issuedreceipt.pdf.ReceiptPdfContentBuilder
import com.naniger.arzikina.domain.model.PaymentMethod
import com.naniger.arzikina.domain.model.ReceiptLine
import com.naniger.arzikina.domain.model.SupportedCurrency
import com.naniger.arzikina.presentation.components.pickerLabel
import com.naniger.arzikina.presentation.transactions.displayTextRes
import com.naniger.arzikina.presentation.utilities.issuedreceipts.preview.PreviewLogoLoader
import com.naniger.arzikina.presentation.utilities.issuedreceipts.preview.PreviewLogoSource
import com.naniger.arzikina.util.AppDateFormats
import com.naniger.arzikina.util.Money
import com.naniger.arzikina.util.MoneyInputFormatter
import com.naniger.arzikina.util.appLocale
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Formulaire « Nouveau reçu » (voir [IssuedReceiptFormViewModel]). Deux vues sous la barre :
 * « Formulaire » et « Aperçu » (dessiné par le même moteur que le PDF, mis à jour à chaque frappe).
 * Aucune règle métier ici : l'écran affiche l'état et transmet les saisies.
 */
@AndroidEntryPoint
class IssuedReceiptFormFragment : Fragment(R.layout.fragment_issued_receipt_form) {

    private val viewModel: IssuedReceiptFormViewModel by viewModels()
    private var binding: FragmentIssuedReceiptFormBinding? = null

    /** Logo actuellement chargé pour l'aperçu, et la clé qui l'identifie (évite de le recharger à chaque frappe). */
    private var previewLogo: Bitmap? = null
    private var previewLogoKey: String? = null

    /** Signature de l'aperçu et l'objet qui l'identifie (image en attente ou nom du fichier mémorisé). */
    private var previewSignature: Bitmap? = null
    private var previewSignatureKey: Any? = null

    /** Vues des lignes de détail, par id de ligne (voir [renderLines]). */
    private val lineViews = LinkedHashMap<Long, ItemIssuedReceiptLineBinding>()

    private val pickLogo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onLogoPicked(it.toString()) }
    }

    private val pickSignature = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onSignatureImported(it.toString()) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentIssuedReceiptFormBinding.bind(view)
        binding = b

        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        setUpTabs(b)
        setUpFields(b)
        setUpDetail(b)
        setUpLogoButtons(b)
        setUpSignatureControls(b)
        b.generateButton.setOnClickListener { viewModel.save(generatePdf = true, language = language()) }
        b.saveButton.setOnClickListener { viewModel.save(generatePdf = false, language = language()) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect { render(it) } }
                launch { viewModel.events.collect { handleEvent(it) } }
                launch { observePreview() }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        lineViews.clear()
        previewLogo = null
        previewLogoKey = null
        previewSignature = null
        previewSignatureKey = null
    }

    private fun language(): String = requireContext().appLocale().language

    // --- Mise en place -----------------------------------------------------------------------

    private fun setUpTabs(b: FragmentIssuedReceiptFormBinding) {
        b.tabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val preview = checkedId == R.id.tabPreviewButton
            b.formScroll.visibility = if (preview) View.GONE else View.VISIBLE
            b.previewScroll.visibility = if (preview) View.VISIBLE else View.GONE
        }
    }

    private fun setUpFields(b: FragmentIssuedReceiptFormBinding) {
        text(b.businessNameField.postcardInput, b.businessNameField.postcardInputLayout, R.string.issued_receipt_business_name_label, InputType.TYPE_TEXT_FLAG_CAP_WORDS) { viewModel.onBusinessNameChange(it) }
        text(b.addressField.postcardInput, b.addressField.postcardInputLayout, R.string.issued_receipt_address_label, InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE, lines = 3) { viewModel.onAddressChange(it) }
        text(b.phoneField.postcardInput, b.phoneField.postcardInputLayout, R.string.issued_receipt_phone_label, type = InputType.TYPE_CLASS_PHONE) { viewModel.onPhoneChange(it) }
        text(b.emailField.postcardInput, b.emailField.postcardInputLayout, R.string.issued_receipt_email_label, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) { viewModel.onEmailChange(it) }
        text(b.taxIdField.postcardInput, b.taxIdField.postcardInputLayout, R.string.issued_receipt_tax_id_label, InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) { viewModel.onTaxIdChange(it) }
        text(b.numberField.postcardInput, b.numberField.postcardInputLayout, R.string.issued_receipt_number_label, InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) { viewModel.onNumberChange(it) }
        text(b.customerNameField.postcardInput, b.customerNameField.postcardInputLayout, R.string.issued_receipt_customer_name_label, InputType.TYPE_TEXT_FLAG_CAP_WORDS) { viewModel.onCustomerNameChange(it) }
        text(b.customerPhoneField.postcardInput, b.customerPhoneField.postcardInputLayout, R.string.issued_receipt_customer_phone_label, type = InputType.TYPE_CLASS_PHONE) { viewModel.onCustomerPhoneChange(it) }
        text(b.descriptionField.postcardInput, b.descriptionField.postcardInputLayout, R.string.issued_receipt_description_label, InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE, lines = 5) { viewModel.onDescriptionChange(it) }
        text(b.referenceField.postcardInput, b.referenceField.postcardInputLayout, R.string.issued_receipt_reference_label, InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) { viewModel.onReferenceChange(it) }
        text(b.notesField.postcardInput, b.notesField.postcardInputLayout, R.string.issued_receipt_notes_label, InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE, lines = 4) { viewModel.onNotesChange(it) }
        text(b.issuedByNameField.postcardInput, b.issuedByNameField.postcardInputLayout, R.string.issued_receipt_issued_by_name_label, InputType.TYPE_TEXT_FLAG_CAP_WORDS) { viewModel.onIssuedByNameChange(it) }
        text(b.issuedByTitleField.postcardInput, b.issuedByTitleField.postcardInputLayout, R.string.issued_receipt_issued_by_title_label, InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) { viewModel.onIssuedByTitleChange(it) }

        // Montant : même champ formaté (séparateur de milliers) que le reste de l'application.
        b.amountField.postcardInputLayout.hint = getString(R.string.issued_receipt_amount_label)
        b.amountField.postcardInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        MoneyInputFormatter.attach(b.amountField.postcardInput) { formatted -> viewModel.onAmountChange(formatted) }

        // Devise et mode de paiement : listes déroulantes.
        val currencies = SupportedCurrency.entries
        b.currencyField.dropdownLayout.hint = getString(R.string.issued_receipt_currency_label)
        b.currencyField.dropdownInput.setSimpleItems(currencies.map { it.pickerLabel(requireContext()) }.toTypedArray())
        b.currencyField.dropdownInput.setOnItemClickListener { _, _, position, _ -> viewModel.onCurrencyChange(currencies[position].code) }

        val methods = PaymentMethod.entries
        b.paymentField.dropdownLayout.hint = getString(R.string.issued_receipt_payment_method_label)
        b.paymentField.dropdownInput.setSimpleItems(methods.map { getString(it.displayTextRes()) }.toTypedArray())
        b.paymentField.dropdownInput.setOnItemClickListener { _, _, position, _ -> viewModel.onPaymentMethodChange(methods[position]) }

        // Date d'émission.
        b.dateField.dateFieldLabel.text = getString(R.string.issued_receipt_date_label)
        b.dateRow.setOnClickListener { showDatePicker() }
    }

    /** Configure un champ texte de l'include `item_postcard_text_input` : libellé, type de saisie, lignes, écouteur. */
    private fun text(
        input: EditText,
        layout: com.google.android.material.textfield.TextInputLayout,
        hint: Int,
        flags: Int = 0,
        type: Int = InputType.TYPE_CLASS_TEXT,
        lines: Int = 1,
        onChange: (String) -> Unit
    ) {
        layout.hint = getString(hint)
        input.inputType = type or flags
        if (lines > 1) {
            input.minLines = lines.coerceAtMost(2)
            input.maxLines = lines
            input.isSingleLine = false
        }
        input.doAfterTextChanged { onChange(it?.toString().orEmpty()) }
    }

    /** Détail : interrupteur, ajout de ligne, remise (mode + valeur) et taxe. Les lignes sont gérées par [renderLines]. */
    private fun setUpDetail(b: FragmentIssuedReceiptFormBinding) {
        b.detailSwitch.setOnCheckedChangeListener { _, checked -> viewModel.onDetailedToggle(checked) }
        b.addLineButton.setOnClickListener { viewModel.onAddLine() }
        b.discountGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.onDiscountModeChange(
                when (checkedId) {
                    R.id.discountPercentButton -> DiscountMode.PERCENT
                    R.id.discountFixedButton -> DiscountMode.FIXED
                    else -> DiscountMode.NONE
                }
            )
        }
        b.discountInput.doAfterTextChanged { viewModel.onDiscountChange(it?.toString().orEmpty()) }
        b.taxInput.doAfterTextChanged { viewModel.onTaxChange(it?.toString().orEmpty()) }
    }

    private fun setUpLogoButtons(b: FragmentIssuedReceiptFormBinding) {
        b.logoChooseButton.setOnClickListener { pickLogo.launch("image/*") }
        b.logoRemoveButton.setOnClickListener { viewModel.onLogoRemoved() }
    }

    /** Même conversion que les autres formulaires : jour choisi (UTC côté MaterialDatePicker) relu en date LOCALE. */
    private fun setUpSignatureControls(b: FragmentIssuedReceiptFormBinding) {
        b.signatureDrawButton.setOnClickListener { showSignaturePad() }
        b.signatureImportButton.setOnClickListener { pickSignature.launch("image/*") }
        b.signatureRemoveButton.setOnClickListener { viewModel.onSignatureRemoved() }
        b.signatureSwitch.setOnCheckedChangeListener { _, checked -> viewModel.onSignReceiptChange(checked) }
    }

    /** Boîte de dialogue de dessin : le tracé est exporté puis traité (recadré, réduit) par le ViewModel. */
    private fun showSignaturePad() {
        val pad = DialogSignaturePadBinding.inflate(layoutInflater)
        fun updateButtons() {
            val hasInk = pad.signaturePad.hasInk
            pad.signatureUndoButton.isEnabled = hasInk
            pad.signatureClearButton.isEnabled = hasInk
        }
        pad.signaturePad.onInkChanged = ::updateButtons
        pad.signatureUndoButton.setOnClickListener { pad.signaturePad.undo() }
        pad.signatureClearButton.setOnClickListener { pad.signaturePad.clear() }
        updateButtons()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.issued_receipt_signature_pad_title)
            .setView(pad.root)
            .setNegativeButton(R.string.issued_receipt_signature_pad_cancel, null)
            .setPositiveButton(R.string.issued_receipt_signature_pad_apply, null)
            .create()
        dialog.setOnShowListener {
            // Bouton géré à la main : un tracé vide ne ferme pas la boîte (sinon l'action semblerait perdue).
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val png = pad.signaturePad.exportPng()
                if (png == null) {
                    Snackbar.make(pad.root, R.string.issued_receipt_signature_error_empty, Snackbar.LENGTH_SHORT).show()
                } else {
                    viewModel.onSignatureDrawn(png)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun showDatePicker() {
        val current = viewModel.state.value.input.issuedAt
        val localDate = Instant.ofEpochMilli(current).atZone(ZoneId.systemDefault()).toLocalDate()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.issued_receipt_date_label)
            .setSelection(localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { utcMillis ->
            val day = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            viewModel.onDateChange(day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
            viewModel.refreshSuggestedNumber()
        }
        picker.show(parentFragmentManager, "issued_receipt_date_picker")
    }

    // --- Affichage ---------------------------------------------------------------------------

    private fun render(state: IssuedReceiptFormState) {
        val b = binding ?: return
        val input = state.input
        b.toolbar.setTitle(if (state.editing) R.string.issued_receipt_form_title_edit else R.string.issued_receipt_form_title)

        sync(b.businessNameField.postcardInput, input.businessName)
        sync(b.addressField.postcardInput, input.address)
        sync(b.phoneField.postcardInput, input.phone)
        sync(b.emailField.postcardInput, input.email)
        sync(b.taxIdField.postcardInput, input.taxId)
        sync(b.customerNameField.postcardInput, input.customerName)
        sync(b.customerPhoneField.postcardInput, input.customerPhone)
        sync(b.descriptionField.postcardInput, input.description)
        sync(b.referenceField.postcardInput, input.reference)
        sync(b.notesField.postcardInput, input.notes)
        sync(b.issuedByNameField.postcardInput, input.issuedByName)
        sync(b.issuedByTitleField.postcardInput, input.issuedByTitle)
        sync(b.amountField.postcardInput, input.amountInput)

        // Numéro : vide = automatique (suggestion en indice) ; verrouillé après le premier enregistrement.
        sync(b.numberField.postcardInput, input.numberInput)
        b.numberField.postcardInputLayout.placeholderText = state.suggestedNumber.takeIf { it.isNotEmpty() }
            ?.let { getString(R.string.issued_receipt_number_auto, it) }
        b.numberField.postcardInput.isEnabled = !state.numberLocked
        b.numberField.postcardInputLayout.helperText = getString(R.string.issued_receipt_number_helper)

        b.dateField.dateFieldValue.text = Instant.ofEpochMilli(input.issuedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(AppDateFormats.longDate(requireContext()))

        val currency = SupportedCurrency.entries.firstOrNull { it.code == input.currencyCode }
        val currencyLabel = currency?.pickerLabel(requireContext()) ?: input.currencyCode
        if (b.currencyField.dropdownInput.text?.toString() != currencyLabel) b.currencyField.dropdownInput.setText(currencyLabel, false)
        val methodLabel = getString(input.paymentMethod.displayTextRes())
        if (b.paymentField.dropdownInput.text?.toString() != methodLabel) b.paymentField.dropdownInput.setText(methodLabel, false)

        renderDetail(b, state)
        renderLogoControls(b, state)
        renderSignatureControls(b, state)
        renderErrors(b, state)
        b.generateButton.isEnabled = !state.isSaving
        b.saveButton.isEnabled = !state.isSaving
    }

    // --- Détail ------------------------------------------------------------------------------

    private fun renderDetail(b: FragmentIssuedReceiptFormBinding, state: IssuedReceiptFormState) {
        val input = state.input
        if (b.detailSwitch.isChecked != input.detailed) b.detailSwitch.isChecked = input.detailed
        b.detailGroup.visibility = if (input.detailed) View.VISIBLE else View.GONE
        // En mode détaillé, le montant est calculé : le champ de saisie disparaît.
        b.amountField.root.visibility = if (input.detailed) View.GONE else View.VISIBLE

        val modeButton = when (input.discountMode) {
            DiscountMode.NONE -> R.id.discountNoneButton
            DiscountMode.PERCENT -> R.id.discountPercentButton
            DiscountMode.FIXED -> R.id.discountFixedButton
        }
        if (b.discountGroup.checkedButtonId != modeButton) b.discountGroup.check(modeButton)
        b.discountCard.visibility = if (input.discountMode == DiscountMode.NONE) View.GONE else View.VISIBLE
        b.discountLayout.hint = getString(
            if (input.discountMode == DiscountMode.FIXED) R.string.issued_receipt_discount_fixed_label else R.string.issued_receipt_discount_percent_label
        )
        sync(b.discountInput, input.discountInput)
        sync(b.taxInput, input.taxInput)
        b.discountLayout.error = if (IssuedReceiptError.DISCOUNT_INVALID in state.errors) getString(R.string.issued_receipt_error_pricing) else null
        b.taxLayout.error = if (IssuedReceiptError.TAX_INVALID in state.errors) getString(R.string.issued_receipt_error_pricing) else null

        if (input.detailed) renderLines(b, state)
        renderTotals(b, input)
    }

    /** Réconcilie les vues avec la liste de lignes : ajoute/retire les vues manquantes, synchronise les textes. */
    private fun renderLines(b: FragmentIssuedReceiptFormBinding, state: IssuedReceiptFormState) {
        val lines = state.input.lines
        lineViews.keys.filter { id -> lines.none { it.id == id } }.forEach { id ->
            lineViews.remove(id)?.let { b.linesContainer.removeView(it.root) }
        }
        lines.forEachIndexed { index, line ->
            val row = lineViews.getOrPut(line.id) {
                createLineRow(b, line.id).also { b.linesContainer.addView(it.root, index) }
            }
            sync(row.lineLabelInput, line.label)
            sync(row.lineQuantityInput, line.quantityInput)
            sync(row.linePriceInput, line.unitPriceInput)

            val quantity = FormNumberParser.quantityMilli(line.quantityInput)
            val price = Money.parseToMinorUnits(line.unitPriceInput)
            val total = if (quantity != null && price != null) ReceiptCalculator.lineTotalOrNull(ReceiptLine(line.label, quantity, price)) else null
            row.lineTotalText.text = total?.let {
                getString(R.string.issued_receipt_line_total, ReceiptAmountFormatter.format(it, state.input.currencyCode, it % 100L != 0L))
            }.orEmpty()

            val show = state.showErrors
            row.lineLabelLayout.error = if (show && line.label.isBlank()) getString(R.string.issued_receipt_error_pricing) else null
            row.lineQuantityLayout.error = if (show && (quantity == null || quantity <= 0)) getString(R.string.issued_receipt_error_pricing) else null
            row.linePriceLayout.error = if (show && price == null) getString(R.string.issued_receipt_error_pricing) else null
            row.lineRemoveButton.isEnabled = lines.size > 1
        }
    }

    private fun createLineRow(b: FragmentIssuedReceiptFormBinding, id: Long): ItemIssuedReceiptLineBinding {
        val row = ItemIssuedReceiptLineBinding.inflate(layoutInflater, b.linesContainer, false)
        row.lineLabelInput.doAfterTextChanged { viewModel.onLineLabelChange(id, it?.toString().orEmpty()) }
        row.lineQuantityInput.doAfterTextChanged { viewModel.onLineQuantityChange(id, it?.toString().orEmpty()) }
        MoneyInputFormatter.attach(row.linePriceInput) { viewModel.onLinePriceChange(id, it) }
        row.lineRemoveButton.setOnClickListener { viewModel.onRemoveLine(id) }
        return row
    }

    /** Totaux calculés en direct (sous-total, remise, taxe, total à payer). */
    private fun renderTotals(b: FragmentIssuedReceiptFormBinding, input: IssuedReceiptFormInput) {
        if (!input.detailed) return
        val totals = IssuedReceiptDraftFactory.totalsOf(input)
        if (totals == null) {
            b.totalsText.text = getString(R.string.issued_receipt_totals_invalid)
            return
        }
        val decimals = listOf(totals.subtotalMinor, totals.discountMinor, totals.taxMinor, totals.totalMinor).any { it % 100L != 0L }
        fun money(v: Long) = ReceiptAmountFormatter.format(v, input.currencyCode, decimals)
        b.totalsText.text = buildList {
            add(getString(R.string.issued_receipt_totals_subtotal, money(totals.subtotalMinor)))
            if (totals.discountMinor > 0) add(getString(R.string.issued_receipt_totals_discount, money(totals.discountMinor)))
            if (totals.taxMinor > 0) add(getString(R.string.issued_receipt_totals_tax, money(totals.taxMinor)))
            add(getString(R.string.issued_receipt_totals_total, money(totals.totalMinor)))
        }.joinToString("\n")
    }

    private fun sync(edit: EditText, value: String) {
        if (edit.text?.toString() != value) edit.setText(value)
    }

    private fun renderLogoControls(b: FragmentIssuedReceiptFormBinding, state: IssuedReceiptFormState) {
        val hasLogo = state.pendingLogoUri != null || (state.storedLogoFileName != null && !state.logoRemoved)
        b.logoRemoveButton.visibility = if (hasLogo) View.VISIBLE else View.GONE
        // En modification, l'émetteur (logo compris) reste celui du reçu : le profil mémorisé n'est pas touché.
        b.logoChooseButton.isEnabled = !state.editing
        b.logoRemoveButton.isEnabled = !state.editing
        b.logoChooseButton.setText(if (hasLogo) R.string.issued_receipt_logo_change else R.string.issued_receipt_logo_choose)
    }

    private fun renderSignatureControls(b: FragmentIssuedReceiptFormBinding, state: IssuedReceiptFormState) {
        // En modification, la signature ne peut être qu'activée/désactivée : le profil mémorisé n'est pas touché.
        b.signatureDrawButton.isEnabled = !state.editing
        b.signatureImportButton.isEnabled = !state.editing
        b.signatureRemoveButton.visibility = if (state.hasSignature && !state.editing) View.VISIBLE else View.GONE
        b.signatureSwitchRow.visibility = if (state.hasSignature) View.VISIBLE else View.GONE
        if (b.signatureSwitch.isChecked != state.signReceipt) b.signatureSwitch.isChecked = state.signReceipt
    }

    private fun renderErrors(b: FragmentIssuedReceiptFormBinding, state: IssuedReceiptFormState) {
        val e = state.errors
        fun err(vararg codes: IssuedReceiptError): String? =
            codes.firstOrNull { it in e }?.let { getString(it.messageRes()) }

        b.businessNameField.postcardInputLayout.error = err(IssuedReceiptError.BUSINESS_NAME_BLANK)
        b.addressField.postcardInputLayout.error = err(IssuedReceiptError.BUSINESS_ADDRESS_BLANK)
        b.phoneField.postcardInputLayout.error = err(IssuedReceiptError.BUSINESS_PHONE_INVALID)
        b.emailField.postcardInputLayout.error = err(IssuedReceiptError.BUSINESS_EMAIL_INVALID)
        b.customerNameField.postcardInputLayout.error = err(IssuedReceiptError.CUSTOMER_NAME_BLANK)
        b.customerPhoneField.postcardInputLayout.error = err(IssuedReceiptError.CUSTOMER_PHONE_INVALID)
        b.descriptionField.postcardInputLayout.error = err(IssuedReceiptError.DESCRIPTION_BLANK)
        b.amountField.postcardInputLayout.error = err(IssuedReceiptError.AMOUNT_NOT_POSITIVE, IssuedReceiptError.AMOUNT_TOO_LARGE)
        b.numberField.postcardInputLayout.error = when {
            state.numberTaken -> getString(R.string.issued_receipt_error_number_taken)
            else -> err(IssuedReceiptError.NUMBER_INVALID)
        }

        // Erreurs sans champ dédié (texte trop long, devise...) : regroupées sous le formulaire.
        val detailed = state.input.detailed
        val general = e.filter {
            it == IssuedReceiptError.TEXT_TOO_LONG || it == IssuedReceiptError.CURRENCY_INVALID || it == IssuedReceiptError.LINES_EMPTY ||
                (detailed && (it == IssuedReceiptError.AMOUNT_NOT_POSITIVE || it == IssuedReceiptError.AMOUNT_TOO_LARGE))
        }
        b.errorBanner.text = general.joinToString("\n") { getString(it.messageRes()) }
        b.errorBanner.visibility = if (general.isEmpty()) View.GONE else View.VISIBLE
    }

    // --- Aperçu ------------------------------------------------------------------------------

    /** Reconstruit l'aperçu à chaque modification (légèrement temporisé : pas de recalcul à chaque lettre). */
    @OptIn(FlowPreview::class)
    private suspend fun observePreview() {
        viewModel.state
            .map { PreviewKey(it) }
            .distinctUntilChanged()
            .debounce(PREVIEW_DEBOUNCE_MS)
            .collect { key -> renderPreview(key.state) }
    }

    /** Clé de l'aperçu : seuls les champs qui changent le document comptent (pas `isSaving`, les erreurs…). */
    private data class PreviewKey(val state: IssuedReceiptFormState) {
        private val fields = listOf(
            state.input, state.suggestedNumber, state.pendingLogoUri, state.storedLogoFileName, state.logoRemoved,
            state.pendingSignature, state.storedSignatureFileName, state.signatureRemoved, state.signReceipt
        )
        override fun equals(other: Any?) = other is PreviewKey && other.fields == fields
        override fun hashCode() = fields.hashCode()
    }

    private suspend fun renderPreview(state: IssuedReceiptFormState) {
        val b = binding ?: return
        updatePreviewLogo(b, state)
        updatePreviewSignature(b, state)
        val input = state.input
        val receipt = IssuedReceiptDraftFactory.toReceipt(
            input.copy(numberInput = input.numberInput.ifBlank { state.suggestedNumber })
        )
        val logo = previewLogo
        // Comme dans le PDF : la signature n'apparaît que si l'interrupteur « Signer ce reçu » est actif.
        val signature = previewSignature?.takeIf { state.signsReceipt }
        val content = ReceiptPdfContentBuilder.build(
            receipt,
            language(),
            logo?.let { PdfLogo(it.width, it.height) },
            signature?.let { PdfLogo(it.width, it.height) }
        )
        b.receiptPreview.show(content, logo, signature)
    }

    /** Charge (ou retire) le logo de l'aperçu seulement quand la source change. */
    private suspend fun updatePreviewLogo(b: FragmentIssuedReceiptFormBinding, state: IssuedReceiptFormState) {
        val repoFile = state.storedLogoFileName?.takeIf { !state.logoRemoved }
        val key = state.pendingLogoUri ?: repoFile
        if (key == previewLogoKey) return
        previewLogoKey = key
        previewLogo = when {
            state.pendingLogoUri != null -> PreviewLogoLoader.load(requireContext(), PreviewLogoSource.Picked(state.pendingLogoUri))
            repoFile != null -> viewModel.logoFile(repoFile)?.let { PreviewLogoLoader.load(requireContext(), PreviewLogoSource.Stored(it)) }
            else -> null
        }
        if (previewLogo != null) b.logoImage.setImageBitmap(previewLogo) else b.logoImage.setImageResource(R.drawable.ic_storefront_24)
    }

    /** Charge (ou retire) la signature seulement quand sa source change ; met aussi à jour la miniature du formulaire. */
    private suspend fun updatePreviewSignature(b: FragmentIssuedReceiptFormBinding, state: IssuedReceiptFormState) {
        val storedName = state.storedSignatureFileName?.takeIf { !state.signatureRemoved }
        val key: Any? = state.pendingSignature ?: storedName
        if (key == previewSignatureKey) return
        previewSignatureKey = key
        previewSignature = withContext(Dispatchers.IO) {
            when {
                state.pendingSignature != null -> state.pendingSignature.pngBytes.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                storedName != null -> viewModel.logoFile(storedName)?.let { BitmapFactory.decodeFile(it.path) }
                else -> null
            }
        }
        if (previewSignature != null) b.signatureImage.setImageBitmap(previewSignature) else b.signatureImage.setImageResource(R.drawable.ic_signature_24)
    }

    // --- Événements --------------------------------------------------------------------------

    private fun handleEvent(event: IssuedReceiptFormEvent) {
        val b = binding ?: return
        when (event) {
            is IssuedReceiptFormEvent.Message -> Snackbar.make(b.root, event.messageRes, Snackbar.LENGTH_LONG).show()
            is IssuedReceiptFormEvent.PdfFailed -> Snackbar.make(b.root, R.string.issued_receipt_pdf_failed, Snackbar.LENGTH_LONG).show()
            is IssuedReceiptFormEvent.Saved ->
                if (event.pdfUri != null) {
                    showPdfReadyDialog(event.number, event.pdfFileName.orEmpty(), event.pdfUri)
                } else {
                    Snackbar.make(b.root, getString(R.string.issued_receipt_saved, event.number), Snackbar.LENGTH_LONG).show()
                }
        }
    }

    private fun showPdfReadyDialog(number: String, fileName: String, uri: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.issued_receipt_pdf_ready_title, number))
            .setMessage(getString(R.string.issued_receipt_pdf_ready_message, fileName))
            .setPositiveButton(R.string.issued_receipt_action_open) { _, _ -> openPdf(uri) }
            .setNeutralButton(R.string.issued_receipt_action_share) { _, _ -> sharePdf(uri, fileName) }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun openPdf(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(uri), "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivityOrWarn(intent)
    }

    private fun sharePdf(uri: String, fileName: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
            .putExtra(Intent.EXTRA_SUBJECT, fileName)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivityOrWarn(Intent.createChooser(send, getString(R.string.issued_receipt_action_share)))
    }

    private fun startActivityOrWarn(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            binding?.root?.let { Snackbar.make(it, R.string.issued_receipt_no_pdf_app, Snackbar.LENGTH_LONG).show() }
        }
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 120L
    }
}
