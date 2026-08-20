package com.arzikina.ne.presentation.utilities.receipts

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentReceiptPdfViewerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Visualiseur PDF plein écran (voir [ReceiptPdfViewerViewModel]) — cahier des charges "Gestion des
 * reçus", section 7 : toutes les pages, navigation Précédent/Suivant, aucune bibliothèque tierce
 * (voir `ReceiptPdfRenderer`, natif).
 */
@AndroidEntryPoint
class ReceiptPdfViewerFragment : Fragment(R.layout.fragment_receipt_pdf_viewer) {

    private val viewModel: ReceiptPdfViewerViewModel by viewModels()
    private var binding: FragmentReceiptPdfViewerBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentReceiptPdfViewerBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        viewBinding.previousPageButton.setOnClickListener { viewModel.goToPreviousPage() }
        viewBinding.nextPageButton.setOnClickListener { viewModel.goToNextPage() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun render(state: ReceiptPdfViewerUiState) {
        val binding = binding ?: return

        binding.toolbar.title = state.receiptName

        val hasPage = state.pageBitmap != null
        binding.pageImage.visibility = if (hasPage) View.VISIBLE else View.GONE
        if (hasPage) {
            binding.pageImage.setImageBitmap(state.pageBitmap)
        }
        binding.loadingIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        binding.unavailableText.visibility = if (!state.isLoading && !hasPage) View.VISIBLE else View.GONE

        // Navigation masquée pour un document d'une seule page (voir fragment_receipt_pdf_viewer.xml) :
        // Précédent/Suivant n'a alors aucun sens.
        binding.navigationBar.visibility = if (state.pageCount > 1) View.VISIBLE else View.GONE
        binding.previousPageButton.isEnabled = state.pageIndex > 0
        binding.nextPageButton.isEnabled = state.pageIndex < state.pageCount - 1
        binding.pageIndicatorText.text = getString(
            R.string.receipt_viewer_page_indicator_format,
            state.pageIndex + 1,
            state.pageCount
        )
    }
}
