import Foundation

/// Saisie du formulaire de catégorie — Android `CategoryFormState`.
public struct CategoryDraft: Equatable, Sendable {
    public var name: String
    public var icon: CategoryIcon
    public var colorArgb: Int64
    /// `.income` ou `.expense` (une catégorie n'est jamais un transfert).
    public var type: TransactionType

    /// Couleur d'une nouvelle catégorie : la première de la palette, comme Android.
    public static let defaultColorArgb: Int64 = 0xFF10_B981

    /// Nouvelle catégorie : une dépense, icône « Autre ». [type] permet de l'ouvrir déjà sur le
    /// type affiché par la liste.
    public init(type: TransactionType = .expense) {
        name = ""
        icon = .other
        colorArgb = Self.defaultColorArgb
        self.type = type == .income ? .income : .expense
    }

    /// Modification : [displayName] est le nom AFFICHÉ (« Salary » en anglais pour une catégorie
    /// par défaut), comme Android ; il redevient le nom de référence à l'enregistrement.
    public init(editing category: Category, displayName: String) {
        name = displayName
        icon = category.icon
        colorArgb = category.colorArgb
        type = category.type
    }
}

public enum CategoryFormError: Error, Equatable, Sendable {
    case nameRequired
}

/// Résultat d'une demande de suppression de catégorie.
public enum CategoryDeletion: Equatable, Sendable {
    case deleted
    /// Encore utilisée (transactions, budgets, automatisations, modèles ou planifications) : la
    /// supprimer laisserait ces éléments « sans catégorie » sur tous les appareils.
    case inUse
    /// Catégorie gérée par l'app (prêts, frais) : voir `SystemCategoryKey.isManagedAutomatically`.
    case managedAutomatically
}

public enum CategoryForm {

    /// Catégorie à enregistrer, ou l'erreur de saisie.
    ///
    /// - [existing] : la catégorie modifiée (`nil` pour une création) ; sa date de création est
    ///   conservée.
    /// - [canonicalName] : nom de référence d'un nom par défaut saisi dans n'importe quelle
    ///   langue (« Salary » → « Salaire »), voir `SystemCategoryKey.canonicalName(for:type:labelsOf:)`.
    public static func build(
        _ draft: CategoryDraft,
        existing: Category?,
        newId: @autoclosure () -> EntityID,
        now: EpochMillis,
        canonicalName: (String, TransactionType) -> String = { name, _ in name }
    ) -> Result<Category, CategoryFormError> {
        let name = draft.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return .failure(.nameRequired) }
        let type: TransactionType = draft.type == .income ? .income : .expense
        return .success(Category(
            id: existing?.id ?? newId(),
            name: canonicalName(name, type),
            icon: draft.icon,
            colorArgb: draft.colorArgb,
            type: type,
            createdAt: existing?.createdAt ?? now
        ))
    }

    /// Une catégorie gérée par l'app ne se modifie pas et ne se supprime pas depuis l'écran des
    /// catégories.
    public static func isEditable(_ category: Category) -> Bool {
        !(category.systemKey?.isManagedAutomatically ?? false)
    }
}

extension SystemCategoryKey {

    /// Catégories que l'app crée et utilise ELLE-MÊME (prêts et remboursements, frais), retrouvées
    /// par leur nom de référence.
    ///
    /// Elles ne sont ni proposées dans le formulaire de transaction, ni modifiables : renommée, une
    /// catégorie de prêt ne serait plus reconnue et l'app en recréerait une autre.
    public var isManagedAutomatically: Bool {
        switch self {
        case .loanDisbursementLent, .loanRepaymentLent, .loanDisbursementBorrowed, .loanRepaymentBorrowed, .fees:
            return true
        default:
            return false
        }
    }

    /// Nom à ENREGISTRER pour une saisie : le libellé d'une catégorie par défaut de même [type],
    /// dans n'importe quelle langue ([labelsOf]), devient son nom de référence ; toute autre saisie
    /// est conservée — Android `SystemCategoryKey.canonicalNameFor`.
    public static func canonicalName(for input: String, type: TransactionType, labelsOf: (SystemCategoryKey) -> [String]) -> String {
        allCases.first { key in
            key.type == type && (input == key.canonicalName || labelsOf(key).contains(input))
        }?.canonicalName ?? input
    }
}
