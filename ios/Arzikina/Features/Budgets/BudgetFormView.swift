import ArzikinaDomain
import SwiftUI

/// Ouverture du formulaire de budget (même présentation depuis tous les écrans).
enum BudgetFormRoute: Identifiable {
    case create
    case edit(Budget)

    var id: String {
        switch self {
        case .create: return "create"
        case .edit(let budget): return "edit-\(budget.id)"
        }
    }

    var mode: BudgetFormViewModel.Mode {
        switch self {
        case .create: return .create
        case .edit(let budget): return .edit(budget)
        }
    }
}

extension View {
    func budgetFormSheet(_ route: Binding<BudgetFormRoute?>, session: SessionModel) -> some View {
        sheet(item: route) { route in
            if let space = session.dataSpace {
                BudgetFormView(mode: route.mode, budgets: space.budgets, categories: space.categories, accounts: space.accounts)
                    .environment(session)
            }
        }
    }
}

/// Formulaire de budget (feuille modale) : catégorie, plafond et devise, puis la période —
/// raccourcis (« Cette semaine », « Ce mois »…) ou dates choisies à la main.
struct BudgetFormView: View {

    @Environment(\.dismiss) private var dismiss
    @Environment(SessionModel.self) private var session
    @State private var model: BudgetFormViewModel
    @State private var isConfirmingDelete = false
    @FocusState private var isLimitFocused: Bool

    private let categories: CategoryRepository
    private let accounts: AccountRepository

    init(mode: BudgetFormViewModel.Mode, budgets: BudgetRepository, categories: CategoryRepository, accounts: AccountRepository) {
        _model = State(initialValue: BudgetFormViewModel(mode: mode, repository: budgets))
        self.categories = categories
        self.accounts = accounts
    }

    var body: some View {
        @Bindable var model = model
        NavigationStack {
            Form {
                categorySection
                Section {
                    AmountField(titleKey: "budget.form.limit", text: $model.draft.limitInput, currencyCode: model.draft.currencyCode)
                        .focused($isLimitFocused)
                    Picker("account.form.currency", selection: Binding(get: { model.draft.currencyCode }, set: { model.changeCurrency($0) })) {
                        ForEach(SupportedCurrency.allCases, id: \.self) { currency in
                            Text(verbatim: currency.pickerLabel).tag(currency.code)
                        }
                        if SupportedCurrency(rawValue: model.draft.currencyCode) == nil {
                            Text(verbatim: model.draft.currencyCode).tag(model.draft.currencyCode)
                        }
                    }
                } footer: {
                    if model.error == .invalidLimit { FormErrorText(key: "budget.form.error.limit") }
                }
                periodSection

                if model.saveFailed {
                    Section { FormErrorText(key: "budget.form.save_failed") }
                }
                if model.isEditing {
                    Section {
                        Button(role: .destructive) {
                            isLimitFocused = false
                            isConfirmingDelete = true
                        } label: {
                            Text("budget.form.delete").frame(maxWidth: .infinity)
                        }
                        .disabled(model.isWorking)
                    } footer: {
                        if model.deleteFailed { FormErrorText(key: "budgets.delete.failed") }
                    }
                }
            }
            .navigationTitle(model.isEditing ? LocalizedStringKey("budget.form.title.edit") : LocalizedStringKey("budget.form.title.add"))
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
            .confirmationDialog("budgets.delete.title", isPresented: $isConfirmingDelete, titleVisibility: .visible) {
                Button("budgets.delete", role: .destructive) { deleteBudget() }
            } message: {
                Text("budgets.delete.message")
            }
            .task { await model.observeCategories(categories) }
            .task { await model.observeBudgets() }
            .task { await model.observeAccounts(accounts) }
        }
    }

    // MARK: - Sections

    @ViewBuilder
    private var categorySection: some View {
        let categories = model.availableCategories
        Section {
            if model.hasLoadedCategories && categories.isEmpty {
                Text("budget.form.no_categories")
                    .foregroundStyle(.secondary)
            } else {
                Picker("budget.form.category", selection: Bindable(model).draft.categoryId) {
                    if model.draft.categoryId == nil {
                        Text("transaction.form.account.choose").tag(EntityID?.none)
                    }
                    ForEach(categories) { category in
                        Label {
                            Text(verbatim: category.displayName)
                        } icon: {
                            Image(systemName: category.systemImage)
                        }
                        .tag(EntityID?.some(category.id))
                    }
                }
            }
        } footer: {
            if model.error == .categoryRequired { FormErrorText(key: "budget.form.error.category") }
        }
    }

    @ViewBuilder
    private var periodSection: some View {
        if model.draft.isLegacyRecurring {
            Section {
                Picker("budget.form.period", selection: Bindable(model).draft.period) {
                    Text("budget.period.weekly").tag(BudgetPeriod.weekly)
                    Text("budget.period.monthly").tag(BudgetPeriod.monthly)
                }
                .pickerStyle(.segmented)
            } header: {
                Text("budget.form.period")
            } footer: {
                Text("budget.form.recurring_footer")
            }
        } else {
            Section {
                QuickRangeChips(selection: model.draft.quickRange) { model.applyQuickRange($0) }
                DatePicker(
                    "budget.form.start_date",
                    selection: Binding(get: { model.startDate }, set: { model.changeStartDate($0) }),
                    displayedComponents: .date
                )
                DatePicker(
                    "budget.form.end_date",
                    selection: Binding(get: { model.endDate }, set: { model.changeEndDate($0) }),
                    in: model.startDate...,
                    displayedComponents: .date
                )
            } header: {
                Text("budget.form.period")
            } footer: {
                switch model.error {
                case .periodRequired: FormErrorText(key: "budget.form.error.period")
                case .endBeforeStart: FormErrorText(key: "budget.form.error.end_before_start")
                default: EmptyView()
                }
            }
        }
    }

    // MARK: - Actions

    private func submit() {
        isLimitFocused = false
        Task {
            if await model.save() {
                session.sync?.requestSync(.localChange)
                dismiss()
            }
        }
    }

    private func deleteBudget() {
        Task {
            if await model.delete() {
                session.sync?.requestSync(.localChange)
                dismiss()
            }
        }
    }
}

/// Raccourcis de période (« Personnalisée » est sélectionné quand les dates ont été choisies à la
/// main).
private struct QuickRangeChips: View {
    let selection: BudgetQuickRange?
    let onSelect: @MainActor (BudgetQuickRange) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(BudgetQuickRange.allCases, id: \.self) { range in
                    chip(titleKey(range), isSelected: selection == range) { onSelect(range) }
                }
                chip("budget.quick_range.custom", isSelected: selection == nil, action: nil)
            }
            .padding(.vertical, 4)
        }
    }

    private func chip(_ titleKey: LocalizedStringKey, isSelected: Bool, action: (() -> Void)?) -> some View {
        Button {
            action?()
        } label: {
            Text(titleKey)
                .font(.subheadline.weight(isSelected ? .semibold : .regular))
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .foregroundStyle(isSelected ? .white : .primary)
                .background(isSelected ? Brand.primary : Color(.tertiarySystemFill), in: Capsule())
        }
        .buttonStyle(.plain)
        .disabled(action == nil)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func titleKey(_ range: BudgetQuickRange) -> LocalizedStringKey {
        switch range {
        case .thisWeek: return "budget.quick_range.this_week"
        case .thisMonth: return "budget.quick_range.this_month"
        case .nextMonth: return "budget.quick_range.next_month"
        case .thisYear: return "budget.quick_range.this_year"
        }
    }
}
