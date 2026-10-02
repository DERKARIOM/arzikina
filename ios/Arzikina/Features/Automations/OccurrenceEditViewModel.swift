import ArzikinaDomain
import Foundation
import Observation

/// « Modifier puis valider » une échéance en attente — Android `RecurringOccurrenceEditViewModel`.
///
/// Pré-rempli avec la règle (jour de l'échéance à l'heure de la règle). Seule la transaction
/// créée pour CETTE échéance reprend les modifications ; la règle ne change jamais par ce chemin.
@MainActor
@Observable
final class OccurrenceEditViewModel {

    var draft: OccurrenceEditDraft {
        didSet {
            error = nil
            failure = nil
        }
    }
    let choices = AutomationChoices()
    private(set) var error: AutomationFormError?
    private(set) var failure: Failure?
    private(set) var isWorking = false

    enum Failure: Equatable {
        /// Traitée entre-temps (sur un autre appareil, par exemple).
        case alreadyProcessed
        case saveFailed
    }

    @ObservationIgnored private let occurrenceId: EntityID
    @ObservationIgnored private let repository: RecurringRepository

    init(occurrence: RecurringTransactionOccurrence, rule: RecurringTransaction, repository: RecurringRepository, calendar: Calendar = ArzikinaCalendar.current) {
        occurrenceId = occurrence.id
        self.repository = repository
        draft = OccurrenceEditDraft(occurrence: occurrence, rule: rule, calendar: calendar)
    }

    func observeAccounts(_ repository: AccountRepository) async {
        await choices.observeAccounts(repository)
    }

    func observeCategories(_ repository: CategoryRepository) async {
        await choices.observeCategories(repository)
    }

    var date: Date {
        get { Date(timeIntervalSince1970: TimeInterval(draft.date) / 1000) }
        set { draft.date = EpochMillis((newValue.timeIntervalSince1970 * 1000).rounded()) }
    }

    /// Crée la transaction et marque l'échéance « modifiée ». `true` si l'écran peut se fermer.
    func confirm() async -> Bool {
        guard !isWorking else { return false }
        isWorking = true
        defer { isWorking = false }

        let now = EpochMillis((Date().timeIntervalSince1970 * 1000).rounded())
        switch draft.build(id: EntityIDs.generate(), now: now) {
        case .failure(let fieldError):
            error = fieldError
            return false
        case .success(let transaction):
            do {
                try await repository.acceptWithChanges(occurrenceId: occurrenceId, transaction: transaction)
                return true
            } catch RecurringWriteError.occurrenceNotPending {
                failure = .alreadyProcessed
                return false
            } catch {
                failure = .saveFailed
                return false
            }
        }
    }
}
