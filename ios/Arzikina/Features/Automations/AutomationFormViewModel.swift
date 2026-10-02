import ArzikinaDomain
import Foundation
import Observation

/// Création, modification et suppression d'une automatisation — Android
/// `RecurringTransactionFormViewModel`.
///
/// Les règles (validation, ce qui est conservé en modification) sont dans le domaine
/// (`AutomationForm`) ; l'écriture est LOCALE, l'envoi suit à la synchronisation.
@MainActor
@Observable
final class AutomationFormViewModel {

    enum Mode {
        case create
        case edit(RecurringTransaction)
    }

    var draft: AutomationDraft {
        didSet {
            error = nil
            saveFailed = false
        }
    }
    let choices = AutomationChoices()
    private(set) var error: AutomationFormError?
    private(set) var isWorking = false
    private(set) var saveFailed = false
    private(set) var deleteFailed = false
    /// Ce que la suppression emporterait, chargé quand on la demande.
    private(set) var deletionImpact: AutomationDeletionImpact?

    let isEditing: Bool

    @ObservationIgnored private let existing: RecurringTransaction?
    @ObservationIgnored private let repository: RecurringRepository
    @ObservationIgnored private let calendar: Calendar

    init(mode: Mode, repository: RecurringRepository, calendar: Calendar = ArzikinaCalendar.current) {
        self.repository = repository
        self.calendar = calendar
        switch mode {
        case .create:
            existing = nil
            isEditing = false
            draft = AutomationDraft(today: .today(calendar: calendar), accountId: nil)
        case .edit(let rule):
            existing = rule
            isEditing = true
            draft = AutomationDraft(editing: rule, calendar: calendar)
        }
    }

    // MARK: - Listes

    /// Nouvelle automatisation : premier compte présélectionné.
    func observeAccounts(_ repository: AccountRepository) async {
        await choices.observeAccounts(repository) { [weak self] accounts in
            guard let self, !self.isEditing, self.draft.details.accountId == nil else { return }
            self.draft.details.accountId = accounts.first?.id
        }
    }

    func observeCategories(_ repository: CategoryRepository) async {
        await choices.observeCategories(repository)
    }

    // MARK: - Dates

    var startDate: Date {
        get { date(draft.startDay) }
        set { draft.startDay = day(newValue) }
    }

    var endDate: Date {
        get { date(draft.endDay) }
        set { draft.endDay = day(newValue) }
    }

    /// Heure de déclenchement (seules l'heure et la minute comptent).
    var triggerTime: Date {
        get {
            let millis = CalendarDay.today(calendar: calendar).millis(hour: draft.triggerHour, minute: draft.triggerMinute, calendar: calendar)
            return Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
        }
        set {
            let components = calendar.dateComponents([.hour, .minute], from: newValue)
            draft.triggerHour = components.hour ?? RecurringTransaction.defaultTriggerHour
            draft.triggerMinute = components.minute ?? RecurringTransaction.defaultTriggerMinute
        }
    }

    private func date(_ day: CalendarDay) -> Date {
        Date(timeIntervalSince1970: TimeInterval(day.startOfDayMillis(calendar: calendar)) / 1000)
    }

    private func day(_ date: Date) -> CalendarDay {
        CalendarDay(epochMillis: EpochMillis((date.timeIntervalSince1970 * 1000).rounded()), calendar: calendar)
    }

    // MARK: - Enregistrement

    /// `true` si l'écran peut se fermer.
    func save() async -> Bool {
        guard !isWorking else { return false }
        isWorking = true
        defer { isWorking = false }

        let result = AutomationForm.build(
            draft,
            existing: existing,
            newId: EntityIDs.generate(),
            now: EpochMillis((Date().timeIntervalSince1970 * 1000).rounded()),
            calendar: calendar
        )
        switch result {
        case .failure(let fieldError):
            error = fieldError
            return false
        case .success(let rule):
            do {
                try await repository.save(rule)
                return true
            } catch {
                saveFailed = true
                return false
            }
        }
    }

    // MARK: - Suppression

    /// Charge ce que la suppression emporterait (pour la confirmation).
    func prepareDeletion() async {
        guard let existing else { return }
        deleteFailed = false
        deletionImpact = (try? await repository.deletionImpact(ruleId: existing.id)) ?? AutomationDeletionImpact(createdTransactionCount: 0)
    }

    func cancelDeletion() {
        deletionImpact = nil
    }

    /// `true` si l'écran peut se fermer.
    func delete(deleteCreatedTransactions: Bool) async -> Bool {
        guard let existing, !isWorking else { return false }
        isWorking = true
        defer {
            isWorking = false
            deletionImpact = nil
        }
        do {
            try await repository.delete(ruleId: existing.id, deleteCreatedTransactions: deleteCreatedTransactions)
            return true
        } catch {
            deleteFailed = true
            return false
        }
    }
}
