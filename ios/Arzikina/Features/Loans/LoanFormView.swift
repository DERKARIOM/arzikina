import ArzikinaDomain
import SwiftUI

/// Prêt / emprunt (feuille modale) : type, personne (choisie ou créée sur place), compte, montant,
/// dates, description et, à la création, un premier remboursement facultatif. L'enregistrement
/// crée ou met à jour la transaction correspondante (l'argent qui sort pour un prêt, qui entre
/// pour un emprunt). En modification, le type est figé.
struct LoanFormView: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(SessionModel.self) private var session
    @State private var model: LoanFormViewModel
    @State private var isAddingPerson = false
    @FocusState private var focusedField: Field?

    private enum Field { case amount, description, firstPayment }

    private let accounts: AccountRepository

    init(mode: LoanFormViewModel.Mode = .create, loans: LoanRepository, accounts: AccountRepository) {
        _model = State(initialValue: LoanFormViewModel(mode: mode, loans: loans))
        self.accounts = accounts
    }

    var body: some View {
        @Bindable var model = model
        NavigationStack {
            Form {
                Section {
                    if model.isEditing {
                        LabeledContent("loans.form.type") { Text(model.draft.type.titleKey) }
                    } else {
                        Picker("loans.form.type", selection: $model.draft.type) {
                            Text("loans.type.lent").tag(LoanType.lent)
                            Text("loans.type.borrowed").tag(LoanType.borrowed)
                        }
                        .pickerStyle(.segmented)
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets())
                    }
                } footer: {
                    Text(model.isEditing ? "loans.form.type.locked_help" : (model.draft.type == .lent ? "loans.form.type.lent_help" : "loans.form.type.borrowed_help"))
                }

                Section {
                    Picker(model.draft.type == .lent ? "loans.form.person.lent" : "loans.form.person.borrowed", selection: $model.draft.personId) {
                        if model.draft.personId == nil {
                            Text("transaction.form.account.choose").tag(EntityID?.none)
                        }
                        ForEach(model.persons) { person in
                            Text(verbatim: person.name).tag(EntityID?.some(person.id))
                        }
                    }
                    Button {
                        isAddingPerson = true
                    } label: {
                        Label("loans.form.person.add", systemImage: "person.badge.plus")
                    }
                } footer: {
                    if model.error == .personRequired { FormErrorText(key: "loans.form.error.person") }
                }

                Section {
                    Picker("loans.form.account", selection: $model.draft.accountId) {
                        if model.draft.accountId == nil {
                            Text("transaction.form.account.choose").tag(EntityID?.none)
                        }
                        ForEach(model.accounts) { account in
                            Text(verbatim: "\(account.displayName) (\(Money.symbol(of: account.currencyCode)))").tag(EntityID?.some(account.id))
                        }
                    }
                    AmountField(titleKey: "loans.form.amount", text: $model.draft.amountInput, currencyCode: model.currencyCode)
                        .focused($focusedField, equals: .amount)
                } footer: {
                    switch model.error {
                    case .accountRequired: FormErrorText(key: "transaction.form.error.account")
                    case .invalidAmount: FormErrorText(key: "transaction.form.error.amount")
                    case .amountBelowRepaid: FormErrorText(key: "loans.form.error.below_repaid")
                    default: EmptyView()
                    }
                }

                Section {
                    DatePicker("loans.form.start", selection: $model.startDate)
                    DatePicker("loans.form.due", selection: $model.dueDate, in: model.startDate..., displayedComponents: .date)
                } footer: {
                    if model.error == .dueBeforeStart { FormErrorText(key: "loans.form.error.due") }
                }

                Section {
                    TextField("loans.form.description", text: $model.draft.description, axis: .vertical)
                        .lineLimit(1...3)
                        .focused($focusedField, equals: .description)
                }

                if !model.isEditing {
                    Section {
                        AmountField(titleKey: "loans.form.first_payment.amount", text: $model.draft.firstPaymentInput, currencyCode: model.currencyCode)
                            .focused($focusedField, equals: .firstPayment)
                        if !model.draft.firstPaymentInput.trimmingCharacters(in: .whitespaces).isEmpty {
                            DatePicker("loans.form.first_payment.date", selection: $model.firstPaymentDate, in: model.startDate...)
                        }
                    } header: {
                        Text("loans.form.first_payment")
                    } footer: {
                        switch model.error {
                        case .invalidFirstPayment: FormErrorText(key: "transaction.form.error.amount")
                        case .firstPaymentExceedsAmount: FormErrorText(key: "loans.form.error.exceeds_total")
                        case .firstPaymentBeforeStart: FormErrorText(key: "loans.form.error.before_start")
                        default: Text("loans.form.first_payment.help")
                        }
                    }
                }

                if model.saveFailed {
                    Section { FormErrorText(key: "transaction.form.save_failed") }
                }
            }
            .navigationTitle(model.isEditing ? LocalizedStringKey("loans.form.title.edit") : LocalizedStringKey("loans.form.title"))
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
            .sheet(isPresented: $isAddingPerson) {
                NewPersonSheet { name, phone in await model.createPerson(name: name, phone: phone) }
                    .presentationDetents([.medium])
            }
            .task { await model.observePersons() }
            .task { await model.observeAccounts(accounts) }
        }
    }

    private func submit() {
        focusedField = nil
        Task {
            if await model.save() {
                session.sync?.requestSync(.localChange)
                dismiss()
            }
        }
    }
}

/// Nouvelle personne : nom (obligatoire) et téléphone (facultatif).
private struct NewPersonSheet: View {
    let onSave: @MainActor (String, String) async -> Bool

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var phone = ""
    @State private var failed = false
    @FocusState private var isNameFocused: Bool

    var body: some View {
        NavigationStack {
            Form {
                TextField("loans.person.name", text: $name)
                    .textContentType(.name)
                    .textInputAutocapitalization(.words)
                    .focused($isNameFocused)
                TextField("loans.person.phone_optional", text: $phone)
                    .textContentType(.telephoneNumber)
                    .keyboardType(.phonePad)
                if failed {
                    FormErrorText(key: "transaction.form.save_failed")
                }
            }
            .navigationTitle("loans.person.new")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.save") {
                        Task {
                            if await onSave(name, phone) { dismiss() } else { failed = true }
                        }
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .onAppear { isNameFocused = true }
        }
    }
}
