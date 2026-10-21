package com.naniger.arzikina.presentation.utilities.issuedreceipts.history

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentIssuedReceiptHistoryBinding
import com.naniger.arzikina.domain.model.IssuedReceiptSummary
import com.naniger.arzikina.presentation.components.ConfirmDialogs
import com.naniger.arzikina.util.AppDateFormats
import com.naniger.arzikina.util.appLocale
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Historique des reçus émis (point d'entrée de l'utilitaire « Gestion de reçus »). Recherche
 * instantanée + panneau de période (basculé par l'action « Filtres », comme Prêts/Transactions).
 * Le tri est fait par la base (le plus récent d'abord) ; la liste n'est jamais filtrée en mémoire.
 */
@AndroidEntryPoint
class IssuedReceiptHistoryFragment : Fragment(R.layout.fragment_issued_receipt_history) {

    private val viewModel: IssuedReceiptHistoryViewModel by viewModels()
    private var binding: FragmentIssuedReceiptHistoryBinding? = null
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val adapter = IssuedReceiptHistoryAdapter(
        IssuedReceiptHistoryActions(
            onView = { findNavController().navigate(R.id.issuedReceiptDetailFragment, bundleOf("receiptId" to it.id)) },
            onOpen = { viewModel.onOpenPdf(it.id, language()) },
            onShare = { viewModel.onSharePdf(it.id, language()) },
            onDelete = ::confirmDelete
        )
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentIssuedReceiptHistoryBinding.bind(view)
        binding = b

        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_toggle_filters) {
                b.filtersPanel.visibility = if (b.filtersPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                true
            } else false
        }
        b.receiptsList.layoutManager = LinearLayoutManager(requireContext())
        b.receiptsList.adapter = adapter
        b.receiptsList.itemAnimator = null
        b.addButton.setOnClickListener { openForm() }
        b.emptyAddButton.setOnClickListener { openForm() }
        b.searchInput.doAfterTextChanged { viewModel.onQueryChange(it?.toString().orEmpty()) }
        b.fromButton.setOnClickListener { pickDate(viewModel.filters.value.fromMillis) { viewModel.onFromChange(it) } }
        b.toButton.setOnClickListener { pickDate(viewModel.filters.value.toMillis) { viewModel.onToChange(it) } }
        b.clearDatesButton.setOnClickListener { viewModel.onClearDates() }
        b.resetFiltersButton.setOnClickListener {
            b.searchInput.setText("")
            viewModel.onResetFilters()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.filters.collect(::renderFilters) }
                launch { viewModel.working.collect { binding?.progress?.visibility = if (it) View.VISIBLE else View.INVISIBLE } }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun language(): String = requireContext().appLocale().language

    private fun openForm() = findNavController().navigate(R.id.issuedReceiptFormFragment)

    private fun render(state: IssuedReceiptHistoryUiState) {
        val b = binding ?: return
        val loaded = state as? IssuedReceiptHistoryUiState.Loaded
        val items = loaded?.items.orEmpty()
        val noReceiptsAtAll = loaded != null && items.isEmpty() && !loaded.filtersActive
        val noResults = loaded != null && items.isEmpty() && loaded.filtersActive

        adapter.submitList(items)
        b.loading.visibility = if (state is IssuedReceiptHistoryUiState.Loading) View.VISIBLE else View.GONE
        b.errorText.visibility = if (state is IssuedReceiptHistoryUiState.Error) View.VISIBLE else View.GONE
        b.emptyState.visibility = if (noReceiptsAtAll) View.VISIBLE else View.GONE
        b.noResults.visibility = if (noResults) View.VISIBLE else View.GONE
        b.receiptsList.visibility = if (items.isNotEmpty()) View.VISIBLE else View.GONE
        b.addButton.visibility = if (noReceiptsAtAll) View.GONE else View.VISIBLE
        // Rien à chercher tant qu'aucun reçu n'existe.
        b.searchCard.visibility = if (noReceiptsAtAll) View.GONE else View.VISIBLE
        b.toolbar.menu.findItem(R.id.action_toggle_filters)?.isVisible = !noReceiptsAtAll
        if (noReceiptsAtAll) b.filtersPanel.visibility = View.GONE
    }

    private fun renderFilters(filters: IssuedReceiptHistoryFilters) {
        val b = binding ?: return
        val format = AppDateFormats.mediumDate(requireContext())
        fun label(prefixRes: Int, millis: Long?, emptyRes: Int): String =
            if (millis == null) getString(emptyRes)
            else getString(prefixRes, Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().format(format))
        b.fromButton.text = label(R.string.issued_receipt_history_from, filters.fromMillis, R.string.issued_receipt_history_from_empty)
        b.toButton.text = label(R.string.issued_receipt_history_to, filters.toMillis, R.string.issued_receipt_history_to_empty)
        b.clearDatesButton.visibility = if (filters.fromMillis != null || filters.toMillis != null) View.VISIBLE else View.GONE
        b.resetFiltersButton.visibility = if (filters.isActive) View.VISIBLE else View.GONE
    }

    /** `MaterialDatePicker` raisonne en UTC : conversion jour UTC ⇄ jour local (voir [HistoryDateBounds]). */
    private fun pickDate(currentLocalMillis: Long?, onPicked: (Long) -> Unit) {
        val selection = HistoryDateBounds.toPickerSelection(currentLocalMillis ?: System.currentTimeMillis(), zone)
        val picker = MaterialDatePicker.Builder.datePicker().setSelection(selection).build()
        picker.addOnPositiveButtonClickListener { onPicked(HistoryDateBounds.startOfDayFromPicker(it, zone)) }
        picker.show(childFragmentManager, "history_date")
    }

    private fun confirmDelete(item: IssuedReceiptSummary) {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.issued_receipt_history_delete_title, item.number),
            message = getString(R.string.issued_receipt_history_delete_message)
        ) { viewModel.onDeleteConfirmed(item.id) }
    }

    private fun handleEvent(event: IssuedReceiptHistoryEvent) {
        val root = binding?.root ?: return
        when (event) {
            is IssuedReceiptHistoryEvent.Message -> Snackbar.make(root, event.messageRes, Snackbar.LENGTH_LONG).show()
            is IssuedReceiptHistoryEvent.PdfReady -> if (event.share) sharePdf(event) else openPdf(event)
        }
    }

    private fun openPdf(event: IssuedReceiptHistoryEvent.PdfReady) {
        startActivityOrWarn(
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(event.file.uri), "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun sharePdf(event: IssuedReceiptHistoryEvent.PdfReady) {
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse(event.file.uri))
            .putExtra(Intent.EXTRA_SUBJECT, event.file.fileName)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivityOrWarn(Intent.createChooser(send, getString(R.string.issued_receipt_action_share)))
    }

    private fun startActivityOrWarn(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            binding?.root?.let { Snackbar.make(it, R.string.issued_receipt_no_pdf_app, Snackbar.LENGTH_LONG).show() }
        }
    }
}
