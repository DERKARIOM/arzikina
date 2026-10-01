import ArzikinaDomain
import Foundation
import Observation
import SwiftUI

/// Enregistrement d'un remboursement — Android `LoanPaymentFormViewModel`.
@MainActor
@Observable
final class LoanPaymentFormViewModel {

    var draft: LoanPaymentDraft {
        didSet { error = nil; saveFailed = false }
    }
    private(set) var accounts: [Account] = []
    private(set) var error: LoanPaymentFormError?
    private(set) var isSaving = false
    private(set) var saveFailed = false

    let summary: LoanSummary
    @ObservationIgnored private let loans: LoanRepository

    init(summary: LoanSummary, loans: LoanRepository) {
        self.summary = summary
        self.loans = loans
        draft = LoanPaymentDraft(loan: summary.loan, now: EpochMillis((Date().timeIntervalSince1970 * 1000).rounded()))
    }

    var accountCurrency: String {
        accounts.first { $0.id == draft.accountId }?.currencyCode ?? summary.currencyCode
    }

    /// Compte dans une autre devise que le prêt : montant enregistré tel quel (avertissement
    /// d'Android).
    var hasCurrencyMismatch: Bool { accountCurrency != summary.currencyCode }

    var date: Date {
        get { Date(timeIntervalSince1970: TimeInterval(draft.date) / 1000) }
        set { draft.date = EpochMillis((newValue.timeIntervalSince1970 * 1000).rounded()) }
    }

    var startDate: Date { Date(timeIntervalSince1970: TimeInterval(summary.loan.startDate) / 1000) }

    func observeAccounts(_ repository: AccountRepository) async {
        for await accounts in repository.observeAccounts() { self.accounts = accounts }
    }

    func save() async -> Bool {
        guard !isSaving else { return false }
        isSaving = true
        defer { isSaving = false }
        switch LoanPaymentForm.build(draft, summary: summary, newId: EntityIDs.generate(), now: EpochMillis((Date().timeIntervalSince1970 * 1000).rounded())) {
        case .failure(let fieldError):
            error = fieldError
            return false
        case .success(let payment):
            do {
                try await loans.recordPayment(payment)
                return true
            } catch LoanWriteError.amountExceedsRemaining {
                error = .exceedsRemaining
                return false
            } catch {
                saveFailed = true
                return false
            }
        }
    }
}

/// Feuille « Enregistrer un remboursement » : reste à payer, compte, montant, date et note.
struct LoanPaymentFormView: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(SessionModel.self) private var session
    @State private var model: LoanPaymentFormViewModel
    @FocusState private var isAmountFocused: Bool

    private let accounts: AccountRepository

    init(summary: LoanSummary, loans: LoanRepository, accounts: AccountRepository) {
        _model = State(initialValue: LoanPaymentFormViewModel(summary: summary, loans: loans))
        self.accounts = accounts
    }

    var body: some View {
        @Bindable var model = model
        NavigationStack {
            Form {
                Section {
                    LabeledContent("loans.detail.remaining_label") {
                        Text(verbatim: Money.format(CurrencyAmount(currencyCode: model.summary.currencyCode, amountMinor: model.summary.remaining)))
                            .monospacedDigit()
                    }
                    Button("loans.payment.fill_remaining") {
                        model.draft.amountInput = Money.formatForInput(model.summary.remaining)
                    }
                }
                Section {
                    Picker("loans.form.account", selection: $model.draft.accountId) {
                        ForEach(model.accounts) { account in
                            Text(verbatim: "\(account.displayName) (\(Money.symbol(of: account.currencyCode)))").tag(EntityID?.some(account.id))
                        }
                    }
                    AmountField(titleKey: "loans.payment.amount", text: $model.draft.amountInput, currencyCode: model.accountCurrency)
                        .focused($isAmountFocused)
                    DatePicker("loans.payment.date", selection: $model.date, in: model.startDate...)
                    TextField("loans.payment.note", text: $model.draft.note, axis: .vertical)
                        .lineLimit(1...3)
                } footer: {
                    switch model.error {
                    case .accountRequired: FormErrorText(key: "transaction.form.error.account")
                    case .invalidAmount: FormErrorText(key: "transaction.form.error.amount")
                    case .exceedsRemaining: FormErrorText(key: "loans.payment.error.exceeds_remaining")
                    case .beforeStart: FormErrorText(key: "loans.form.error.before_start")
                    case nil:
                        if model.hasCurrencyMismatch { Text("loans.payment.currency_mismatch") }
                    }
                }
                if model.saveFailed {
                    Section { FormErrorText(key: "transaction.form.save_failed") }
                }
            }
            .navigationTitle("loans.payment.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.save") { submit() }
                        .disabled(model.isSaving)
                }
            }
            .interactiveDismissDisabled(model.isSaving)
            .task { await model.observeAccounts(accounts) }
            .onAppear { isAmountFocused = true }
        }
    }

    private func submit() {
        isAmountFocused = false
        Task {
            if await model.save() {
                session.sync?.requestSync(.localChange)
                dismiss()
            }
        }
    }
}
