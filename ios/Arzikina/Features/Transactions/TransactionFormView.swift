import ArzikinaDomain
import SwiftUI

/// Formulaire de transaction (feuille modale) : type, montant, compte(s), catégorie, date et
/// heure, description, moyen de paiement, puis les frais éventuels — mêmes champs et mêmes règles
/// qu'Android (`TransactionFormFragment`).
///
/// Une transaction qui fait partie d'un prêt s'affiche en lecture seule : elle se gère depuis le
/// prêt, pour que le prêt et ses transactions restent cohérents.
struct TransactionFormView: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(SessionModel.self) private var session
    @State private var model: TransactionFormViewModel
    @FocusState private var focusedField: Field?

    private enum Field { case amount, description, feeAmount, feeDescription }

    init(mode: TransactionFormViewModel.Mode, transactions: TransactionRepository) {
        _model = State(initialValue: TransactionFormViewModel(mode: mode, transactions: transactions))
    }

    var body: some View {
        NavigationStack {
            Form {
                if model.isLinkedToLoan {
                    Section {
                        Label("transaction.form.loan_linked", systemImage: "person.2.fill")
                            .foregroundStyle(.secondary)
                    }
                }
                Group {
                    typeSection
                    amountSection
                    accountsSection
                    if model.draft.type != .transfer { categorySection }
                    detailsSection
                    feeSection
                }
                .disabled(model.isLinkedToLoan)
                if model.saveFailed {
                    Section { FormErrorText(key: "transaction.form.save_failed") }
                }
            }
            .navigationTitle(model.isEditing ? LocalizedStringKey("transaction.form.title.edit") : LocalizedStringKey("transaction.form.title.add"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("common.cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("common.save") { submit() }
                        .disabled(model.isSaving || model.isLinkedToLoan)
                }
            }
            .interactiveDismissDisabled(model.isSaving)
            .task(id: session.dataSpace.map(ObjectIdentifier.init)) {
                guard let space = session.dataSpace else { return }
                await model.observeAccounts(space.accounts)
            }
            .task(id: session.dataSpace.map(ObjectIdentifier.init)) {
                guard let space = session.dataSpace else { return }
                await model.observeCategories(space.categories)
            }
            .task {
                await model.loadExistingDetails()
            }
        }
    }

    // MARK: - Sections

    private var typeSection: some View {
        Section {
            Picker("transaction.form.type", selection: Binding(get: { model.draft.type }, set: { model.changeType($0) })) {
                Text("transaction.type.expense").tag(TransactionType.expense)
                Text("transaction.type.income").tag(TransactionType.income)
                Text("transaction.type.transfer").tag(TransactionType.transfer)
            }
            .pickerStyle(.segmented)
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())
        }
    }

    private var amountSection: some View {
        Section {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                TextField("transaction.form.amount", text: Binding(get: { model.draft.amountInput }, set: { model.changeAmount($0) }))
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .keyboardType(.decimalPad)
                    .focused($focusedField, equals: .amount)
                    .foregroundStyle(amountColor)
                Text(verbatim: Money.symbol(of: model.currencyCode))
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
            .accessibilityElement(children: .combine)
        } header: {
            Text("transaction.form.amount")
        } footer: {
            if model.error == .invalidAmount { FormErrorText(key: "transaction.form.error.amount") }
        }
    }

    private var accountsSection: some View {
        Section {
            AccountPicker(
                titleKey: model.draft.type == .transfer ? "transaction.form.source_account" : "transaction.form.account",
                selection: model.draft.accountId,
                accounts: model.accounts,
                onSelect: model.changeAccount
            )
            if model.draft.type == .transfer {
                AccountPicker(
                    titleKey: "transaction.form.destination_account",
                    selection: model.draft.transferAccountId,
                    accounts: model.accounts,
                    onSelect: model.changeTransferAccount
                )
            }
        } footer: {
            switch model.error {
            case .accountRequired: FormErrorText(key: "transaction.form.error.account")
            case .destinationRequired: FormErrorText(key: "transaction.form.error.destination")
            case .sameTransferAccount: FormErrorText(key: "transaction.form.error.same_account")
            default: EmptyView()
            }
        }
    }

    private var categorySection: some View {
        Section {
            if model.categories.isEmpty {
                Text("transaction.form.no_category")
                    .foregroundStyle(.secondary)
            } else {
                CategoryGrid(
                    categories: model.categories,
                    selection: model.draft.categoryId,
                    onSelect: model.changeCategory
                )
            }
        } header: {
            Text("transaction.form.category")
        } footer: {
            if model.error == .categoryRequired { FormErrorText(key: "transaction.form.error.category") }
        }
    }

    private var detailsSection: some View {
        Section {
            DatePicker(
                "transaction.form.date_time",
                selection: Binding(get: { model.date }, set: { model.changeDate($0) }),
                displayedComponents: [.date, .hourAndMinute]
            )
            TextField(
                "transaction.form.description",
                text: Binding(get: { model.draft.description }, set: { model.changeDescription($0) }),
                axis: .vertical
            )
            .lineLimit(1...3)
            .focused($focusedField, equals: .description)
            Picker("transaction.form.payment_method", selection: Binding(get: { model.draft.paymentMethod }, set: { model.changePaymentMethod($0) })) {
                Text("transaction.form.payment_method.none").tag(PaymentMethod?.none)
                ForEach(PaymentMethod.allCases, id: \.self) { method in
                    Text(verbatim: method.displayName).tag(PaymentMethod?.some(method))
                }
            }
        }
    }

    private var feeSection: some View {
        Section {
            Toggle("transaction.form.fee.toggle", isOn: Binding(get: { model.draft.hasFee }, set: { model.changeHasFee($0) }))
            if model.draft.hasFee {
                AmountField(
                    titleKey: "transaction.form.fee.amount",
                    text: Binding(get: { model.draft.feeAmountInput }, set: { model.changeFeeAmount($0) }),
                    currencyCode: model.feeCurrencyCode
                )
                .focused($focusedField, equals: .feeAmount)
                Picker("transaction.form.fee.type", selection: Binding(get: { model.draft.feeType }, set: { model.changeFeeType($0) })) {
                    ForEach(FeeType.allCases, id: \.self) { type in
                        Text(verbatim: type.displayName).tag(type)
                    }
                }
                AccountPicker(
                    titleKey: "transaction.form.fee.account",
                    selection: model.draft.feeAccountId,
                    accounts: model.accounts,
                    onSelect: model.changeFeeAccount
                )
                TextField(
                    "transaction.form.fee.description",
                    text: Binding(get: { model.draft.feeDescription }, set: { model.changeFeeDescription($0) })
                )
                .focused($focusedField, equals: .feeDescription)
            }
        } footer: {
            switch model.error {
            case .invalidFeeAmount: FormErrorText(key: "transaction.form.error.fee_amount")
            case .feeAccountRequired: FormErrorText(key: "transaction.form.error.fee_account")
            default: EmptyView()
            }
        }
    }

    private var amountColor: Color {
        switch model.draft.type {
        case .income: return Brand.income
        case .expense: return Brand.expense
        case .transfer: return Brand.transfer
        }
    }

    // MARK: - Enregistrement

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

