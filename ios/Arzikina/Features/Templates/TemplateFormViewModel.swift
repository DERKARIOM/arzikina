import ArzikinaDomain
import Foundation
import Observation

/// Création, modification et suppression d'un modèle — Android `MarketplaceFormViewModel`, y
/// compris « Créer un modèle à partir de cette transaction ».
///
/// Les règles (validation, ce qui est conservé) sont dans le domaine (`TemplateForm`) ;
/// l'écriture est LOCALE, l'envoi suit à la synchronisation.
@MainActor
@Observable
final class TemplateFormViewModel {

    enum Mode {
        /// Nouveau modèle, compte éventuellement présélectionné.
        case create(accountId: EntityID?)
        case edit(TransactionTemplate)
        /// Pré-rempli par une transaction enregistrée ([categoryName] : nom affiché de sa catégorie).
        case fromTransaction(ArzikinaDomain.Transaction, categoryName: String?)
    }

    var draft: TemplateDraft {
        didSet {
            error = nil
            saveFailed = false
        }
    }
    let choices = AutomationChoices()
    private(set) var error: TemplateFormError?
    private(set) var isWorking = false
    private(set) var saveFailed = false
    private(set) var deleteFailed = false
    /// La transaction d'origine a déjà un modèle : aucun second n'est créé.
    private(set) var isAlreadyLinked = false

    let isEditing: Bool
    let isFromTransaction: Bool

    @ObservationIgnored private let existing: TransactionTemplate?
    @ObservationIgnored private let sourceTransactionId: EntityID?
    @ObservationIgnored private let repository: TransactionTemplateRepository
    @ObservationIgnored private let calendar: Calendar

    init(mode: Mode, repository: TransactionTemplateRepository, calendar: Calendar = ArzikinaCalendar.current) {
        self.repository = repository
        self.calendar = calendar
        switch mode {
        case .create(let accountId):
            existing = nil
            sourceTransactionId = nil
            draft = TemplateDraft(accountId: accountId)
        case .edit(let template):
            existing = template
            sourceTransactionId = nil
            draft = TemplateDraft(editing: template)
        case .fromTransaction(let transaction, let categoryName):
            existing = nil
            sourceTransactionId = transaction.id
            draft = TemplateDraft(from: transaction, categoryName: categoryName)
        }
        isEditing = existing != nil
        isFromTransaction = sourceTransactionId != nil
    }

    /// Erreur à afficher sous les champs communs (type, montant, compte, catégorie).
    var detailsError: AutomationFormError? {
        switch error {
        case .invalidAmount: return .invalidAmount
        case .categoryRequired: return .categoryRequired
        case .accountRequired: return .accountRequired
        case .nameRequired, nil: return nil
        }
    }

    // MARK: - Listes

    /// Nouveau modèle sans compte : le premier est présélectionné.
    func observeAccounts(_ repository: AccountRepository) async {
        await choices.observeAccounts(repository) { [weak self] accounts in
            guard let self, self.existing == nil, self.draft.details.accountId == nil else { return }
            self.draft.details.accountId = accounts.first?.id
        }
    }

    func observeCategories(_ repository: CategoryRepository) async {
        await choices.observeCategories(repository)
    }

    /// À l'ouverture depuis une transaction : un modèle existe peut-être déjà (autre appareil).
    func checkExistingLink() async {
        guard let sourceTransactionId else { return }
        isAlreadyLinked = (try? await repository.template(createdFromTransaction: sourceTransactionId)) != nil
    }

    // MARK: - Heure par défaut

    var defaultTime: Date {
        get {
            let millis = CalendarDay.today(calendar: calendar).millis(hour: draft.defaultHour, minute: draft.defaultMinute, calendar: calendar)
            return Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
        }
        set {
            let components = calendar.dateComponents([.hour, .minute], from: newValue)
            draft.defaultHour = components.hour ?? RecurringTransaction.defaultTriggerHour
            draft.defaultMinute = components.minute ?? RecurringTransaction.defaultTriggerMinute
        }
    }

    // MARK: - Enregistrement

    /// `true` si l'écran peut se fermer.
    func save() async -> Bool {
        guard !isWorking, !isAlreadyLinked else { return false }
        isWorking = true
        defer { isWorking = false }
        let now = EpochMillis((Date().timeIntervalSince1970 * 1000).rounded())
        switch TemplateForm.build(draft, existing: existing, sourceTransactionId: sourceTransactionId, newId: EntityIDs.generate(), now: now) {
        case .failure(let fieldError):
            error = fieldError
            return false
        case .success(let template):
            do {
                try await repository.save(template)
                return true
            } catch TemplateWriteError.alreadyLinked {
                isAlreadyLinked = true
                return false
            } catch {
                saveFailed = true
                return false
            }
        }
    }

    /// `true` si l'écran peut se fermer.
    func delete() async -> Bool {
        guard let existing, !isWorking else { return false }
        isWorking = true
        defer { isWorking = false }
        deleteFailed = false
        do {
            try await repository.delete(id: existing.id)
            return true
        } catch {
            deleteFailed = true
            return false
        }
    }
}
