import ArzikinaDomain
import Foundation
import Observation

/// Formulaire de création ou de modification d'un compte (y compris objectif d'épargne et carte).
///
/// Les règles (validation, champs effacés quand le type change, nom de référence des comptes par
/// défaut) sont dans le domaine (`AccountForm`) ; ce ViewModel orchestre la saisie et
/// l'enregistrement LOCAL — l'envoi au serveur suit à la synchronisation.
@MainActor
@Observable
final class AccountFormViewModel {

    enum Mode {
        /// [initialType] : présélection selon le groupe affiché (Comptes, Cartes, Épargne).
        case create(initialType: AccountType)
        case edit(Account)
    }

    var draft: AccountDraft
    private(set) var error: AccountFormError?
    private(set) var isSaving = false
    private(set) var saveFailed = false
    /// Un objectif d'épargne va redevenir un compte classique : confirmation demandée.
    var isConfirmingSavingsGoalRemoval = false
    /// Suppression demandée : ce qu'elle emporterait (affiché dans la confirmation).
    var pendingDeletion: AccountDeletionImpact?
    private(set) var isDeleting = false
    private(set) var deleteFailed = false

    let isEditing: Bool
    /// Nom du compte édité, pour le message de confirmation.
    let originalName: String

    @ObservationIgnored private let existing: Account?
    @ObservationIgnored private let repository: AccountRepository
    @ObservationIgnored private let calendar: Calendar

    init(mode: Mode, repository: AccountRepository, calendar: Calendar = ArzikinaCalendar.current) {
        self.repository = repository
        self.calendar = calendar
        switch mode {
        case .create(let type):
            existing = nil
            isEditing = false
            originalName = ""
            draft = AccountDraft(icon: AccountForm.defaultIcon(for: type, current: .cash, isEditing: false), type: type)
        case .edit(let account):
            existing = account
            isEditing = true
            originalName = account.displayName
            draft = AccountDraft(editing: account, displayName: account.displayName)
        }
    }

    /// Couleurs proposées : la palette, plus la couleur actuelle si elle vient d'ailleurs (compte
    /// créé avant la palette actuelle, par exemple).
    var colorChoices: [Int64] {
        ColorPalette.choices(including: draft.colorArgb)
    }

    func changeType(_ type: AccountType) {
        draft.icon = AccountForm.defaultIcon(for: type, current: draft.icon, isEditing: isEditing)
        draft.type = type
        clearError()
    }

    func changeExpiry(_ input: String) {
        draft.cardExpiryInput = AccountForm.formatExpiryInput(input)
        clearError()
    }

    func changeLastFour(_ input: String) {
        draft.cardLastFourInput = String(input.filter { $0.isASCII && $0.isNumber }.prefix(4))
        clearError()
    }

    func clearError() {
        error = nil
        saveFailed = false
    }

    /// Enregistre ; `true` si l'écran peut se fermer.
    func save(confirmedSavingsGoalRemoval: Bool = false) async -> Bool {
        guard !isSaving else { return false }
        isSaving = true
        defer { isSaving = false }

        let today = calendar.dateComponents([.year, .month], from: Date())
        let displayOrder: Int64
        do {
            displayOrder = isEditing ? 0 : try await repository.nextDisplayOrder()
        } catch {
            saveFailed = true
            return false
        }
        let outcome = AccountForm.validate(
            draft,
            existing: existing,
            newId: EntityIDs.generate(),
            newDisplayOrder: displayOrder,
            currentYear: today.year ?? 2000,
            currentMonth: today.month ?? 1,
            confirmedSavingsGoalRemoval: confirmedSavingsGoalRemoval,
            canonicalName: DomainDisplay.canonicalAccountName
        )
        switch outcome {
        case .invalid(let fieldError):
            error = fieldError
            return false
        case .needsSavingsGoalRemovalConfirmation:
            isConfirmingSavingsGoalRemoval = true
            return false
        case .valid(var account):
            if !isEditing {
                account.createdAt = EpochMillis(Date().timeIntervalSince1970 * 1000)
            }
            do {
                try await repository.save(account)
                return true
            } catch {
                saveFailed = true
                return false
            }
        }
    }

    // MARK: - Suppression

    /// Calcule ce que la suppression emporterait, puis demande confirmation.
    func requestDeletion() async {
        guard let existing else { return }
        deleteFailed = false
        do {
            pendingDeletion = try await repository.deletionImpact(id: existing.id)
        } catch {
            deleteFailed = true
        }
    }

    /// Supprime le compte et tout ce qui en dépend ; `true` si l'écran peut se fermer.
    func delete() async -> Bool {
        guard let existing, !isDeleting else { return false }
        isDeleting = true
        defer { isDeleting = false }
        do {
            try await repository.delete(id: existing.id)
            return true
        } catch {
            deleteFailed = true
            return false
        }
    }
}
