import Foundation

/// Filtres de la liste des modèles — Android `MarketplaceFilters` : recherche (nom, description)
/// et catégorie (`nil` = toutes).
public struct TemplateFilters: Equatable, Sendable {
    public var query: String
    public var categoryId: EntityID?

    public init(query: String = "", categoryId: EntityID? = nil) {
        self.query = query
        self.categoryId = categoryId
    }

    /// Exclut volontairement la recherche, qui a son propre bouton « effacer ».
    public var hasActiveFilters: Bool { categoryId != nil }
}

/// Un modèle prêt à afficher : sa catégorie (icône, couleur) et son compte (devise).
public struct TemplateItem: Identifiable, Equatable, Sendable {
    public let template: TransactionTemplate
    public let category: Category
    public let account: Account

    public var id: EntityID { template.id }
}

/// Liste « Modèles de transactions » — Android `MarketplaceViewModel.buildUiState`.
public struct TemplateLibrary: Equatable, Sendable {
    /// Sans recherche ni filtre : les favoris, puis les autres. Sinon : tous les résultats dans
    /// [others] (et [favorites] vide), comme Android qui ne sépare plus les sections.
    public var favorites: [TemplateItem]
    public var others: [TemplateItem]
    /// Nombre total de modèles, filtres ou non (distingue « aucun modèle » de « aucun résultat »).
    public var totalCount: Int
    /// Catégories utilisées par au moins un modèle (seules utiles au filtre), par nom.
    public var categoryOptions: [Category]

    public static let empty = TemplateLibrary(favorites: [], others: [], totalCount: 0, categoryOptions: [])

    public var isEmpty: Bool { favorites.isEmpty && others.isEmpty }

