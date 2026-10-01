import ArzikinaDomain
import Foundation
import Observation

/// Écran « Mes catégories » : catégories filtrables par type et suppression — Android
/// `CategoriesViewModel`.
@MainActor
@Observable
final class CategoriesViewModel {

    /// Filtre en haut de l'écran (Android `CategoryFilter`).
    enum Filter: CaseIterable, Hashable {
        case all, expense, income

        /// Type d'une nouvelle catégorie créée depuis ce filtre.
        var typeForNewCategory: TransactionType { self == .income ? .income : .expense }

        func includes(_ category: ArzikinaDomain.Category) -> Bool {
            switch self {
            case .all: return true
            case .expense: return category.type == .expense
            case .income: return category.type == .income
            }
        }
    }

    enum DeletionOutcome: Equatable {
        case deleted, inUse, failed
    }

    var filter: Filter = .all
    private(set) var categories: [ArzikinaDomain.Category] = []
    private(set) var hasLoaded = false

    /// Catégories modifiables du filtre, dépenses puis revenus, triées par nom AFFICHÉ.
    var expenseCategories: [ArzikinaDomain.Category] { editable(of: .expense) }
    var incomeCategories: [ArzikinaDomain.Category] { editable(of: .income) }

    /// Catégories gérées par l'app (prêts, frais) du filtre : affichées à part, en lecture seule.
    var managedCategories: [ArzikinaDomain.Category] {
        sorted(categories.filter { filter.includes($0) && !CategoryForm.isEditable($0) })
    }

    var isFilteredListEmpty: Bool {
        expenseCategories.isEmpty && incomeCategories.isEmpty && managedCategories.isEmpty
    }

    func observe(_ repository: CategoryRepository) async {
        for await categories in repository.observeCategories(type: nil) {
            self.categories = categories
            hasLoaded = true
        }
    }

    func delete(_ category: ArzikinaDomain.Category, using repository: CategoryRepository) async -> DeletionOutcome {
        do {
            switch try await repository.delete(id: category.id) {
            case .deleted: return .deleted
            case .inUse, .managedAutomatically: return .inUse
            }
        } catch {
            return .failed
        }
    }

    private func editable(of type: TransactionType) -> [ArzikinaDomain.Category] {
        sorted(categories.filter { $0.type == type && filter.includes($0) && CategoryForm.isEditable($0) })
    }

    private func sorted(_ categories: [ArzikinaDomain.Category]) -> [ArzikinaDomain.Category] {
        categories.sorted { $0.displayName.localizedStandardCompare($1.displayName) == .orderedAscending }
    }
}
