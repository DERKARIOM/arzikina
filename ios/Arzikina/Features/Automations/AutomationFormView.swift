import ArzikinaDomain
import SwiftUI

/// Ouverture du formulaire d'automatisation (nouvelle ou existante).
enum AutomationFormRoute: Identifiable {
    case create
    case edit(RecurringTransaction)

    var id: String {
        switch self {
        case .create: return "create"
        case .edit(let rule): return "edit-\(rule.id)"
        }
    }

    var mode: AutomationFormViewModel.Mode {
        switch self {
        case .create: return .create
        case .edit(let rule): return .edit(rule)
        }
    }
}

/// Formulaire d'automatisation (feuille modale) — Android `RecurringTransactionFormFragment` :
/// la transaction (type, montant, compte, catégorie), sa planification (fréquence, première
/// échéance, heure, fin éventuelle), puis description et moyen de paiement.
///
/// La pause / reprise reste dans la liste (interrupteur) : ce formulaire ne réactive jamais une
/// règle.
struct AutomationFormView: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(SessionModel.self) private var session
    @State private var model: AutomationFormViewModel

    private let accounts: AccountRepository
    private let categories: CategoryRepository

    init(mode: AutomationFormViewModel.Mode, recurring: RecurringRepository, accounts: AccountRepository, categories: CategoryRepository) {
        _model = State(initialValue: AutomationFormViewModel(mode: mode, repository: recurring))
        self.accounts = accounts
        self.categories = categories
    }

    var body: some View {
        @Bindable var model = model
        NavigationStack {
            Form {
                AutomationDetailsSections(details: $model.draft.details, choices: model.choices, error: model.error) {
                    scheduleSection
                }
                if model.saveFailed {
                    Section { FormErrorText(key: "transaction.form.save_failed") }
                }
                if model.isEditing {
                    deleteSection
                }
            }
            .navigationTitle(model.isEditing ? LocalizedStringKey("automation.form.title.edit") : LocalizedStringKey("automation.form.title.add"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.save") { submit() }
                        .disabled(model.isWorking)
                }
            }
            .interactiveDismissDisabled(model.isWorking)
            .confirmationDialog(
                "automation.delete.title",
                isPresented: Binding(get: { model.deletionImpact != nil }, set: { if !$0 { model.cancelDeletion() } }),
                titleVisibility: .visible,
                presenting: model.deletionImpact
            ) { impact in
                deletionButtons(impact)
            } message: { impact in
                deletionMessage(impact)
            }
            .task { await model.observeAccounts(accounts) }
            .task { await model.observeCategories(categories) }
        }
    }

    // MARK: - Planification

    private var scheduleSection: some View {
        Section {
            Picker("automation.form.frequency", selection: $model.draft.frequency) {
                ForEach(RecurringFrequency.allCases, id: \.self) { frequency in
                    Text(verbatim: frequency.displayName).tag(frequency)
                }
            }
            DatePicker(
                model.draft.frequency == .once ? LocalizedStringKey("automation.form.date") : LocalizedStringKey("automation.form.start_date"),
                selection: $model.startDate,
                displayedComponents: .date
            )
            DatePicker("automation.form.time", selection: $model.triggerTime, displayedComponents: .hourAndMinute)
            if model.draft.showsEndDate {
                Toggle("automation.form.has_end_date", isOn: $model.draft.hasEndDate)
                if model.draft.hasEndDate {
                    DatePicker("automation.form.end_date", selection: $model.endDate, in: model.startDate..., displayedComponents: .date)
                }
            }
        } header: {
            Text("automation.form.schedule")
        } footer: {
            if model.error == .endBeforeStart {
                FormErrorText(key: "automation.form.error.end_date")
            } else if model.isEditing {
                Text("automation.form.future_only")
            } else {
                Text("automation.form.schedule.footer")
            }
        }
    }

    // MARK: - Suppression

    private var deleteSection: some View {
        Section {
            Button(role: .destructive) {
                Task { await model.prepareDeletion() }
            } label: {
                Text("automation.delete")
                    .frame(maxWidth: .infinity)
            }
            .disabled(model.isWorking)
        } footer: {
            if model.deleteFailed { FormErrorText(key: "automation.delete.failed") }
        }
    }

    @ViewBuilder
    private func deletionButtons(_ impact: AutomationDeletionImpact) -> some View {
        Button("automation.delete", role: .destructive) { delete(withTransactions: false) }
        if impact.createdTransactionCount > 0 {
            Button("automation.delete.with_transactions \(impact.createdTransactionCount)", role: .destructive) {
                delete(withTransactions: true)
            }
        }
    }

    private func deletionMessage(_ impact: AutomationDeletionImpact) -> Text {
        impact.createdTransactionCount > 0
            ? Text("automation.delete.message.with_transactions \(impact.createdTransactionCount)")
            : Text("automation.delete.message")
    }

    // MARK: - Actions

    private func submit() {
        Task {
            if await model.save() {
                session.sync?.requestSync(.localChange)
                dismiss()
                // Moment naturel pour proposer les rappels (rien si déjà accepté ou refusé).
                await session.reminders?.requestAuthorizationIfNeeded()
            }
        }
    }

    private func delete(withTransactions: Bool) {
        Task {
            if await model.delete(deleteCreatedTransactions: withTransactions) {
                session.sync?.requestSync(.localChange)
                dismiss()
            }
        }
    }
}
