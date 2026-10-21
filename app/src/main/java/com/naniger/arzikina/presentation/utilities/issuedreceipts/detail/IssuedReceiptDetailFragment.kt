package com.naniger.arzikina.presentation.utilities.issuedreceipts.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentIssuedReceiptDetailBinding
import com.naniger.arzikina.domain.issuedreceipt.pdf.PdfLogo
import com.naniger.arzikina.domain.issuedreceipt.pdf.ReceiptPdfContentBuilder
import com.naniger.arzikina.domain.model.IssuedReceipt
import com.naniger.arzikina.presentation.components.ConfirmDialogs
import com.naniger.arzikina.presentation.utilities.issuedreceipts.preview.PreviewLogoLoader
import com.naniger.arzikina.presentation.utilities.issuedreceipts.preview.PreviewLogoSource
import com.naniger.arzikina.util.appLocale
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/** Consultation d'un reçu : aperçu fidèle au PDF + actions (voir [IssuedReceiptDetailViewModel]). */
@AndroidEntryPoint
class IssuedReceiptDetailFragment : Fragment(R.layout.fragment_issued_receipt_detail) {

    private val viewModel: IssuedReceiptDetailViewModel by viewModels()
    private var binding: FragmentIssuedReceiptDetailBinding? = null
    private var logo: Bitmap? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentIssuedReceiptDetailBinding.bind(view)
        binding = b
        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        b.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> { edit(); true }
                R.id.action_delete -> { confirmDelete(); true }
                else -> false
            }
        }
        b.openButton.setOnClickListener { viewModel.onOpenPdf(language()) }
        b.shareButton.setOnClickListener { viewModel.onSharePdf(language()) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.load()
                launch { viewModel.state.collect { render(it) } }
                launch { viewModel.working.collect { binding?.progress?.visibility = if (it) View.VISIBLE else View.INVISIBLE } }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        logo = null
    }

    private fun language(): String = requireContext().appLocale().language

    private fun edit() = findNavController().navigate(R.id.issuedReceiptFormFragment, bundleOf("receiptId" to viewModel.receiptId))

    private fun confirmDelete() {
        val number = (viewModel.state.value as? IssuedReceiptDetailState.Loaded)?.receipt?.number ?: return
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.issued_receipt_history_delete_title, number),
            message = getString(R.string.issued_receipt_history_delete_message)
        ) { viewModel.onDeleteConfirmed() }
    }

    private suspend fun render(state: IssuedReceiptDetailState) {
        val b = binding ?: return
        b.loading.visibility = if (state is IssuedReceiptDetailState.Loading) View.VISIBLE else View.GONE
        b.notFound.visibility = if (state is IssuedReceiptDetailState.NotFound) View.VISIBLE else View.GONE
        val loaded = state as? IssuedReceiptDetailState.Loaded
        b.previewScroll.visibility = if (loaded != null) View.VISIBLE else View.GONE
        b.actionBar.visibility = if (loaded != null) View.VISIBLE else View.GONE
        b.toolbar.menu.findItem(R.id.action_edit)?.isVisible = loaded != null
        b.toolbar.menu.findItem(R.id.action_delete)?.isVisible = loaded != null
        if (loaded != null) showPreview(b, loaded.receipt)
    }

    private suspend fun showPreview(b: FragmentIssuedReceiptDetailBinding, receipt: IssuedReceipt) {
        b.toolbar.title = receipt.number
        logo = receipt.issuer.logoFileName?.let { name ->
            viewModel.logoFile(name)?.let { PreviewLogoLoader.load(requireContext(), PreviewLogoSource.Stored(it)) }
        }
        val current = logo
        b.receiptPreview.show(ReceiptPdfContentBuilder.build(receipt, language(), current?.let { PdfLogo(it.width, it.height) }), current)
    }

    private fun handleEvent(event: IssuedReceiptDetailEvent) {
        val root = binding?.root ?: return
        when (event) {
            is IssuedReceiptDetailEvent.Message -> Snackbar.make(root, event.messageRes, Snackbar.LENGTH_LONG).show()
            is IssuedReceiptDetailEvent.Deleted -> findNavController().navigateUp()
            is IssuedReceiptDetailEvent.PdfReady ->
                if (event.share) {
                    val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
                        .putExtra(Intent.EXTRA_STREAM, Uri.parse(event.file.uri))
                        .putExtra(Intent.EXTRA_SUBJECT, event.file.fileName)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivityOrWarn(Intent.createChooser(send, getString(R.string.issued_receipt_action_share)))
                } else {
                    startActivityOrWarn(
                        Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(event.file.uri), "application/pdf")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                }
        }
    }

    private fun startActivityOrWarn(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            binding?.root?.let { Snackbar.make(it, R.string.issued_receipt_no_pdf_app, Snackbar.LENGTH_LONG).show() }
        }
    }
}
