import ArzikinaDomain
import SwiftUI

/// « Mes catégories » (depuis les Réglages, comme Android) : dépenses et revenus séparés,
/// filtre par type, création, modification et suppression.
///
/// Les catégories des prêts et des frais, créées et utilisées par l'app, sont regroupées à part
/// en lecture seule. Une catégorie encore utilisée ne peut pas être supprimée (sinon ses
/// transactions, budgets… se retrouveraient « sans catégorie » sur tous les appareils).
struct CategoriesView: View {

    @Environment(SessionModel.self) private var session
    @State private var model = CategoriesViewModel()
    @State private var formRoute: FormRoute?
    @State private var pendingDeletion: ArzikinaDomain.Category?
    @State private var deletionAlert: CategoriesViewModel.DeletionOutcome?

    private enum FormRoute: Identifiable {
        case create(TransactionType)
        case edit(ArzikinaDomain.Category)

        var id: String {
            switch self {
            case .create(let type): return "create-\(type.rawValue)"
            case .edit(let category): return "edit-\(category.id)"
            }
        }

        var mode: CategoryFormViewModel.Mode {
            switch self {
            case .create(let type): return .create(type: type)
            case .edit(let category): return .edit(category)
            }
        }
    }

    var body: some View {
        content
            .navigationTitle("categories.title")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        formRoute = .create(model.filter.typeForNewCategory)
                    } label: {
                        Label("categories.add", systemImage: "plus")
                    }
                }
            }
            .sheet(item: $formRoute) { route in
                if let space = session.dataSpace {
                    CategoryFormView(mode: route.mode, repository: space.categories)
                        .environment(session)
                }
            }
            .confirmationDialog(
                "categories.delete.title",
                isPresented: Binding(get: { pendingDeletion != nil }, set: { if !$0 { pendingDeletion = nil } }),
                titleVisibility: .visible,
                presenting: pendingDeletion
            ) { category in
                Button("categories.delete", role: .destructive) { delete(category) }
            } message: { category in
                Text("categories.delete.message \(category.displayName)")
            }
            .alert(
                deletionAlert == .failed ? LocalizedStringKey("categories.delete.failed") : LocalizedStringKey("categories.delete.in_use"),
                isPresented: Binding(get: { deletionAlert != nil }, set: { if !$0 { deletionAlert = nil } })
            ) {
                Button("common.ok", role: .cancel) {}
            }
            .refreshable {
                await session.sync?.refresh()
            }
            .task(id: session.dataSpace.map(ObjectIdentifier.init)) {
                guard let space = session.dataSpace else { return }
                await model.observe(space.categories)
            }
    }

    // MARK: - Contenu

    @ViewBuilder
    private var content: some View {
        if !model.hasLoaded {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
        } else if model.categories.isEmpty {
            ContentUnavailableView {
                Label("categories.empty.title", systemImage: "square.grid.2x2")
            } description: {
                Text("categories.empty.message")
            } actions: {
                Button("categories.add") { formRoute = .create(.expense) }
                    .buttonStyle(.borderedProminent)
            }
        } else {
            List {
                Section {
                    Picker("categories.filter", selection: Bindable(model).filter) {
                        Text("categories.filter.all").tag(CategoriesViewModel.Filter.all)
                        Text("category.type.expense").tag(CategoriesViewModel.Filter.expense)
                        Text("category.type.income").tag(CategoriesViewModel.Filter.income)
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }

                if !model.expenseCategories.isEmpty {
                    Section("categories.section.expense") {
                        ForEach(model.expenseCategories) { editableRow($0) }
                    }
                }
                if !model.incomeCategories.isEmpty {
                    Section("categories.section.income") {
                        ForEach(model.incomeCategories) { editableRow($0) }
                    }
                }
                if !model.managedCategories.isEmpty {
                    Section {
                        ForEach(model.managedCategories) { category in
                            Button {
                                formRoute = .edit(category)
                            } label: {
                                CategoryRow(category: category, isLocked: true)
                            }
                            .buttonStyle(.plain)
                        }
                    } header: {
                        Text("categories.section.managed")
                    } footer: {
                        Text("categories.section.managed.footer")
                    }
                }
                if model.isFilteredListEmpty {
                    Section {
                        Text("categories.filter.empty")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .listStyle(.insetGrouped)
        }
    }

    private func editableRow(_ category: ArzikinaDomain.Category) -> some View {
        Button {
            formRoute = .edit(category)
        } label: {
            CategoryRow(category: category, isLocked: false)
        }
        .buttonStyle(.plain)
        // Pas de `role: .destructive` : la ligne disparaîtrait AVANT la confirmation.
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button {
                pendingDeletion = category
            } label: {
                Label("categories.delete", systemImage: "trash")
            }
            .tint(.red)
        }
        .contextMenu {
            Button(role: .destructive) {
                pendingDeletion = category
            } label: {
                Label("categories.delete", systemImage: "trash")
            }
        }
    }

    private func delete(_ category: ArzikinaDomain.Category) {
        guard let space = session.dataSpace else { return }
        Task {
            let outcome = await model.delete(category, using: space.categories)
            if outcome == .deleted {
                session.sync?.requestSync(.localChange)
            } else {
                deletionAlert = outcome
            }
        }
    }
}

/// Ligne de catégorie : pastille (icône sur sa couleur) et nom affiché.
private struct CategoryRow: View {
    let category: ArzikinaDomain.Category
    let isLocked: Bool

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: category.systemImage)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(Color(argb: category.colorArgb), in: Circle())
                .accessibilityHidden(true)
            Text(verbatim: category.displayName)
                .font(.body)
                .lineLimit(1)
            Spacer(minLength: 8)
            if isLocked {
                Image(systemName: "lock.fill")
                    .font(.footnote)
                    .foregroundStyle(.tertiary)
                    .accessibilityLabel(Text("categories.managed.accessibility"))
            }
        }
        .padding(.vertical, 2)
        .contentShape(Rectangle())
    }
}

#Preview {
    NavigationStack { CategoriesView() }
        .environment(SessionModel.preview())
}
