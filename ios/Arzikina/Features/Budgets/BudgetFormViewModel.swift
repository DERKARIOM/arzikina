import ArzikinaDomain
import Foundation
import Observation

/// Création, modification et suppression d'un budget — Android `BudgetFormViewModel`.
///
/// Les règles (validation, raccourcis de période, catégories disponibles) sont dans le domaine
/// (`BudgetForm`, `BudgetDraft`) ; l'écriture est LOCALE, l'envoi suit à la synchronisation.
@MainActor
@Observable
final class BudgetFormViewModel {

    enum Mode {
        case create
        case edit(Budget)
    }

    var draft: BudgetDraft {
        didSet {
            error = nil
            saveFailed = false
        }
    }
    private(set) var error: BudgetFormError?
    private(set) var isWorking = false
    private(set) var saveFailed = false
    private(set) var deleteFailed = false
    private(set) var hasLoadedCategories = false

    let isEditing: Bool

    @ObservationIgnored private let existing: Budget?
    @ObservationIgnored private let repository: BudgetRepository
    @ObservationIgnored private let calendar: Calendar
    @ObservationIgnored private let today: CalendarDay
    @ObservationIgnored private var hasChosenCurrency: Bool

    private var expenseCategories: [ArzikinaDomain.Category] = []
    private var budgets: [Budget] = []

    init(mode: Mode, repository: BudgetRepository, calendar: Calendar = ArzikinaCalendar.current) {
        self.repository = repository
        self.calendar = calendar
        today = .today(calendar: calendar)
        switch mode {
        case .create:
            existing = nil
            isEditing = false
            hasChosenCurrency = false
            draft = BudgetDraft(today: today, calendar: calendar)
        case .edit(let budget):
            existing = budget
            isEditing = true
            hasChosenCurrency = true
            draft = BudgetDraft(editing: budget, calendar: calendar)
        }
    }

    // MARK: - Listes

    /// Catégories de dépense sans budget actif (plus celle du budget modifié), triées par nom affiché.
    var availableCategories: [ArzikinaDomain.Category] {
        BudgetForm.availableCategories(expenseCategories, budgets: budgets, editingBudgetId: existing?.id, today: today, calendar: calendar)
            .sorted { $0.displayName.localizedStandardCompare($1.displayName) == .orderedAscending }
    }

    func observeCategories(_ repository: CategoryRepository) async {
        for await categories in repository.observeCategories(type: .expense) {
            expenseCategories = categories
            hasLoadedCategories = true
        }
    }

    func observeBudgets() async {
        for await budgets in repository.observeBudgets() {
            self.budgets = budgets
        }
    }

    /// Devise d'un nouveau budget : celle du premier compte (écart volontaire avec Android, qui
    /// impose XOF : un budget dans une devise sans compte ne compterait jamais rien).
    func observeAccounts(_ repository: AccountRepository) async {
        for await accounts in repository.observeAccounts() {
            if !hasChosenCurrency, let currency = accounts.first?.currencyCode {
                draft.currencyCode = currency
            }
        }
    }

    func changeCurrency(_ code: String) {
        hasChosenCurrency = true
        draft.currencyCode = code
    }

    // MARK: - Période

    func applyQuickRange(_ range: BudgetQuickRange) {
        draft.applyQuickRange(range, today: today, calendar: calendar)
    }

    var startDate: Date { date(draft.startDay) }
    var endDate: Date { date(draft.endDay) }

    func changeStartDate(_ date: Date) {
        draft.changeStartDay(day(date))
    }

    func changeEndDate(_ date: Date) {
        draft.changeEndDay(day(date))
    }

    private func date(_ day: CalendarDay?) -> Date {
        let millis = (day ?? today).startOfDayMillis(calendar: calendar)
        return Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
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

        let result = BudgetForm.build(
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
        case .success(let budget):
            do {
                try await repository.save(budget)
                return true
            } catch {
                saveFailed = true
                return false
            }
        }
    }

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
