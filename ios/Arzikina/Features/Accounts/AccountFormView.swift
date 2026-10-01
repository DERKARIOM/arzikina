import ArzikinaDomain
import SwiftUI

/// Formulaire de compte (feuille modale) : nom, type, apparence (icône, couleur), devise et solde
/// initial, puis les champs propres au type — objectif d'épargne (montant cible, description) ou
/// carte de crédit (4 derniers chiffres, expiration) — et l'exclusion des statistiques.
///
/// Les sections propres à un type n'apparaissent que pour ce type ; leur saisie est conservée si
/// l'utilisateur change de type puis revient, mais rien d'orphelin n'est enregistré.
struct AccountFormView: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(SessionModel.self) private var session
    @State private var model: AccountFormViewModel
    @FocusState private var focusedField: Field?

    private enum Field { case name, balance, target, description, lastFour, expiry }

    init(mode: AccountFormViewModel.Mode, repository: AccountRepository) {
        _model = State(initialValue: AccountFormViewModel(mode: mode, repository: repository))
    }

    var body: some View {
        NavigationStack {
            Form {
                identitySection
                appearanceSection
                balanceSection
                if model.draft.type == .savingsGoal { savingsGoalSection }
                if model.draft.type == .creditCard { cardSection }
                statisticsSection
                if model.saveFailed {
                    Section {
                        FormErrorText(key: "account.form.save_failed")
                    }
                }
            }
            .navigationTitle(model.isEditing ? LocalizedStringKey("account.form.title.edit") : LocalizedStringKey("account.form.title.add"))
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
            .confirmationDialog(
                "account.form.savings_remove.title",
                isPresented: $model.isConfirmingSavingsGoalRemoval,
                titleVisibility: .visible
            ) {
                Button("account.form.savings_remove.confirm", role: .destructive) {
                    submit(confirmedSavingsGoalRemoval: true)
                }
            } message: {
                Text("account.form.savings_remove.message \(model.originalName)")
            }
            .interactiveDismissDisabled(model.isSaving)
        }
    }

    // MARK: - Sections

    private var identitySection: some View {
        Section {
            TextField("account.form.name", text: $model.draft.name)
                .focused($focusedField, equals: .name)
                .textInputAutocapitalization(.words)
                .submitLabel(.next)
                .onChange(of: model.draft.name) { model.clearError() }
            Picker("account.form.type", selection: Binding(get: { model.draft.type }, set: { model.changeType($0) })) {
                ForEach(AccountType.allCases, id: \.self) { type in
                    Text(verbatim: type.displayName).tag(type)
                }
            }
        } footer: {
            if model.error == .nameRequired { FormErrorText(key: "account.form.error.name_required") }
        }
    }

    private var appearanceSection: some View {
        Section("account.form.appearance") {
            IconGrid(
                selection: $model.draft.icon,
                icons: AccountIcon.allCases,
                colorArgb: model.draft.colorArgb,
                systemImage: \.systemImage,
                accessibilityName: \.displayName
            )
            ColorGrid(selection: $model.draft.colorArgb, choices: model.colorChoices)
        }
    }

    private var balanceSection: some View {
        Section {
            Picker("account.form.currency", selection: $model.draft.currencyCode) {
                ForEach(SupportedCurrency.allCases, id: \.self) { currency in
                    Text(verbatim: currency.pickerLabel).tag(currency.code)
                }
                // Devise d'un compte créé ailleurs, hors de la liste proposée : conservée.
                if SupportedCurrency(rawValue: model.draft.currencyCode) == nil {
                    Text(verbatim: model.draft.currencyCode).tag(model.draft.currencyCode)
                }
            }
            AmountField(titleKey: "account.form.initial_balance", text: $model.draft.initialBalanceInput, currencyCode: model.draft.currencyCode)
                .focused($focusedField, equals: .balance)
                .onChange(of: model.draft.initialBalanceInput) { model.clearError() }
        } footer: {
            if model.error == .invalidInitialBalance { FormErrorText(key: "account.form.error.invalid_amount") }
        }
    }

    private var savingsGoalSection: some View {
        Section {
            AmountField(titleKey: "account.form.savings_target", text: $model.draft.savingsTargetInput, currencyCode: model.draft.currencyCode)
                .focused($focusedField, equals: .target)
                .onChange(of: model.draft.savingsTargetInput) { model.clearError() }
            TextField("account.form.savings_description", text: $model.draft.savingsDescriptionInput, axis: .vertical)
                .focused($focusedField, equals: .description)
                .lineLimit(1...3)
        } header: {
            Text("account.form.savings_section")
        } footer: {
            if model.error == .invalidSavingsTarget {
                FormErrorText(key: "account.form.error.savings_target")
            } else {
                Text("account.form.savings_help")
            }
        }
    }

    private var cardSection: some View {
        Section {
            TextField("account.form.card_last_four", text: Binding(get: { model.draft.cardLastFourInput }, set: { model.changeLastFour($0) }))
                .keyboardType(.numberPad)
                .focused($focusedField, equals: .lastFour)
            TextField("account.form.card_expiry", text: Binding(get: { model.draft.cardExpiryInput }, set: { model.changeExpiry($0) }))
                .keyboardType(.numberPad)
                .focused($focusedField, equals: .expiry)
        } header: {
            Text("account.form.card_section")
        } footer: {
            switch model.error {
            case .invalidCardLastFour: FormErrorText(key: "account.form.error.card_last_four")
            case .invalidCardExpiry: FormErrorText(key: "account.form.error.card_expiry")
            default: Text("account.form.card_help")
            }
        }
    }

    private var statisticsSection: some View {
        Section {
            Toggle("account.form.exclude_statistics", isOn: $model.draft.isExcludedFromStatistics)
        } footer: {
            Text("account.form.exclude_statistics_help")
        }
    }

    // MARK: - Enregistrement

    private func submit(confirmedSavingsGoalRemoval: Bool = false) {
        focusedField = nil
        Task {
            if await model.save(confirmedSavingsGoalRemoval: confirmedSavingsGoalRemoval) {
                session.sync?.requestSync(.localChange)
                dismiss()
            }
        }
    }
}

// MARK: - Composants du formulaire

/// Message d'erreur sous un champ.
struct FormErrorText: View {
    let key: LocalizedStringKey

    var body: some View {
        Label(key, systemImage: "exclamationmark.circle.fill")
            .foregroundStyle(Brand.expense)
    }
}

/// Champ de montant : clavier décimal et symbole de la devise.
struct AmountField: View {
    let titleKey: LocalizedStringKey
    @Binding var text: String
    let currencyCode: String

    var body: some View {
        LabeledContent(titleKey) {
            HStack(spacing: 6) {
                TextField(titleKey, text: $text)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .labelsHidden()
                Text(verbatim: Money.symbol(of: currencyCode))
                    .foregroundStyle(.secondary)
            }
        }
    }
}
