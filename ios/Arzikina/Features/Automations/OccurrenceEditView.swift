import ArzikinaDomain
import SwiftUI

/// « Modifier puis valider » une échéance (feuille modale) — Android
/// `RecurringOccurrenceEditDialogFragment` : mêmes champs que la règle, plus la date et l'heure
/// de la transaction. La règle d'origine n'est pas modifiée.
struct OccurrenceEditView: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(SessionModel.self) private var session
    @State private var model: OccurrenceEditViewModel

    private let accounts: AccountRepository
    private let categories: CategoryRepository

    init(occurrence: RecurringTransactionOccurrence, rule: RecurringTransaction, recurring: RecurringRepository, accounts: AccountRepository, categories: CategoryRepository) {
        _model = State(initialValue: OccurrenceEditViewModel(occurrence: occurrence, rule: rule, repository: recurring))
        self.accounts = accounts
        self.categories = categories
    }

    var body: some View {
        @Bindable var model = model
        NavigationStack {
            Form {
                AutomationDetailsSections(details: $model.draft.details, choices: model.choices, error: model.error) {
                    Section {
                        DatePicker("transaction.form.date_time", selection: $model.date, displayedComponents: [.date, .hourAndMinute])
                    } footer: {
                        Text("occurrence.edit.footer")
                    }
                }
                switch model.failure {
                case .alreadyProcessed:
                    Section { FormErrorText(key: "automations.action_failed") }
                case .saveFailed:
                    Section { FormErrorText(key: "transaction.form.save_failed") }
                case nil:
                    EmptyView()
                }
            }
            .navigationTitle("occurrence.edit.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("automations.accept") { confirm() }
                        .disabled(model.isWorking || model.failure == .alreadyProcessed)
                }
            }
            .interactiveDismissDisabled(model.isWorking)
            .task { await model.observeAccounts(accounts) }
            .task { await model.observeCategories(categories) }
        }
    }

    private func confirm() {
        Task {
            if await model.confirm() {
                session.sync?.requestSync(.localChange)
                dismiss()
            }
        }
    }
}