    /// [templates] : déjà triés (favoris en tête, puis nom). Un modèle dont la catégorie ou le
    /// compte a disparu (supprimé ailleurs) n'est pas affiché plutôt que d'inventer une valeur.
    /// [categoryName] donne le nom affiché (traduit) d'une catégorie, pour le tri du filtre.
    public static func make(
        templates: [TransactionTemplate],
        categories: [Category],
        accounts: [Account],
        filters: TemplateFilters,
        categoryName: (Category) -> String = { $0.name }
    ) -> TemplateLibrary {
        let categoriesById = Dictionary(categories.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let accountsById = Dictionary(accounts.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let query = filters.query.trimmingCharacters(in: .whitespacesAndNewlines)

        let items: [TemplateItem] = templates.compactMap { template in
            guard filters.categoryId == nil || template.categoryId == filters.categoryId,
                  matches(template, query: query),
                  let category = categoriesById[template.categoryId],
                  let account = accountsById[template.accountId]
            else { return nil }
            return TemplateItem(template: template, category: category, account: account)
        }
        let usedCategoryIds = Set(templates.map(\.categoryId))
        let options = categories
            .filter { usedCategoryIds.contains($0.id) }
            .sorted { categoryName($0).localizedStandardCompare(categoryName($1)) == .orderedAscending }

        let sectioned = query.isEmpty && !filters.hasActiveFilters
        return TemplateLibrary(
            favorites: sectioned ? items.filter(\.template.isFavorite) : [],
            others: sectioned ? items.filter { !$0.template.isFavorite } : items,
            totalCount: templates.count,
            categoryOptions: options
        )
    }

    private static func matches(_ template: TransactionTemplate, query: String) -> Bool {
        guard !query.isEmpty else { return true }
        return template.name.range(of: query, options: .caseInsensitive) != nil
            || template.description.range(of: query, options: .caseInsensitive) != nil
    }
}

extension TransactionDraft {

    /// Formulaire de transaction pré-rempli par un modèle (Android « Acheter ») : type, montant,
    /// compte, catégorie et description du modèle, datée d'AUJOURD'HUI — à l'heure par défaut du
    /// modèle s'il en a une, sinon maintenant. Le modèle lui-même n'est jamais modifié.
    public init(template: TransactionTemplate, now: EpochMillis, calendar: Calendar) {
        var date = now
        if let hour = template.defaultHour, let minute = template.defaultMinute {
            date = CalendarDay(epochMillis: now, calendar: calendar).millis(hour: hour, minute: minute, calendar: calendar)
        }
        self.init(now: date, presetAccountId: template.accountId)
        // Un modèle n'est jamais un transfert : pas de description automatique à calculer.
        changeType(template.type == .transfer ? .expense : template.type, accountName: { _ in nil })
        amountInput = Money.formatForInput(template.amount)
        categoryId = template.categoryId
        changeDescription(template.description, accountName: { _ in nil })
    }
}

public enum TemplateNaming {
    /// Nom de la copie d'un modèle (Android « Nom (copie) ») ; [suffix] est traduit par l'app.
    public static func duplicateName(of name: String, suffix: String) -> String {
        "\(name) \(suffix)"
    }
}

// MARK: - Choisir un modèle depuis le formulaire de transaction

extension TransactionDraft {

    /// « Choisir un modèle » dans une NOUVELLE transaction — Android `applyTemplate` : montant,
    /// type, catégorie et description du modèle ; la date reste le jour choisi (à l'heure par
    /// défaut du modèle s'il en a une). Le COMPTE déjà choisi est conservé (formulaire ouvert depuis
    /// un compte précis), ainsi que les frais et le moyen de paiement.
    public mutating func apply(_ template: TransactionTemplate, calendar: Calendar) {
        changeType(template.type == .transfer ? .expense : template.type, accountName: { _ in nil })
        amountInput = Money.formatForInput(template.amount)
        categoryId = template.categoryId
        changeDescription(template.description, accountName: { _ in nil })
        if let hour = template.defaultHour, let minute = template.defaultMinute {
            date = CalendarDay(epochMillis: date, calendar: calendar).millis(hour: hour, minute: minute, calendar: calendar)
        }
    }
}

// MARK: - Formulaire de modèle

/// Saisie du formulaire de modèle — Android `MarketplaceFormState`. Les champs de la transaction
/// (type, montant, compte, catégorie, description) sont ceux des automatisations
/// (`AutomationDetailsDraft`) : mêmes règles, même validation.
///
/// Le favori n'est PAS modifiable ici (action isolée de la liste) : une modification ne le retire
/// jamais par mégarde.
public struct TemplateDraft: Equatable, Sendable {
    /// Le nom s'affiche sur la ligne du modèle : une longue description n'y est reprise qu'en
    /// partie (Android `MAX_NAME_LENGTH`).
    public static let maxNameLength = 60

    public var name: String
    public var details: AutomationDetailsDraft
    public var hasDefaultTime: Bool
    /// Conservées même quand [hasDefaultTime] est désactivé (réactiver ne perd pas le choix).
    public var defaultHour: Int
    public var defaultMinute: Int

    /// Nouveau modèle : une dépense, sur [accountId] s'il est fourni ; heure par défaut 08:00 (si
    /// activée), comme Android.
    public init(accountId: EntityID?) {
        name = ""
        details = AutomationDetailsDraft(accountId: accountId)
        hasDefaultTime = false
        defaultHour = RecurringTransaction.defaultTriggerHour
        defaultMinute = RecurringTransaction.defaultTriggerMinute
    }

    public init(editing template: TransactionTemplate) {
        name = template.name
        details = AutomationDetailsDraft(
            type: template.type,
            amountInput: Money.formatForInput(template.amount),
            accountId: template.accountId,
            categoryId: template.categoryId,
            description: template.description
        )
        hasDefaultTime = template.defaultHour != nil && template.defaultMinute != nil
        defaultHour = template.defaultHour ?? RecurringTransaction.defaultTriggerHour
        defaultMinute = template.defaultMinute ?? RecurringTransaction.defaultTriggerMinute
    }

    /// « Créer un modèle à partir de cette transaction » — Android `TemplateFromTransaction.prefillOf` :
    /// type, montant, compte, catégorie et description ; jamais la date, le moyen de paiement ni
    /// les frais. Nom proposé : la description, sinon le nom de la catégorie ([categoryName], déjà
    /// traduit), limité à [maxNameLength].
    public init(from transaction: Transaction, categoryName: String?) {
        let description = transaction.description.trimmingCharacters(in: .whitespacesAndNewlines)
        let proposed = description.isEmpty ? (categoryName ?? "").trimmingCharacters(in: .whitespacesAndNewlines) : description
        self.init(accountId: transaction.accountId)
        name = String(proposed.prefix(Self.maxNameLength))
        details = AutomationDetailsDraft(
            type: transaction.type,
            amountInput: Money.formatForInput(transaction.amount),
            accountId: transaction.accountId,
            categoryId: transaction.categoryId,
            description: description
        )
    }
}

/// Erreur de saisie d'un modèle, dans l'ordre d'Android (nom, montant, catégorie, compte).
public enum TemplateFormError: Error, Equatable, Sendable {
    case nameRequired
    case invalidAmount
    case categoryRequired
    case accountRequired
}

public enum TemplateForm {

    /// Une transaction peut servir de base à un modèle — Android `TemplateFromTransaction.isEligible` :
    /// jamais un transfert, jamais une ligne de frais. (Le lien avec un prêt et l'existence d'un
    /// modèle déjà créé sont vérifiés par l'appelant.)
    public static func isEligible(_ transaction: Transaction) -> Bool {
        transaction.type != .transfer && transaction.feeType == nil
    }

    /// Modèle à enregistrer, ou la PREMIÈRE erreur de saisie. En modification, le favori, la date
    /// de création et le lien avec la transaction d'origine sont ceux de [existing] ;
    /// [sourceTransactionId] ne compte qu'à la création.
    public static func build(
        _ draft: TemplateDraft,
        existing: TransactionTemplate?,
        sourceTransactionId: EntityID?,
        newId: @autoclosure () -> EntityID,
        now: EpochMillis
    ) -> Result<TransactionTemplate, TemplateFormError> {
        let name = draft.name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return .failure(.nameRequired) }
        guard let amount = Money.parseToMinorUnits(draft.details.amountInput), amount > 0 else { return .failure(.invalidAmount) }
        guard let categoryId = draft.details.categoryId else { return .failure(.categoryRequired) }
        guard let accountId = draft.details.accountId else { return .failure(.accountRequired) }
        return .success(TransactionTemplate(
            id: existing?.id ?? newId(),
            name: name,
            type: draft.details.type,
            amount: amount,
            categoryId: categoryId,
            accountId: accountId,
            description: draft.details.description.trimmingCharacters(in: .whitespacesAndNewlines),
            isFavorite: existing?.isFavorite ?? false,
            defaultHour: draft.hasDefaultTime ? draft.defaultHour : nil,
            defaultMinute: draft.hasDefaultTime ? draft.defaultMinute : nil,
            sourceTransactionId: existing == nil ? sourceTransactionId : existing?.sourceTransactionId,
            createdAt: existing?.createdAt ?? now,
            updatedAt: now
        ))
    }
}
