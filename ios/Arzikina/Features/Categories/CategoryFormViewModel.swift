import ArzikinaDomain
import Foundation
import Observation

/// Création, modification et suppression d'une catégorie — Android `CategoryFormViewModel`.
///
/// Les règles (nom obligatoire, nom de référence d'une catégorie par défaut) sont dans le
/// domaine (`CategoryForm`) ; l'écriture est LOCALE, l'envoi au serveur suit à la synchronisation.
@MainActor
@Observable
final class CategoryFormViewModel {

    enum Mode {
        /// [type] : type présélectionné (filtre de la liste, ou type de la transaction en cours).
        case create(type: TransactionType)
        case edit(ArzikinaDomain.Category)
    }

    var draft: CategoryDraft {
        didSet { if draft.name != oldValue.name { error = nil } }
    }
    private(set) var error: CategoryFormError?
    private(set) var isWorking = false
    private(set) var saveFailed = false
    /// Résultat d'une suppression refusée (catégorie utilisée), ou échec technique.
    private(set) var deletionProblem: DeletionProblem?

    enum DeletionProblem: Equatable {
        case inUse
        case failed
    }

    let isEditing: Bool
    /// `false` pour une catégorie gérée par l'app (prêts, frais) : lecture seule.
    let isEditable: Bool

    @ObservationIgnored private let existing: ArzikinaDomain.Category?
    @ObservationIgnored private let repository: CategoryRepository

    init(mode: Mode, repository: CategoryRepository) {
        self.repository = repository
        switch mode {
        case .create(let type):
            existing = nil
            isEditing = false
            isEditable = true
            draft = CategoryDraft(type: type)
        case .edit(let category):
            existing = category
            isEditing = true
            isEditable = CategoryForm.isEditable(category)
            draft = CategoryDraft(editing: category, displayName: category.displayName)
        }
    }

    var colorChoices: [Int64] {
        ColorPalette.choices(including: draft.colorArgb)
    }

    /// Nom affiché dans la confirmation de suppression.
    var originalName: String {
        existing?.displayName ?? ""
    }

    /// Enregistre ; retourne la catégorie enregistrée, ou `nil` si l'écran doit rester ouvert.
    func save() async -> ArzikinaDomain.Category? {
        guard isEditable, !isWorking else { return nil }
        isWorking = true
        defer { isWorking = false }
        saveFailed = false

        let result = CategoryForm.build(
            draft,
            existing: existing,
            newId: EntityIDs.generate(),
            now: EpochMillis((Date().timeIntervalSince1970 * 1000).rounded()),
            canonicalName: DomainDisplay.canonicalCategoryName
        )
        switch result {
        case .failure(let fieldError):
            error = fieldError
            return nil
        case .success(let category):
            do {
                try await repository.save(category)
                return category
            } catch {
                saveFailed = true
                return nil
            }
        }
    }

    /// Supprime ; `true` si l'écran peut se fermer.
    func delete() async -> Bool {
        guard let existing, isEditable, !isWorking else { return false }
        isWorking = true
        defer { isWorking = false }
        deletionProblem = nil
        do {
            switch try await repository.delete(id: existing.id) {
            case .deleted: return true
            case .inUse, .managedAutomatically: deletionProblem = .inUse
            }
        } catch {
            deletionProblem = .failed
        }
        return false
    }
}
