package com.naniger.arzikina.presentation.utilities.receipts

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.DialogEditReceiptAmountBinding
import com.naniger.arzikina.databinding.DialogRenameReceiptBinding
import com.naniger.arzikina.databinding.FragmentReceiptDetailBinding
import com.naniger.arzikina.domain.model.Receipt
import com.naniger.arzikina.domain.model.Transaction
import com.naniger.arzikina.presentation.components.ConfirmDialogs
import com.naniger.arzikina.presentation.components.NavAnimations
import com.naniger.arzikina.presentation.transactions.TransactionFormFragmentArgs
import com.naniger.arzikina.util.AppDateFormats
import com.naniger.arzikina.util.AppResult
import com.naniger.arzikina.util.DatePeriods
import com.naniger.arzikina.util.FileSizeFormatter
import com.naniger.arzikina.util.Money
import com.naniger.arzikina.util.MoneyInputFormatter
import com.naniger.arzikina.util.TriggerTimeFormatter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Écran "Détail du reçu" (voir [ReceiptDetailViewModel]) — cahier des charges "Gestion des reçus",
 * section 6. Même convention que [com.naniger.arzikina.presentation.utilities.loans.LoanDetailFragment]
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
        viewBinding.addTransactionButton.setOnClickListener { viewModel.onAddTransactionClicked() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state -> render(state) } }
                launch { viewModel.previewBitmap.collect { bitmap -> renderPreview(bitmap) } }
                launch { viewModel.suggestedAmountMinor.collect { amount -> renderSuggestedAmount(amount) } }
                launch { viewModel.detectAmountButtonState.collect { state -> renderDetectAmountButton(state) } }
                launch {
                    combine(viewModel.linkedTransaction, viewModel.isPreparingTransaction) { linked, isPreparing ->
                        linked to isPreparing
                    }.collect { (linked, isPreparing) -> renderAddTransactionButton(linked, isPreparing) }
                }
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
            receivedDate.format(AppDateFormats.longDate(requireContext())),
            TriggerTimeFormatter.format(requireContext(), receivedTime.hour, receivedTime.minute)
        )

        binding.sizeValue.text = FileSizeFormatter.format(requireContext(), receipt.fileSize)
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

    /**
     * Voir [ReceiptDetailViewModel.linkedTransaction]/[ReceiptDetailViewModel.isPreparingTransaction] :
     * libellé du bouton piloté par la présence d'une transaction déjà liée (anti-doublon),
     * désactivé pendant l'extraction/correspondance déclenchée par un clic (voir
     * [ReceiptDetailViewModel.onAddTransactionClicked]) — jamais masqué (voir
     * `fragment_receipt_detail.xml`, commentaire sur `addTransactionButton`). Pilote au passage
     * `transactionLinkedBadge` (cahier des charges "statut visuel du reçu", même donnée) —
     * indépendant de [isPreparing] : le badge ne doit pas clignoter pendant une analyse en cours
     * pour un AUTRE clic (cas rare mais possible si l'utilisateur revient sur cet écran).
     */
    private fun renderAddTransactionButton(linkedTransaction: Transaction?, isPreparing: Boolean) {
        val binding = binding ?: return
        binding.transactionLinkedBadge.visibility = if (linkedTransaction != null) View.VISIBLE else View.GONE
        binding.addTransactionButton.isEnabled = !isPreparing
        binding.addTransactionButton.text = when {
            isPreparing -> getString(R.string.receipt_detail_add_transaction_loading)
            linkedTransaction != null -> getString(R.string.receipt_detail_view_transaction_action)
            else -> getString(R.string.receipt_detail_add_transaction_action)
        }
    }

    private fun handleEvent(event: ReceiptDetailEvent) {
        val binding = binding ?: return
        when (event) {
            ReceiptDetailEvent.ShareFailed ->
                Snackbar.make(binding.root, getString(R.string.receipt_detail_share_failed_message), Snackbar.LENGTH_LONG).show()
            ReceiptDetailEvent.OpenWithFailed ->
                Snackbar.make(binding.root, getString(R.string.receipt_detail_open_with_failed_message), Snackbar.LENGTH_LONG).show()
            is ReceiptDetailEvent.OpenLinkedTransaction ->
                findNavController().navigate(
                    R.id.transactionFormFragment,
                    TransactionFormFragmentArgs(transactionId = event.transactionId).toBundle(),
                    NavAnimations.push
                )
            is ReceiptDetailEvent.PrefillNewTransaction -> navigateToPrefilledTransactionForm(event.prefill)
        }
    }

    /**
     * Voir `nav_graph.xml` (`transactionFormFragment`, arguments `presetXxx`) : chaque champ `null`
     * de [prefill] est simplement OMIS du bundle plutôt que transmis explicitement — les valeurs par
     * défaut Navigation (0L/`null`) prennent alors le relais, exactement comme pour une ouverture
     * normale du formulaire (voir [TransactionPrefill], "non détecté" jamais une valeur inventée).
     */
    private fun navigateToPrefilledTransactionForm(prefill: TransactionPrefill) {
        findNavController().navigate(
            R.id.transactionFormFragment,
            TransactionFormFragmentArgs(
                presetAmountMinor = prefill.amountMinor ?: 0L,
                presetFeeAmountMinor = prefill.feeAmountMinor ?: 0L,
                presetDateTimeMillis = prefill.dateTimeMillis ?: 0L,
                presetDescription = prefill.counterparty?.let { requireContext().receiptDescription(it) },
                presetCategoryId = prefill.categoryId ?: 0L,
                presetAccountId = prefill.accountId ?: 0L,
                presetReceiptId = prefill.receiptId,
                presetType = prefill.type?.name
            ).toBundle(),
            NavAnimations.push
        )
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
        // Pas de ViewModel pour ce champ (voir la doc de tête) : MoneyInputFormatter.attach exige
        // tout de même un callback (lambda vide ici, sans effet) — la valeur saisie est relue
        // directement sur le champ au clic du bouton positif ci-dessous, comme avant.
        MoneyInputFormatter.attach(dialogBinding.editAmountInput) {}
        dialogBinding.editAmountInput.setText(Money.formatForInput(suggested))

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
            ReceiptPdfViewerFragmentArgs(receiptId = receipt.id).toBundle(),
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
}
