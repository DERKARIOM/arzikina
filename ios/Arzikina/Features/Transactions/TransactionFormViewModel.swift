import ArzikinaDomain
import Foundation
import Observation

/// Formulaire de création ou de modification d'une transaction (revenu, dépense, transfert) et
/// de ses frais.
///
/// Les règles (validation, transitions, description automatique d'un transfert, frais) sont dans
/// le domaine (`TransactionDraft`, `TransactionForm`) ; ce ViewModel charge les listes (comptes,
/// catégories), orchestre la saisie et l'enregistrement LOCAL — l'envoi au serveur suit à la
/// synchronisation.
@MainActor
@Observable
final class TransactionFormViewModel {

    enum Mode {
        /// [presetAccountId] : compte présélectionné (ouverture depuis le détail d'un compte).
        case create(presetAccountId: EntityID?)
        case edit(Transaction)
    }

    private(set) var draft: TransactionDraft
    private(set) var accounts: [Account] = []
    private(set) var allCategories: [ArzikinaDomain.Category] = []
    private(set) var error: TransactionFormError?
    private(set) var isSaving = false
    private(set) var saveFailed = false
    /// La transaction fait partie d'un prêt : elle se gère depuis le prêt (comme Android).
    private(set) var isLinkedToLoan = false
    private(set) var isDeleting = false
    private(set) var deleteFailed = false

    let isEditing: Bool

    @ObservationIgnored private let existing: Transaction?
    @ObservationIgnored private let transactions: TransactionRepository
    @ObservationIgnored private var hasLoadedFee = false

    init(mode: Mode, transactions: TransactionRepository) {
        self.transactions = transactions
        switch mode {
        case .create(let presetAccountId):
            existing = nil
            isEditing = false
            draft = TransactionDraft(now: Self.nowMillis(), presetAccountId: presetAccountId)
        case .edit(let transaction):
            existing = transaction
            isEditing = true
            draft = TransactionDraft(editing: transaction, fee: nil)
        }
    }

    // MARK: - Listes

    /// Catégories du type choisi, sans les catégories système des prêts et des frais.
    var categories: [ArzikinaDomain.Category] {
        allCategories.filter { $0.type == draft.type && TransactionForm.isSelectable($0) }
    }

    func account(_ id: EntityID?) -> Account? {
        id.flatMap { id in accounts.first { $0.id == id } }
    }

    /// Devise du montant principal (celle du compte source).
    var currencyCode: String {
        account(draft.accountId)?.currencyCode ?? SupportedCurrency.defaultCode
    }

    var feeCurrencyCode: String {
        account(draft.feeAccountId)?.currencyCode ?? currencyCode
    }

    func observeAccounts(_ repository: AccountRepository) async {
        for await accounts in repository.observeAccounts() {
            self.accounts = accounts
        }
    }

    func observeCategories(_ repository: CategoryRepository) async {
        for await categories in repository.observeCategories(type: nil) {
            allCategories = categories
        }
    }

    /// Modification : frais liés et lien éventuel avec un prêt, chargés une fois.
    func loadExistingDetails() async {
        guard let existing, !hasLoadedFee else { return }
        hasLoadedFee = true
        isLinkedToLoan = (try? await transactions.isLinkedToLoan(id: existing.id)) ?? false
        if let feeId = existing.feeTransactionId, let fee = try? await transactions.transaction(id: feeId) {
            draft = TransactionDraft(editing: existing, fee: fee)
        }
    }

    // MARK: - Saisie

    private var accountName: (EntityID) -> String? {
        let accounts = self.accounts
        return { id in accounts.first { $0.id == id }?.displayName }
    }

    func changeType(_ type: TransactionType) { mutate { $0.changeType(type, accountName: accountName) } }
    func changeAccount(_ id: EntityID?) { mutate { $0.changeAccount(id, accountName: accountName) } }
    func changeTransferAccount(_ id: EntityID?) { mutate { $0.changeTransferAccount(id, accountName: accountName) } }
    func changeFeeAccount(_ id: EntityID?) { mutate { $0.changeFeeAccount(id) } }
    func changeDescription(_ value: String) { mutate { $0.changeDescription(value, accountName: accountName) } }
    func changeCategory(_ id: EntityID?) { mutate { $0.categoryId = id } }
    func changeAmount(_ value: String) { mutate { $0.amountInput = value } }
    func changeDate(_ date: Date) { mutate { $0.date = EpochMillis((date.timeIntervalSince1970 * 1000).rounded()) } }
    func changePaymentMethod(_ method: PaymentMethod?) { mutate { $0.paymentMethod = method } }
    func changeHasFee(_ value: Bool) { mutate { $0.hasFee = value } }
    func changeFeeAmount(_ value: String) { mutate { $0.feeAmountInput = value } }
    func changeFeeType(_ type: FeeType) { mutate { $0.feeType = type } }
    func changeFeeDescription(_ value: String) { mutate { $0.feeDescription = value } }

    var date: Date {
        Date(timeIntervalSince1970: TimeInterval(draft.date) / 1000)
    }

    private func mutate(_ change: (inout TransactionDraft) -> Void) {
        change(&draft)
        error = nil
        saveFailed = false
        deleteFailed = false
    }

    // MARK: - Enregistrement

    /// Enregistre ; `true` si l'écran peut se fermer.
    func save() async -> Bool {
        guard !isSaving, !isLinkedToLoan else { return false }
        isSaving = true
        defer { isSaving = false }

        switch TransactionForm.build(draft, existing: existing, newId: EntityIDs.generate(), now: Self.nowMillis()) {
        case .failure(let fieldError):
            error = fieldError
            return false
        case .success(let result):
            do {
                try await transactions.save(result.transaction, fee: result.fee)
                return true
            } catch {
                saveFailed = true
                return false
            }
        }
    }

    // MARK: - Suppression

    /// Seule une transaction existante, hors prêt, peut être supprimée.
    var canDelete: Bool { isEditing && !isLinkedToLoan }

    /// Supprime la transaction et ses frais ; `true` si l'écran peut se fermer.
    func delete() async -> Bool {
        guard let existing, canDelete, !isDeleting, !isSaving else { return false }
        isDeleting = true
        defer { isDeleting = false }
        do {
            // Revérifié : le bouton peut être touché avant la fin de `loadExistingDetails`.
            if try await transactions.isLinkedToLoan(id: existing.id) {
                isLinkedToLoan = true
                return false
            }
            try await transactions.delete(id: existing.id)
            return true
        } catch {
            deleteFailed = true
            return false
        }
    }

    private static func nowMillis() -> EpochMillis {
        EpochMillis((Date().timeIntervalSince1970 * 1000).rounded())
    }
}