// MARK: - Composants

/// Choix d'un compte (nom affiché et devise).
private struct AccountPicker: View {
    let titleKey: LocalizedStringKey
    let selection: EntityID?
    let accounts: [Account]
    let onSelect: @MainActor (EntityID?) -> Void

    var body: some View {
        Picker(titleKey, selection: Binding(get: { selection }, set: { onSelect($0) })) {
            if selection == nil {
                Text("transaction.form.account.choose").tag(EntityID?.none)
            }
            ForEach(accounts) { account in
                Label {
                    Text(verbatim: "\(account.displayName) (\(Money.symbol(of: account.currencyCode)))")
                } icon: {
                    Image(systemName: account.icon.systemImage)
                }
                .tag(EntityID?.some(account.id))
            }
        }
    }
}

/// Catégories sous forme de pastilles (icône sur sa couleur + nom), touchables d'un coup d'œil.
private struct CategoryGrid: View {
    let categories: [ArzikinaDomain.Category]
    let selection: EntityID?
    let onSelect: @MainActor (EntityID?) -> Void

    private let columns = [GridItem(.adaptive(minimum: 76), spacing: 10)]

    var body: some View {
        LazyVGrid(columns: columns, spacing: 12) {
            ForEach(categories) { category in
                let isSelected = category.id == selection
                Button {
                    onSelect(category.id)
                } label: {
                    VStack(spacing: 6) {
                        Image(systemName: category.systemImage)
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundStyle(isSelected ? .white : Color(argb: category.colorArgb))
                            .frame(width: 44, height: 44)
                            .background(isSelected ? Color(argb: category.colorArgb) : Color(.tertiarySystemFill), in: Circle())
                        Text(verbatim: category.displayName)
                            .font(.caption2)
                            .lineLimit(2)
                            .multilineTextAlignment(.center)
                            .foregroundStyle(isSelected ? .primary : .secondary)
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(verbatim: category.displayName))
                .accessibilityAddTraits(isSelected ? .isSelected : [])
            }
        }
        .padding(.vertical, 6)
    }
}
