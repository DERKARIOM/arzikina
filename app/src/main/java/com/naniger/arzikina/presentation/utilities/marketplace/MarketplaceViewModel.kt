package com.naniger.arzikina.presentation.utilities.marketplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.Category
import com.naniger.arzikina.domain.model.TransactionTemplate
import com.naniger.arzikina.domain.repository.AccountRepository
import com.naniger.arzikina.domain.repository.CategoryRepository
import com.naniger.arzikina.domain.repository.TransactionTemplateRepository
import com.naniger.arzikina.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Filtres appliqués à la liste des modèles — même schéma que `ReceiptFilters`. `categoryId ==
 * null` signifie "Toutes" (cahier des charges section 8). */
data class MarketplaceFilters(
    val query: String = "",
    val categoryId: Long? = null
) {
    /** Exclut volontairement [query] : même raisonnement que `ReceiptFilters.hasActiveFilters`
     *  (le champ de recherche a déjà son propre bouton "effacer"). */
    val hasActiveFilters: Boolean get() = categoryId != null
}

/**
 * État de l'écran "Marketplace personnelle" : [rows] déjà prêtes pour [MarketplaceAdapter], et
 * [totalTemplateCount] — nombre TOTAL de modèles existants, indépendamment de la recherche/du
 * filtre courant. Permet à [MarketplaceFragment] de distinguer "aucun modèle du tout" (état vide
 * illustré) de "la recherche/le filtre actuel ne retourne rien" ([MarketplaceListRow.NoResults]) —
 * même raisonnement que `LoansUiState.summary.totalCount`/`LoansFragment.hasAnyLoans`.
 */
data class MarketplaceUiState(
    val rows: List<MarketplaceListRow>,
    val totalTemplateCount: Int
)

/**
 * ViewModel de l'écran "Marketplace personnelle" (cahier des charges du même nom) : liste des
 * modèles de transaction, favoris en tête (voir section 7), recherche instantanée (nom +
 * description) et filtre par catégorie (voir section 8) — parmi les catégories effectivement
 * utilisées par au moins un modèle, pas la totalité des catégories de l'app (voir
 * [categoryFilterOptions]) : inutile de proposer un filtre pour une catégorie qui n'a aucun modèle.
 *
 * Le filtrage/tri se fait en mémoire après lecture de
 * [TransactionTemplateRepository.observeTemplates], comme le reste de l'application (voir
 * `ReceiptsViewModel`) — un volume de modèles personnel reste modeste.
 *
 * Les actions du menu ⋮ qui ne dépendent PAS du formulaire (Dupliquer/Favoris/Supprimer, voir
 * cahier des charges section 6) sont déjà branchées ici ; "Modifier" sera ajouté avec le formulaire
 * de modèle (étape suivante, voir [MarketplaceFragment]) plutôt que de pointer vers un écran qui
 * n'existe pas encore.
 */
