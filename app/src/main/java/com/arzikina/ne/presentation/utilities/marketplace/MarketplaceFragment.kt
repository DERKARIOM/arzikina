package com.arzikina.ne.presentation.utilities.marketplace

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.arzikina.ne.R
import com.arzikina.ne.databinding.FragmentMarketplaceBinding
import com.arzikina.ne.domain.model.Category
import com.arzikina.ne.presentation.components.ConfirmDialogs
import com.arzikina.ne.presentation.components.NavAnimations
import com.arzikina.ne.util.AppResult
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Écran "Marketplace personnelle" (voir [MarketplaceViewModel], cahier des charges du même nom) :
 * bibliothèque personnelle de modèles de transaction — Cards, recherche, filtre par catégorie,
 * favoris en tête, gestion (Dupliquer/Favoris/Supprimer). Même structure que
 * [com.arzikina.ne.presentation.utilities.receipts.ReceiptsFragment]/
 * [com.arzikina.ne.presentation.utilities.LoansFragment].
 *
 * Création/modification d'un modèle (voir [navigateToForm]/[MarketplaceFormFragment]) et "Acheter"
 * (voir [onBuyClicked], réutilise les arguments `preset*` déjà existants de `TransactionFormFragment`)
 * sont branchées. Atteint depuis le bloc Utilitaires (voir `UtilityCatalog`), section Utilitaires
 * — cahier des charges "Marketplace personnelle", section 12.
 */
@AndroidEntryPoint
class MarketplaceFragment : Fragment(R.layout.fragment_marketplace) {

    private val viewModel: MarketplaceViewModel by viewModels()
    private var binding: FragmentMarketplaceBinding? = null

