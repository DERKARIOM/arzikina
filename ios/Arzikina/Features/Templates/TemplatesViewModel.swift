import ArzikinaDomain
import Foundation
import Observation

/// « Modèles de transactions » — Android `MarketplaceViewModel` : favoris en tête, recherche
/// (nom, description), filtre par catégorie, et les actions favori / dupliquer / supprimer.
///
/// Modèles, catégories et comptes sont lus en continu ; la liste affichée est recalculée EN
/// MÉMOIRE (`TemplateLibrary`) à chaque changement ou saisie, sans relire la base.
@MainActor
@Observable
final class TemplatesViewModel {

    var filters = TemplateFilters() {
        didSet { if filters != oldValue { recompute() } }
    }
    private(set) var library: TemplateLibrary = .empty
    private(set) var hasLoaded = false
    private(set) var actionFailed = false

    @ObservationIgnored private var templates: [TransactionTemplate] = []
    @ObservationIgnored private var categories: [ArzikinaDomain.Category] = []
    @ObservationIgnored private var accounts: [Account] = []

    // MARK: - Observation

    func observeTemplates(_ repository: TransactionTemplateRepository) async {
        for await templates in repository.observeTemplates() {
            self.templates = templates
            hasLoaded = true
            recompute()
        }
    }

    func observeCategories(_ repository: CategoryRepository) async {
        for await categories in repository.observeCategories(type: nil) {
            self.categories = categories
            // Catégorie filtrée supprimée entre-temps : filtre retiré.
            if let id = filters.categoryId, !categories.contains(where: { $0.id == id }) { filters.categoryId = nil }
            recompute()
        }
    }

    func observeAccounts(_ repository: AccountRepository) async {
        for await accounts in repository.observeAccounts() {
            self.accounts = accounts
            recompute()
        }
    }

    // MARK: - Actions

    func toggleFavorite(_ item: TemplateItem, using repository: TransactionTemplateRepository) async {
        await perform { try await repository.setFavorite(id: item.id, isFavorite: !item.template.isFavorite) }
    }

    func duplicate(_ item: TemplateItem, using repository: TransactionTemplateRepository) async {
        let name = TemplateNaming.duplicateName(of: item.template.name, suffix: DomainDisplay.localized("templates.copy_suffix"))
        await perform { try await repository.duplicate(id: item.id, name: name) }
    }

    func delete(_ item: TemplateItem, using repository: TransactionTemplateRepository) async {
        await perform { try await repository.delete(id: item.id) }
    }

    func dismissFailure() { actionFailed = false }

    private func perform(_ action: () async throws -> Void) async {
        do {
            try await action()
        } catch {
            // Supprimé entre-temps sur un autre appareil, par exemple : la liste se met à jour.
            actionFailed = true
        }
    }

    private func recompute() {
        library = TemplateLibrary.make(
            templates: templates,
            categories: categories,
            accounts: accounts,
            filters: filters,
            categoryName: { $0.displayName }
        )
    }
}
