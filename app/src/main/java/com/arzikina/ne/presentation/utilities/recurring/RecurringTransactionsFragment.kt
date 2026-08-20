package com.arzikina.ne.presentation.utilities.recurring

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentRecurringTransactionsBinding
import com.arzikina.ne.presentation.components.NavAnimations
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
 * Un tap sur une ligne "À venir" ouvre le formulaire en mode édition pour la règle correspondante
 * (voir [onOccurrenceRowClick]) — modifier ou supprimer (bouton dédié du formulaire, déjà géré par
 * [RecurringTransactionFormFragment]) passent tous les deux par cet unique écran, même principe que
 * [com.arzikina.ne.presentation.budget.BudgetFragment]. "À traiter" reste volontairement inerte au
 * tap : réservée à un futur dialogue de validation Enregistrer/Modifier/Rejeter (voir cahier des
 * charges, section "Dialog"), une interaction distincte de l'édition de la règle elle-même, pas
 * encore construite. "Historique" reste inerte aussi pour cette version.
 */
@AndroidEntryPoint
class RecurringTransactionsFragment : Fragment(R.layout.fragment_recurring_transactions) {

    private val viewModel: RecurringTransactionsViewModel by viewModels()
    private var binding: FragmentRecurringTransactionsBinding? = null
    private val adapter = RecurringTransactionsAdapter(onOccurrenceClick = ::onOccurrenceRowClick)

    /**
     * Demande la permission `POST_NOTIFICATIONS` (Android 13+) au premier passage sur CET écran
     * plutôt qu'au démarrage de l'app : Automatisation est la seule fonctionnalité qui en a besoin
     * (voir `com.arzikina.ne.work.AutomationNotifier`), demander au moment où elle devient
     * pertinente pour l'utilisateur plutôt qu'à froid, sans contexte, est la pratique recommandée
     * par Android. Résultat volontairement ignoré : un refus ne bloque jamais la création/
     * modification d'une automatisation (voir `RecurringTransactionFormFragment`, inchangé) — seule
     * la notification de rappel ne s'affichera pas (voir `AutomationNotifier.notifyTrigger`, qui gère
     * déjà silencieusement ce cas sans vérification supplémentaire de son côté).
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* résultat ignoré, voir doc ci-dessus */ }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentRecurringTransactionsBinding.bind(view)
        binding = viewBinding

        viewBinding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        viewBinding.recurringTransactionsList.layoutManager = LinearLayoutManager(requireContext())
        viewBinding.recurringTransactionsList.adapter = adapter
        viewBinding.addRecurringTransactionButton.setOnClickListener { navigateToForm() }
        viewBinding.emptyAddRecurringTransactionButton.setOnClickListener { navigateToForm() }
        requestNotificationPermissionIfNeeded()

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

    /** Ne fait rien avant Android 13 (`POST_NOTIFICATIONS` n'existe pas, les notifications sont
     * autorisées par défaut) ni si déjà accordée — évite de rouvrir inutilement la boîte de dialogue
     * système à chaque passage sur cet écran une fois la permission déjà en main. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun navigateToForm() {
        // Toujours en création (recurringTransactionId par défaut = 0L, voir nav_graph.xml) — l'édition
        // d'une règle existante passe par onOccurrenceRowClick, pas par ce bouton d'ajout.
        findNavController().navigate(R.id.recurringTransactionFormFragment, null, NavAnimations.push)
    }

    /**
     * Seule la section "À venir" ouvre le formulaire en mode édition (voir la doc de classe) : la
     * règle y est TOUJOURS représentée par exactement une ligne tant qu'elle reste active (voir
     * `RecurringTransactionsViewModel.upcomingItems`, basé sur `nextExecutionDate`), donc toute
     * automatisation active reste modifiable/supprimable par ce biais, sans exception. "À traiter"
     * et "Historique" ne font rien au tap pour l'instant.
     */
    private fun onOccurrenceRowClick(item: RecurringOccurrenceUiItem, section: RecurringSection) {
        if (section != RecurringSection.UPCOMING) return
        findNavController().navigate(
            R.id.recurringTransactionFormFragment,
            bundleOf("recurringTransactionId" to item.recurringTransaction.id),
            NavAnimations.push
        )
    }
}
