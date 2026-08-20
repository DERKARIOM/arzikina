package com.arzikina.ne.presentation.utilities.receipts

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentReceiptsBinding
import com.arzikina.ne.domain.model.Receipt
import com.arzikina.ne.presentation.components.NavAnimations
import com.arzikina.ne.util.AppResult
import com.arzikina.ne.util.Constants
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Écran "Gestion des reçus" : liste groupée par jour, recherche, filtre par période, import manuel
 * (voir [ReceiptsViewModel]). Même structure que [com.arzikina.ne.presentation.utilities.LoansFragment]/
 * [com.arzikina.ne.presentation.transactions.TransactionsFragment].
 *
 * Atteint depuis le bloc Utilitaires (voir `UtilityCatalog`) — point d'entrée ajouté à l'Étape 8.
 */
@AndroidEntryPoint
class ReceiptsFragment : Fragment(R.layout.fragment_receipts) {

    private val viewModel: ReceiptsViewModel by viewModels()
    private var binding: FragmentReceiptsBinding? = null

    private val adapter = ReceiptsAdapter(onClick = { receipt -> onReceiptClicked(receipt) })

    /** Voir cahier des charges section 12 : import manuel via le sélecteur de fichiers Android,
     * filtré sur [Constants.RECEIPT_MIME_TYPE] uniquement (même MIME que le partage entrant, voir
     * [com.arzikina.ne.MainActivity]). */
    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onDocumentPicked(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentReceiptsBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        setUpList(viewBinding)
        setUpSearch(viewBinding)
        setUpChips(viewBinding)

        viewBinding.resetFiltersButton.setOnClickListener { viewModel.resetFilters() }
        viewBinding.addReceiptButton.setOnClickListener { launchImportPicker() }
        viewBinding.emptyImportButton.setOnClickListener { launchImportPicker() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state -> render(state) } }
                launch { viewModel.filters.collect { filters -> renderFilters(filters) } }
                launch { viewModel.events.collect { event -> handleEvent(event) } }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpToolbar(binding: FragmentReceiptsBinding) {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_toggle_filters) {
                toggleFiltersPanel(binding)
                true
            } else {
                false
            }
        }
    }

    private fun toggleFiltersPanel(binding: FragmentReceiptsBinding) {
        binding.filtersPanel.visibility = if (binding.filtersPanel.visibility == View.VISIBLE) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun setUpList(binding: FragmentReceiptsBinding) {
        binding.receiptsList.layoutManager = LinearLayoutManager(requireContext())
        binding.receiptsList.adapter = adapter
    }

    private fun setUpSearch(binding: FragmentReceiptsBinding) {
        binding.searchInput.doAfterTextChanged { text ->
            viewModel.onQueryChange(text?.toString().orEmpty())
        }
    }

    private fun setUpChips(binding: FragmentReceiptsBinding) {
        binding.periodChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val period = when (checkedIds.firstOrNull()) {
                R.id.periodChipToday -> ReceiptPeriodFilter.TODAY
                R.id.periodChipWeek -> ReceiptPeriodFilter.THIS_WEEK
                R.id.periodChipMonth -> ReceiptPeriodFilter.THIS_MONTH
                else -> ReceiptPeriodFilter.ALL
            }
            if (period != viewModel.filters.value.period) {
                viewModel.onPeriodFilterChange(period)
            }
        }
    }

    private fun launchImportPicker() {
        importLauncher.launch(arrayOf(Constants.RECEIPT_MIME_TYPE))
    }

    private fun onDocumentPicked(uri: Uri) {
        viewModel.importReceipt(
            sourceUri = uri.toString(),
            mimeType = Constants.RECEIPT_MIME_TYPE,
            fallbackDisplayName = getString(R.string.receipt_share_default_file_name)
        )
    }

    private fun render(state: AppResult<List<ReceiptDaySection>>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return

        val hasReceipts = state.data.isNotEmpty()
        binding.receiptsList.visibility = if (hasReceipts) View.VISIBLE else View.GONE
        binding.addReceiptButton.visibility = if (hasReceipts) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasReceipts) View.GONE else View.VISIBLE
        adapter.submitList(state.data.toListRows())
    }

    private fun renderFilters(filters: ReceiptFilters) {
        val binding = binding ?: return
        if (binding.searchInput.text?.toString() != filters.query) {
            binding.searchInput.setText(filters.query)
        }

        val expectedPeriodChip = when (filters.period) {
            ReceiptPeriodFilter.ALL -> R.id.periodChipAll
            ReceiptPeriodFilter.TODAY -> R.id.periodChipToday
            ReceiptPeriodFilter.THIS_WEEK -> R.id.periodChipWeek
            ReceiptPeriodFilter.THIS_MONTH -> R.id.periodChipMonth
        }
        if (binding.periodChipGroup.checkedChipId != expectedPeriodChip) {
            binding.periodChipGroup.check(expectedPeriodChip)
        }

        binding.resetFiltersButton.visibility = if (filters.hasActiveFilters) View.VISIBLE else View.GONE
    }

    private fun handleEvent(event: ReceiptImportEvent) {
        val binding = binding ?: return
        val message = when (event) {
            ReceiptImportEvent.Success -> getString(R.string.receipt_share_imported_message)
            is ReceiptImportEvent.Failure -> getString(R.string.receipt_share_import_failed_message)
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun onReceiptClicked(receipt: Receipt) {
        findNavController().navigate(
            R.id.receiptDetailFragment,
            bundleOf("receiptId" to receipt.id),
            NavAnimations.push
        )
    }
}
