import ArzikinaDomain
import Foundation
import Observation

/// Création ou modification d'un prêt / emprunt — Android `LoanFormViewModel` : à la création,
/// un premier remboursement facultatif (étape 2 d'Android) ; en modification, le type est figé.
@MainActor
@Observable
final class LoanFormViewModel {

    enum Mode {
        case create
        /// [summary] donne le montant déjà remboursé (plancher du nouveau montant).
        case edit(LoanSummary)
    }

    var draft: LoanDraft {
        didSet { error = nil; saveFailed = false }
    }
    private(set) var persons: [Person] = []
    private(set) var accounts: [Account] = []
    private(set) var error: LoanFormError?
    private(set) var isSaving = false
    private(set) var saveFailed = false

    let isEditing: Bool
    /// Montant déjà remboursé (modification).
    let repaid: MinorUnits

    @ObservationIgnored private let existing: Loan?
    @ObservationIgnored private let loans: LoanRepository

    init(mode: Mode, loans: LoanRepository) {
        self.loans = loans
        switch mode {
        case .create:
            existing = nil
            isEditing = false
            repaid = 0
            draft = LoanDraft(now: Self.nowMillis())
        case .edit(let summary):
            existing = summary.loan
            isEditing = true
            repaid = summary.amountRepaid
            draft = LoanDraft(editing: summary.loan)
        }
    }

    var currencyCode: String {
        accounts.first { $0.id == draft.accountId }?.currencyCode ?? SupportedCurrency.defaultCode
    }

    func observePersons() async {
        for await persons in loans.observePersons() { self.persons = persons }
    }

    /// Le premier compte est présélectionné à la création (le plus souvent les espèces).
    func observeAccounts(_ repository: AccountRepository) async {
        for await accounts in repository.observeAccounts() {
            self.accounts = accounts
            if !isEditing, draft.accountId == nil || !accounts.contains(where: { $0.id == draft.accountId }) {
                draft.accountId = accounts.first?.id
            }
        }
    }

    /// Nouvelle personne (nom obligatoire, téléphone facultatif), aussitôt sélectionnée.
    func createPerson(name: String, phone: String) async -> Bool {
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else { return false }
        let trimmedPhone = phone.trimmingCharacters(in: .whitespacesAndNewlines)
        let person = Person(id: EntityIDs.generate(), name: trimmedName, phone: trimmedPhone.isEmpty ? nil : trimmedPhone, createdAt: Self.nowMillis())
        do {
            try await loans.savePerson(person)
            draft.personId = person.id
            return true
        } catch {
            return false
        }
    }

    var startDate: Date {
        get { Self.date(draft.startDate) }
        set { draft.startDate = Self.millis(newValue) }
    }

    var dueDate: Date {
        get { Self.date(draft.dueDate) }
        set { draft.dueDate = Self.millis(newValue) }
    }

    var firstPaymentDate: Date {
        get { Self.date(draft.firstPaymentDate) }
        set { draft.firstPaymentDate = Self.millis(newValue) }
    }

    /// `true` si l'écran peut se fermer.
    func save() async -> Bool {
        guard !isSaving else { return false }
        isSaving = true
        defer { isSaving = false }
        let result = LoanForm.build(draft, existing: existing, repaid: repaid, newId: EntityIDs.generate(), newPaymentId: EntityIDs.generate(), now: Self.nowMillis())
        switch result {
        case .failure(let fieldError):
            error = fieldError
            return false
        case .success(let value):
            do {
                if isEditing {
                    try await loans.update(value.loan)
                } else {
                    try await loans.create(value.loan, firstPayment: value.firstPayment)
                }
                return true
            } catch LoanWriteError.amountBelowRepaid {
                // Un remboursement est arrivé par synchronisation pendant la saisie.
                error = .amountBelowRepaid
                return false
            } catch {
                saveFailed = true
                return false
            }
        }
    }

    private static func date(_ millis: EpochMillis) -> Date { Date(timeIntervalSince1970: TimeInterval(millis) / 1000) }
    private static func millis(_ date: Date) -> EpochMillis { EpochMillis((date.timeIntervalSince1970 * 1000).rounded()) }
    private static func nowMillis() -> EpochMillis { millis(Date()) }
}
