package com.arzikina.ne.presentation.settings

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentBackupBinding
import com.arzikina.ne.domain.model.BackupResult
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.util.Constants
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Première interface réelle pour [BackupViewModel] (voir sa doc — jusqu'ici sans aucune UI dans
 * la reconstruction XML/Views, [SettingsFragment] étant resté un simple placeholder). Ni
 * [BackupViewModel] ni [com.arzikina.ne.domain.repository.BackupRepository] ne sont modifiés.
 *
 * Les flux ([java.io.OutputStream]/[java.io.InputStream]) sont résolus ici, à partir de l'[Uri]
 * choisie via le Storage Access Framework (voir [BackupViewModel], qui documente ce découpage) —
 * jamais fermés explicitement par ce Fragment : [BackupViewModel]/[com.arzikina.ne.data.repository.BackupRepositoryImpl]
 * les referment eux-mêmes une fois la lecture/écriture terminée (voir leur `use { }`).
 */
@AndroidEntryPoint
class BackupFragment : Fragment(R.layout.fragment_backup) {

    private val viewModel: BackupViewModel by viewModels()
    private var binding: FragmentBackupBinding? = null

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { onExportDestinationChosen(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { confirmImport(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentBackupBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        viewBinding.exportButton.setOnClickListener {
            exportLauncher.launch(Constants.DEFAULT_BACKUP_FILE_NAME)
        }
        viewBinding.importButton.setOnClickListener {
            importLauncher.launch(arrayOf("application/json"))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event -> handleEvent(event) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun onExportDestinationChosen(uri: Uri) {
        val outputStream = requireContext().contentResolver.openOutputStream(uri) ?: return
        viewModel.exportBackup(outputStream)
    }

    /** Remplace TOUTES les données actuelles (voir [BackupRepositoryImpl.importBackup]) :
     * confirmation obligatoire avant d'ouvrir le flux, même schéma que la suppression d'un
     * compte/d'une transaction (voir [ConfirmDialogs]). */
    private fun confirmImport(uri: Uri) {
        ConfirmDialogs.confirm(
            context = requireContext(),
            title = getString(R.string.settings_backup_import_confirm_title),
            message = getString(R.string.settings_backup_import_confirm_message),
            confirmLabel = getString(R.string.settings_backup_import_confirm_action),
            onConfirm = {
                val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return@confirm
                viewModel.importBackup(inputStream)
            }
        )
    }

    private fun handleEvent(event: BackupEvent) {
        val binding = binding ?: return
        val message = when (event) {
            is BackupEvent.ExportSuccess -> formatResult(R.string.settings_backup_export_success, event.result)
            is BackupEvent.ImportSuccess -> formatResult(R.string.settings_backup_import_success, event.result)
            is BackupEvent.Error -> "${getString(R.string.settings_backup_error_prefix)} ${event.message}"
        }
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun formatResult(messageRes: Int, result: BackupResult): String = getString(
        messageRes,
        result.accountsCount,
        result.categoriesCount,
        result.transactionsCount,
        result.budgetsCount,
        result.savingsGoalsCount,
        result.loansCount
    )
}
