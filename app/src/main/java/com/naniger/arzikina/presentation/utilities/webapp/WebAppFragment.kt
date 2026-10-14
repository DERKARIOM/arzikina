package com.naniger.arzikina.presentation.utilities.webapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.naniger.arzikina.R
import com.naniger.arzikina.databinding.FragmentWebAppBinding
import com.naniger.arzikina.util.external.WebLinkOpener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Écran « Application web » (Utilitaires) : QR code et lien vers la page de connexion de la
 * console web, avec « Ouvrir » et « Copier ». Aucune donnée personnelle n'est impliquée : le QR ne
 * contient que l'adresse publique de la page de connexion (l'utilisateur s'y connecte lui-même).
 */
@AndroidEntryPoint
class WebAppFragment : Fragment(R.layout.fragment_web_app) {

    private val viewModel: WebAppViewModel by viewModels()
    private var binding: FragmentWebAppBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = FragmentWebAppBinding.bind(view)
        binding = b

        b.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        b.linkText.text = viewModel.loginUrl
        b.linkText.paintFlags = b.linkText.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        b.linkText.setOnClickListener { openLink() }
        b.openButton.setOnClickListener { openLink() }
        b.copyButton.setOnClickListener { copyLink() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.qrCode.collect { bitmap ->
                    b.qrImage.setImageBitmap(bitmap)
                    b.qrProgress.isVisible = bitmap == null
                }
            }
        }
    }

    private fun openLink() {
        if (!WebLinkOpener.open(requireContext(), viewModel.loginUrl)) {
            showMessage(R.string.web_app_open_failed)
        }
    }

    private fun copyLink() {
        val clipboard = requireContext().getSystemService<ClipboardManager>() ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.web_app_link_label), viewModel.loginUrl))
        showMessage(R.string.web_app_link_copied)
    }

    private fun showMessage(messageRes: Int) {
        binding?.root?.let { Snackbar.make(it, messageRes, Snackbar.LENGTH_SHORT).show() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}
