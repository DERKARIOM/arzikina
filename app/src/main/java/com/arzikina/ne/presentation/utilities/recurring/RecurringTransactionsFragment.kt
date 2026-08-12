package com.arzikina.ne.presentation.utilities.recurring

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentRecurringTransactionsBinding
import com.arzikina.ne.util.AppResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Écran "Transactions planifiées" (voir maquette de référence, adaptée au design Arzikina — voir
 * cahier des charges "Gestion automatique des transactions planifiées"). Remplace le placeholder
 * "en cours de développement" comme [com.arzikina.ne.presentation.utilities.loans.LoansFragment] —
 * id de destination `recurringTransactionsFragment` (voir nav_graph.xml).
 *
 * Trois sections défilant dans une seule liste ("À traiter"/"À venir"/"Historique", voir
 * [RecurringTransactionsListRow]) : PAS de recherche/filtres pour cette première version (voir
 * [com.arzikina.ne.presentation.utilities.loans.LoansFragment] pour ce que ça impliquerait
 * d'ajouter plus tard, sur le même modèle).
 *
 * Lecture seule pour l'instant : un tap sur une ligne "À traiter" n'ouvre PAS encore le dialogue de
 * validation Enregistrer/Modifier/Rejeter (voir cahier des charges, section "Dialog") — cette
 * interaction arrive à une étape suivante du plan de développement, distincte de cet écran de
 * consultation.
 */
@AndroidEntryPoint
class RecurringTransactionsFragment : Fragment(R.layout.fragment_recurring_transactions) {

    private val viewModel: RecurringTransactionsViewModel by viewModels()
    private var binding: FragmentRecurringTransactionsBinding? = null
    private val adapter = RecurringTransactionsAdapter(onOccurrenceClick = {})

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentRecurringTransactionsBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        viewBinding.recurringTransactionsList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.recurringTransactionsList.adapter = adapter
        viewBinding.addRecurringTransactionButton.setOnClickListener { navigateToForm() }
        viewBinding.emptyAddRecurringTransactionButton.setOnClickListener { navigateToForm() }

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

    private fun render(state: AppResult<RecurringTransactionsUiState>) {
        val binding = binding ?: return
        if (state !is AppResult.Success) return
        val uiState = state.data

        val hasAnyData = uiState.pendingItems.isNotEmpty() || uiState.upcomingItems.isNotEmpty() || uiState.historyItems.isNotEmpty()

        binding.addRecurringTransactionButton.visibility = if (hasAnyData) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasAnyData) View.GONE else View.VISIBLE
        binding.recurringTransactionsList.visibility = if (hasAnyData) View.VISIBLE else View.GONE

        if (!hasAnyData) {
            adapter.submitList(emptyList())
            return
        }

        val rows = buildList {
            add(RecurringTransactionsListRow.Header(uiState.summary))
            addSection(R.string.recurring_transactions_pending_title, uiState.pendingItems, RecurringSection.PENDING)
            addSection(R.string.recurring_transactions_upcoming_title, uiState.upcomingItems, RecurringSection.UPCOMING)
            addSection(R.string.recurring_transactions_history_title, uiState.historyItems, RecurringSection.HISTORY)
        }
        adapter.submitList(rows)
    }

    /** Une section VIDE n'ajoute ni titre ni ligne (voir la doc de [RecurringTransactionsListRow]). */
    private fun MutableList<RecurringTransactionsListRow>.addSection(
        titleRes: Int,
        items: List<RecurringOccurrenceUiItem>,
        section: RecurringSection
    ) {
        if (items.isEmpty()) return
        add(RecurringTransactionsListRow.SectionTitle(titleRes, items.size))
        items.forEach { add(RecurringTransactionsListRow.OccurrenceRow(it, section)) }
    }

    private fun navigateToForm() {
        // Toujours en création (recurringTransactionId par défaut = 0L, voir nav_graph.xml) : le tap
        // sur une ligne existante pour l'éditer arrive à une étape suivante du plan de développement
        // (voir cahier des charges), distincte de ce bouton d'ajout.
        findNavController().navigate(R.id.recurringTransactionFormFragment)
    }
}