    private val adapter = MarketplaceAdapter(
        onBuyClick = { item -> onBuyClicked(item) },
        onEdit = { item -> navigateToForm(item.id) },
        onToggleFavorite = { item -> viewModel.onToggleFavorite(item) },
        onDuplicate = { item -> viewModel.onDuplicate(item) },
        onDelete = { item -> onDeleteClicked(item) }
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = FragmentMarketplaceBinding.bind(view)
        binding = viewBinding

        setUpToolbar(viewBinding)
        setUpList(viewBinding)
        setUpSearch(viewBinding)

        viewBinding.resetFiltersButton.setOnClickListener { viewModel.resetFilters() }
        viewBinding.addTemplateButton.setOnClickListener { navigateToForm(templateId = 0L) }
        viewBinding.emptyAddTemplateButton.setOnClickListener { navigateToForm(templateId = 0L) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { state -> render(state) } }
                launch { viewModel.filters.collect { filters -> renderFilters(filters) } }
                launch {
                    viewModel.categoryFilterOptions.collect { categories ->
                        setUpCategoryChips(viewBinding, categories, viewModel.filters.value.categoryId)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    private fun setUpToolbar(binding: FragmentMarketplaceBinding) {
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

    private fun toggleFiltersPanel(binding: FragmentMarketplaceBinding) {
        binding.filtersPanel.visibility = if (binding.filtersPanel.visibility == View.VISIBLE) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun setUpList(binding: FragmentMarketplaceBinding) {
        binding.templatesList.layoutManager = LinearLayoutManager(requireContext())
        binding.templatesList.adapter = adapter
    }

    private fun setUpSearch(binding: FragmentMarketplaceBinding) {
        binding.searchInput.doAfterTextChanged { text -> viewModel.onQueryChange(text?.toString().orEmpty()) }
    }

    /**
     * Reconstruit ENTIÈREMENT le ChipGroup dynamique (voir `item_marketplace_category_chip.xml`,
     * contrairement aux chips fixes de `ReceiptsFragment.setUpChips`) : "Toutes" (`tag == null`)
     * suivie d'une Chip par [categories] (`tag == categoryId`, voir
     * [MarketplaceViewModel.categoryFilterOptions]). Appelé uniquement quand l'ensemble des
     * catégories disponibles change — [renderFilters] se contente ensuite de cocher/décocher parmi
     * les chips déjà posées ici, sans jamais les reconstruire.
     */
    private fun setUpCategoryChips(binding: FragmentMarketplaceBinding, categories: List<Category>, selectedCategoryId: Long?) {
        val chipGroup = binding.categoryChipGroup
        chipGroup.removeAllViews()
        chipGroup.setOnCheckedStateChangeListener(null)

        val allChip = inflateCategoryChip(chipGroup).apply {
            id = View.generateViewId()
            text = getString(R.string.marketplace_filter_all)
            tag = null
        }
        chipGroup.addView(allChip)

        categories.forEach { category ->
            val chip = inflateCategoryChip(chipGroup).apply {
                id = View.generateViewId()
                text = category.name
                tag = category.id
            }
            chipGroup.addView(chip)
        }

        val targetChip = chipGroup.children.filterIsInstance<Chip>().firstOrNull { it.tag == selectedCategoryId } ?: allChip
        chipGroup.check(targetChip.id)

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedChip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) }
            val categoryId = checkedChip?.tag as? Long
            if (categoryId != viewModel.filters.value.categoryId) {
                viewModel.onCategoryFilterChange(categoryId)
            }
        }
    }

    private fun inflateCategoryChip(parent: ChipGroup): Chip =
        layoutInflater.inflate(R.layout.item_marketplace_category_chip, parent, false) as Chip

    private fun render(state: AppResult<MarketplaceUiState>) {
        val binding = binding ?: return

        binding.loadingState.visibility = if (state is AppResult.Loading) View.VISIBLE else View.GONE
        binding.errorState.visibility = if (state is AppResult.Error) View.VISIBLE else View.GONE
        if (state is AppResult.Error) {
            binding.errorMessage.text = state.message
        }
        if (state !is AppResult.Success) {
            binding.templatesList.visibility = View.GONE
            binding.emptyState.visibility = View.GONE
            binding.addTemplateButton.visibility = View.GONE
            return
        }

        val uiState = state.data
        // Même raisonnement que LoansFragment.hasAnyLoans/ReceiptsFragment : distingue "aucun
        // modèle du tout" (état vide illustré) de "la recherche/le filtre courant ne retourne rien"
        // (MarketplaceListRow.NoResults, déjà présent dans uiState.rows dans ce cas).
        val hasAnyTemplates = uiState.totalTemplateCount > 0

        binding.searchCard.visibility = if (hasAnyTemplates) View.VISIBLE else View.GONE
        binding.toolbar.menu.findItem(R.id.action_toggle_filters)?.isVisible = hasAnyTemplates
        if (!hasAnyTemplates) binding.filtersPanel.visibility = View.GONE

        binding.addTemplateButton.visibility = if (hasAnyTemplates) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (hasAnyTemplates) View.GONE else View.VISIBLE
        binding.templatesList.visibility = if (hasAnyTemplates) View.VISIBLE else View.GONE

        adapter.submitList(uiState.rows)
    }

    private fun renderFilters(filters: MarketplaceFilters) {
        val binding = binding ?: return
        if (binding.searchInput.text?.toString() != filters.query) {
            binding.searchInput.setText(filters.query)
        }

        val targetChip = binding.categoryChipGroup.children.filterIsInstance<Chip>()
            .firstOrNull { it.tag == filters.categoryId }
        if (targetChip != null && binding.categoryChipGroup.checkedChipId != targetChip.id) {
            binding.categoryChipGroup.check(targetChip.id)
        }

        binding.resetFiltersButton.visibility = if (filters.hasActiveFilters) View.VISIBLE else View.GONE
    }

    /**
     * "Acheter" (cahier des charges section 4) : ouvre `TransactionFormFragment` en mode CRÉATION
     * (`transactionId` non fourni, voir sa valeur par défaut dans nav_graph.xml), préreempli via les
     * arguments `preset*` — DÉJÀ existants dans nav_graph.xml pour la fonctionnalité "Créer une
     * transaction depuis un reçu" (voir `ReceiptDetailFragment`), réutilisés ici tels quels : aucune
     * modification de `TransactionFormFragment`/`TransactionFormViewModel` nécessaire pour cette
     * étape. `presetDescription` omis si vide (`null` = "non renseigné", jamais une chaîne vide
     * inventée — même convention que `ReceiptDetailFragment`).
     *
     * Ne modifie JAMAIS [item] lui-même (cahier des charges section 5) : ce n'est qu'un point de
     * départ pour l'utilisateur, modifiable dans le formulaire avant validation — voir la doc de
     * tête de [com.arzikina.ne.domain.model.TransactionTemplate].
     *
     * `presetDateTimeMillis` (extension "Heure par défaut") : envoyé UNIQUEMENT si le modèle a une
     * heure par défaut ([computePresetDateTimeMillis]), sur la date DU JOUR — jamais celle de la
     * création du modèle. `0L` sinon (valeur par défaut de l'argument nav_graph, voir
     * `TransactionFormViewModel.applyReceiptPresets` : `takeIf { it > 0L }`) : comportement
     * inchangé (heure actuelle) quand aucune heure par défaut n'est définie.
     */
    private fun onBuyClicked(item: TransactionTemplateListItem) {
        findNavController().navigate(
            R.id.transactionFormFragment,
            bundleOf(
                "presetAccountId" to item.accountId,
                "presetAmountMinor" to item.amount,
                "presetDescription" to item.description.ifBlank { null },
                "presetCategoryId" to item.categoryId,
                "presetType" to item.type.name,
                "presetDateTimeMillis" to computePresetDateTimeMillis(item.defaultHour, item.defaultMinute)
            ),
            NavAnimations.push
        )
    }

    /** `null` (pas d'heure par défaut sur ce modèle) → `0L`, ignoré côté formulaire (voir
     * [onBuyClicked]). Sinon : date du jour + heure du modèle, secondes/millisecondes à zéro —
     * même construction `Calendar` que [com.arzikina.ne.util.TriggerTimeFormatter]. */
    private fun computePresetDateTimeMillis(hour: Int?, minute: Int?): Long {
        if (hour == null || minute == null) return 0L
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    /** [templateId] = 0L (voir nav_graph.xml) : création — même convention que
     * `RecurringTransactionsFragment.navigateToForm`. */
    private fun navigateToForm(templateId: Long) {
        findNavController().navigate(
            R.id.marketplaceTemplateFormFragment,
            bundleOf("templateId" to templateId),
            NavAnimations.push
        )
    }

    private fun onDeleteClicked(item: TransactionTemplateListItem) {
        val binding = binding ?: return
        ConfirmDialogs.confirm(
            context = binding.root.context,
            title = getString(R.string.marketplace_delete_confirm_title),
            message = getString(R.string.marketplace_delete_confirm_message, item.name)
        ) {
            viewModel.onDelete(item)
        }
    }
}