@HiltViewModel
class MarketplaceViewModel @Inject constructor(
    private val templateRepository: TransactionTemplateRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _filters = MutableStateFlow(MarketplaceFilters())
    val filters: StateFlow<MarketplaceFilters> = _filters.asStateFlow()

    /** Source combinée unique (voir [uiState]/[categoryFilterOptions]) : évite deux abonnements
     * séparés à [TransactionTemplateRepository.observeTemplates]/[CategoryRepository.observeCategories]
     * pour ces deux flux dérivés. */
    private val combinedData: StateFlow<CombinedData> = combine(
        templateRepository.observeTemplates(),
        categoryRepository.observeCategories(),
        accountRepository.observeAccounts()
    ) { templates, categories, accounts -> CombinedData(templates, categories, accounts) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), CombinedData.EMPTY)

    val categoryFilterOptions: StateFlow<List<Category>> = combinedData
        .map { data -> data.categories.filter { category -> data.templates.any { it.categoryId == category.id } }.sortedBy { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), emptyList())

    val uiState: StateFlow<AppResult<MarketplaceUiState>> = combine(combinedData, _filters) { data, filters ->
        buildUiState(data.templates, data.categories, data.accounts, filters)
    }
        .map<MarketplaceUiState, AppResult<MarketplaceUiState>> { AppResult.Success(it) }
        .catch { throwable -> emit(AppResult.Error(throwable.message ?: "Erreur inconnue", throwable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = AppResult.Loading
        )

    fun onQueryChange(query: String) {
        _filters.update { it.copy(query = query) }
    }

    fun onCategoryFilterChange(categoryId: Long?) {
        _filters.update { it.copy(categoryId = categoryId) }
    }

    fun resetFilters() {
        _filters.update { it.copy(categoryId = null) }
    }

    fun onToggleFavorite(item: TransactionTemplateListItem) {
        viewModelScope.launch {
            runCatching { templateRepository.setFavorite(item.id, !item.isFavorite) }
        }
    }

    fun onDuplicate(item: TransactionTemplateListItem) {
        viewModelScope.launch {
            runCatching { templateRepository.duplicateTemplate(item.id) }
        }
    }

    fun onDelete(item: TransactionTemplateListItem) {
        viewModelScope.launch {
            runCatching { templateRepository.deleteTemplate(item.id) }
        }
    }

    private fun buildUiState(
        templates: List<TransactionTemplate>,
        categories: List<Category>,
        accounts: List<Account>,
        filters: MarketplaceFilters
    ): MarketplaceUiState {
        val categoriesById = categories.associateBy { it.id }
        val accountsById = accounts.associateBy { it.id }
        val normalizedQuery = filters.query.trim()

        // Ordre déjà favoris en tête / nom croissant (voir TransactionTemplateDao.observeAllForUser)
        // — préservé par filter/mapNotNull ci-dessous, jamais retrié ici.
        val items = templates
            .filter { filters.categoryId == null || it.categoryId == filters.categoryId }
            .filter { matchesQuery(it, normalizedQuery) }
            .mapNotNull { template ->
                // Catégorie/compte introuvables : ne devrait arriver que pour un compte
                // supprimé (CASCADE, voir TransactionTemplateEntity) entre deux lectures — filtré
                // plutôt qu'un crash, jamais une valeur inventée (aucune catégorie n'est censée
                // pouvoir disparaître : contrainte FK NO_ACTION empêche sa suppression tant qu'un
                // modèle la référence).
                val category = categoriesById[template.categoryId] ?: return@mapNotNull null
                val account = accountsById[template.accountId] ?: return@mapNotNull null
                TransactionTemplateListItem(
                    id = template.id,
                    name = template.name,
                    type = template.type,
                    amount = template.amount,
                    currencyCode = account.currencyCode,
                    categoryId = category.id,
                    categoryName = category.name,
                    categoryIcon = category.icon,
                    categoryColorArgb = category.colorArgb,
                    accountId = template.accountId,
                    description = template.description,
                    isFavorite = template.isFavorite,
                    defaultHour = template.defaultHour,
                    defaultMinute = template.defaultMinute
                )
            }

        val rows = when {
            items.isEmpty() && templates.isNotEmpty() -> listOf(MarketplaceListRow.NoResults)
            items.isEmpty() -> emptyList()
            filters.hasActiveFilters || normalizedQuery.isNotEmpty() -> items.map { MarketplaceListRow.TemplateRow(it) }
            else -> buildSectionedRows(items)
        }

        return MarketplaceUiState(rows = rows, totalTemplateCount = templates.size)
    }

    /** Aucune recherche/filtre actif : sépare "⭐ Mes favoris" du reste (cahier des charges
     * section 7) — voir la doc de [MarketplaceListRow.SectionHeader]. */
    private fun buildSectionedRows(items: List<TransactionTemplateListItem>): List<MarketplaceListRow> {
        val favorites = items.filter { it.isFavorite }
        val others = items.filter { !it.isFavorite }
        return buildList {
            if (favorites.isNotEmpty()) {
                add(MarketplaceListRow.SectionHeader(R.string.marketplace_section_favorites))
                addAll(favorites.map { MarketplaceListRow.TemplateRow(it) })
                if (others.isNotEmpty()) {
                    add(MarketplaceListRow.SectionHeader(R.string.marketplace_section_all))
                }
            }
            addAll(others.map { MarketplaceListRow.TemplateRow(it) })
        }
    }

    private fun matchesQuery(template: TransactionTemplate, query: String): Boolean {
        if (query.isEmpty()) return true
        return template.name.contains(query, ignoreCase = true) ||
            template.description.contains(query, ignoreCase = true)
    }

    private data class CombinedData(
        val templates: List<TransactionTemplate>,
        val categories: List<Category>,
        val accounts: List<Account>
    ) {
        companion object {
            val EMPTY = CombinedData(emptyList(), emptyList(), emptyList())
        }
    }
}
