import ArzikinaDomain
import SwiftUI

/// Ouverture du formulaire de modèle (même présentation depuis la liste et le formulaire de
/// transaction).
enum TemplateFormRoute: Identifiable {
    case create
    case edit(TransactionTemplate)
    case fromTransaction(ArzikinaDomain.Transaction, categoryName: String?)

    var id: String {
        switch self {
        case .create: return "create"
        case .edit(let template): return "edit-\(template.id)"
        case .fromTransaction(let transaction, _): return "from-\(transaction.id)"
        }
    }

    var mode: TemplateFormViewModel.Mode {
        switch self {
        case .create: return .create(accountId: nil)
        case .edit(let template): return .edit(template)
        case .fromTransaction(let transaction, let categoryName): return .fromTransaction(transaction, categoryName: categoryName)
        }
    }
}

extension View {
    func templateFormSheet(_ route: Binding<TemplateFormRoute?>, session: SessionModel) -> some View {
        sheet(item: route) { route in
            if let space = session.dataSpace {
                TemplateFormView(mode: route.mode, templates: space.templates, accounts: space.accounts, categories: space.categories)
                    .environment(session)
            }
        }
    }
}

/// Formulaire de modèle (feuille modale) — Android `MarketplaceFormFragment` : nom, puis la
/// transaction type (type, montant, compte, catégorie), une heure par défaut facultative et la
/// description. Le favori se change depuis la liste.
struct TemplateFormView: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(SessionModel.self) private var session
    @State private var model: TemplateFormViewModel
    @State private var isConfirmingDelete = false

    private let accounts: AccountRepository
    private let categories: CategoryRepository

    init(mode: TemplateFormViewModel.Mode, templates: TransactionTemplateRepository, accounts: AccountRepository, categories: CategoryRepository) {
        _model = State(initialValue: TemplateFormViewModel(mode: mode, repository: templates))
        self.accounts = accounts
        self.categories = categories
    }

    var body: some View {
        @Bindable var model = model
        NavigationStack {
            Form {
                if model.isAlreadyLinked {
                    Section {
                        Label("template.form.already_linked", systemImage: "info.circle")
                            .foregroundStyle(.secondary)
                    }
                }
                Section {
                    TextField("template.form.name", text: $model.draft.name)
                        .textInputAutocapitalization(.sentences)
                } header: {
                    Text("template.form.name")
                } footer: {
                    if model.error == .nameRequired {
                        FormErrorText(key: "template.form.error.name")
                    } else if model.isFromTransaction {
                        Text("template.form.from_transaction")
                    }
                }
                AutomationDetailsSections(details: $model.draft.details, choices: model.choices, error: model.detailsError, showsPaymentMethod: false) {
                    defaultTimeSection
                }
                if model.saveFailed {
                    Section { FormErrorText(key: "transaction.form.save_failed") }
                }
                if model.isEditing {
                    Section {
                        Button(role: .destructive) {
                            isConfirmingDelete = true
                        } label: {
                            Text("templates.delete").frame(maxWidth: .infinity)
                        }
                        .disabled(model.isWorking)
                    } footer: {
                        if model.deleteFailed { FormErrorText(key: "templates.action_failed") }
                    }
                }
            }
            .navigationTitle(model.isEditing ? LocalizedStringKey("template.form.title.edit") : LocalizedStringKey("template.form.title.add"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.save") { submit() }
                        .disabled(model.isWorking || model.isAlreadyLinked)
                }
            }
            .interactiveDismissDisabled(model.isWorking)
            .confirmationDialog("templates.delete.title", isPresented: $isConfirmingDelete, titleVisibility: .visible) {
                Button("templates.delete", role: .destructive) { delete() }
            } message: {
                Text("templates.delete.message \(model.draft.name)")
            }
            .task { await model.observeAccounts(accounts) }
            .task { await model.observeCategories(categories) }
            .task { await model.checkExistingLink() }
        }
    }

    /// Heure par défaut facultative : appliquée au jour où le modèle est utilisé.
    private var defaultTimeSection: some View {
        Section {
            Toggle("template.form.default_time.toggle", isOn: $model.draft.hasDefaultTime)
            if model.draft.hasDefaultTime {
                DatePicker("template.form.default_time", selection: $model.defaultTime, displayedComponents: .hourAndMinute)
            }
        } footer: {
            Text("template.form.default_time.footer")
        }
    }

    private func submit() {
        Task {
            if await model.save() {
                session.sync?.requestSync(.localChange)
                dismiss()
            }
        }
    }

    private func delete() {
        Task {
            if await model.delete() {
                session.sync?.requestSync(.localChange)
                dismiss()
            }
        }
    }
}
