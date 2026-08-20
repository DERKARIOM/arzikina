package com.arzikina.ne.presentation.utilities.receipts

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.DialogEditReceiptAmountBinding
import com.arzikina.ne.databinding.DialogRenameReceiptBinding
import com.arzikina.ne.databinding.FragmentReceiptDetailBinding
import com.arzikina.ne.domain.model.Receipt
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.presentation.components.NavAnimations
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.DatePeriods
import com.arzikina.ne.util.FileSizeFormatter
import com.arzikina.ne.util.Money
import com.arzikina.ne.util.TriggerTimeFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Écran "Détail du reçu" (voir [ReceiptDetailViewModel]) — cahier des charges "Gestion des reçus",
 * section 6. Même convention que [com.arzikina.ne.presentation.utilities.loans.LoanDetailFragment]
 * pour la garde anti-double-navigation ([hasNavigatedAwayOnError]) et la confirmation de suppression
 * ([ConfirmDialogs]).
 */
@AndroidEntryPoint
class ReceiptDetailFragment : Fragment(R.layout.fragment_receipt_detail) {

    private val viewModel: ReceiptDetailViewModel by viewModels()
    private var binding: FragmentReceiptDetailBinding? = null

    private var latestReceipt: Receipt? = null

    /** Voir la doc de classe : le reçu affiché a pu être supprimé depuis un autre écran pendant que
     * celui-ci restait ouvert — ce garde-fou coûte peu et évite un `findNavController().navigateUp()`
     * répété si [AppResult.Error] est émis plusieurs fois. */
    private var hasNavigatedAwayOnError = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentReceiptDetailBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        viewBinding.openButton.setOnClickListener { viewModel.openWithAnotherApp() }
        viewBinding.shareButton.setOnClickListener { viewModel.shareReceipt() }
        viewBinding.previewContainer.setOnClickListener { openPdfViewer() }
        viewBinding.confirmSuggestedAmountButton.setOnClickListener { viewModel.confirmSuggestedAmount() }
        viewBinding.dismissSuggestedAmountButton.setOnClickListener { viewModel.dismissSuggestedAmount() }
        viewBinding.editSuggestedAmountButton.setOnClickListener { showEditAmountDialog() }
        viewBinding.detectAmountButton.setOnClickListener { viewModel.detectAmount() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state -> render(state) } }
                launch { viewModel.previewBitmap.collect { bitmap -> renderPreview(bitmap) } }
                launch { viewModel.suggestedAmountMinor.collect { amount -> renderSuggestedAmount(amount) } }
                launch { viewModel.detectAmountButtonState.collect { state -> renderDetectAmountButton(state) } }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpToolbar(binding: FragmentReceiptDetailBinding) {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_rename_receipt -> {
                    showRenameDialog()
                    true
                }
                R.id.action_delete_item -> {
                    confirmDelete()
                    true
                }
                else -> false
            }
        }
    }

    private fun render(state: AppResult<Receipt>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) {
            if (state is AppResult.Error && !hasNavigatedAwayOnError) {
                hasNavigatedAwayOnError = true
                findNavController().navigateUp()
            }
            return
        }
        val receipt = state.data
        latestReceipt = receipt

        binding.toolbar.title = receipt.fileName

        val receivedDate = DatePeriods.toLocalDate(receipt.receivedAt)
        val receivedTime = DatePeriods.toLocalTime(receipt.receivedAt)
        binding.receivedValue.text = getString(
            R.string.receipt_meta_line_format,
            receivedDate.format(dateFormatter),
            TriggerTimeFormatter.format(requireContext(), receivedTime.hour, receivedTime.minute)
        )

        binding.sizeValue.text = FileSizeFormatter.format(receipt.fileSize)
        binding.sourceValue.text = receipt.sourceName ?: getString(R.string.receipt_source_unknown)

        val amountMinor = receipt.amountMinor
        if (amountMinor != null) {
            binding.amountRow.visibility = View.VISIBLE
            binding.amountValue.text = Money.formatAmount(amountMinor)
        } else {
            binding.amountRow.visibility = View.GONE
        }
    }

    private fun renderPreview(bitmap: Bitmap?) {
        val binding = binding ?: return
        if (bitmap != null) {
            binding.previewImage.setImageBitmap(bitmap)
            binding.previewImage.visibility = View.VISIBLE
            binding.previewUnavailableText.visibility = View.GONE
        } else {
            binding.previewImage.visibility = View.GONE
            binding.previewUnavailableText.visibility = View.VISIBLE
        }
    }

    /** Voir `ReceiptDetailViewModel.suggestedAmountMinor` : `null` masque le bandeau (aucune
     * suggestion en attente — cas normal la plupart du temps, voir Étape 5, "déclenchement à la
     * demande"), une valeur non nulle l'affiche avec le montant formaté (voir [Money.formatAmount]). */
    private fun renderSuggestedAmount(amountMinor: Long?) {
        val binding = binding ?: return
        if (amountMinor != null) {
            binding.suggestedAmountValue.text = Money.formatAmount(amountMinor)
            binding.suggestedAmountCard.visibility = View.VISIBLE
        } else {
            binding.suggestedAmountCard.visibility = View.GONE
        }
    }

    /** Voir la doc de [DetectAmountButtonState] — même fichier que [ReceiptDetailViewModel], aucun
     * import supplémentaire nécessaire. */
    private fun renderDetectAmountButton(state: DetectAmountButtonState) {
        val binding = binding ?: return
        when (state) {
            DetectAmountButtonState.Hidden -> binding.detectAmountButton.visibility = View.GONE
            DetectAmountButtonState.Idle -> {
                binding.detectAmountButton.visibility = View.VISIBLE
                binding.detectAmountButton.isEnabled = true
                binding.detectAmountButton.text = getString(R.string.receipt_detail_detect_amount_action)
            }
            DetectAmountButtonState.Loading -> {
                binding.detectAmountButton.visibility = View.VISIBLE
                binding.detectAmountButton.isEnabled = false
                binding.detectAmountButton.text = getString(R.string.receipt_detail_detect_amount_loading)
            }
        }
    }

    private fun handleEvent(event: ReceiptDetailEvent) {
        val binding = binding ?: return
        val message = when (event) {
            ReceiptDetailEvent.ShareFailed -> getString(R.string.receipt_detail_share_failed_message)
            ReceiptDetailEvent.OpenWithFailed -> getString(R.string.receipt_detail_open_with_failed_message)
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    /**
     * Positive button gérée manuellement (voir `PersonPickerDialog.showAddPersonDialog`, même
     * principe) : un nom vide affiche une erreur SUR LE CHAMP sans fermer le dialogue, plutôt que de
     * fermer puis rouvrir (ou pire, enregistrer silencieusement le nom précédent).
     */
    private fun showRenameDialog() {
        val receipt = latestReceipt ?: return
        val dialogBinding = DialogRenameReceiptBinding.inflate(layoutInflater)
        dialogBinding.renameInput.setText(receipt.fileName)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.receipt_detail_rename_action)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.receipt_detail_rename_action, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = dialogBinding.renameInput.text?.toString()?.trim().orEmpty()
                if (newName.isEmpty()) {
                    dialogBinding.renameLayout.error = getString(R.string.receipt_detail_rename_empty_error)
                } else {
                    dialogBinding.renameLayout.error = null
                    viewModel.renameReceipt(newName)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    /**
     * Voir la doc de [showRenameDialog] : même principe de validation manuelle du bouton positif
     * (erreur affichée SUR LE CHAMP, dialogue jamais fermé puis rouvert plutôt qu'une fermeture
     * immédiate sur une saisie invalide). Pré-rempli avec la valeur actuellement suggérée (voir
     * `ReceiptDetailViewModel.suggestedAmountMinor`) — permet de la CORRIGER plutôt que de la
     * ressaisir entièrement depuis zéro. `return` silencieux si aucune suggestion n'est affichée :
     * ce bouton n'est de toute façon visible que dans ce cas (voir `suggestedAmountCard`).
     */
    private fun showEditAmountDialog() {
        val suggested = viewModel.suggestedAmountMinor.value ?: return
        val dialogBinding = DialogEditReceiptAmountBinding.inflate(layoutInflater)
        dialogBinding.editAmountInput.setText(Money.formatMajorUnits(suggested))

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.receipt_detail_edit_amount_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.receipt_detail_edit_amount_title, null)
            .setNegativeButton(R.string.action_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val amountMinor = Money.parseToMinorUnits(dialogBinding.editAmountInput.text?.toString().orEmpty())
                if (amountMinor == null) {
                    dialogBinding.editAmountLayout.error = getString(R.string.receipt_detail_edit_amount_empty_error)
                } else {
                    dialogBinding.editAmountLayout.error = null
                    viewModel.saveAmount(amountMinor)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    /**
     * Voir `fragment_receipt_detail.xml` (commentaire sur `previewContainer`) : sans effet tant
     * qu'aucune page n'est actuellement affichée ([binding.previewImage] `GONE`, voir
     * [renderPreview]) — inutile d'ouvrir un visualiseur plein écran pour re-tenter le même rendu qui
     * vient d'échouer ici, sur le même fichier.
     */
    private fun openPdfViewer() {
        val binding = binding ?: return
        val receipt = latestReceipt ?: return
        if (binding.previewImage.visibility != View.VISIBLE) return
        findNavController().navigate(
            R.id.receiptPdfViewerFragment,
            bundleOf("receiptId" to receipt.id),
            NavAnimations.push
        )
    }

    private fun confirmDelete() {
        // Garde-fou : pas de dialogue tant qu'aucun reçu n'est encore chargé (voir latestReceipt).
        latestReceipt ?: return
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.receipt_detail_delete_title),
            message = getString(R.string.receipt_detail_delete_message),
            onConfirm = {
                viewModel.deleteReceipt()
                hasNavigatedAwayOnError = true
                findNavController().navigateUp()
            }
        )
    }

    private companion object {
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)
    }
}
