import ArzikinaDomain
import SwiftUI

/// Toutes les transactions, groupées par jour (du plus récent au plus ancien), avec recherche
/// instantanée, filtres (type, compte, catégorie, période) et suppression — Android
/// `TransactionsFragment`, atteint comme sur Android depuis « Voir tout » du tableau de bord.
///
/// Conventions iOS : recherche dans la barre de navigation, filtres dans une feuille, suppression
/// par balayage ou menu contextuel, toujours confirmée.
struct TransactionsView: View {

    @Environment(SessionModel.self) private var session
    @Environment(\.scenePhase) private var scenePhase
    @State private var model = TransactionsViewModel()
    @State private var transactionForm: TransactionFormRoute?
    @State private var isShowingFilters = false
    @State private var pendingDeletion: ArzikinaDomain.Transaction?
    @State private var deletionAlert: DeletionAlert?

    private enum DeletionAlert {
        case linkedToLoan, failed

        var titleKey: LocalizedStringKey {
            switch self {
            case .linkedToLoan: return "transactions.delete.loan_linked"
            case .failed: return "transactions.delete.failed"
            }
        }
    }

    var body: some View {
        navigationContent
            .confirmationDialog(
                "transactions.delete.title",
                isPresented: Binding(get: { pendingDeletion != nil }, set: { if !$0 { pendingDeletion = nil } }),
                titleVisibility: .visible,
                presenting: pendingDeletion
            ) { transaction in
                Button("transactions.delete", role: .destructive) { delete(transaction) }
            } message: { _ in
                Text("transactions.delete.message")
            }
            .alert(
                deletionAlertTitle,
                isPresented: Binding(get: { deletionAlert != nil }, set: { if !$0 { deletionAlert = nil } })
            ) {
                Button("common.ok", role: .cancel) {}
            }
            .refreshable {
                await session.sync?.refresh()
            }
            .task(id: spaceId) {
                guard let space = session.dataSpace else { return }
                await model.observeLedger(space.ledger)
            }
            .task(id: spaceId) {
                guard let space = session.dataSpace else { return }
                await model.observeAccounts(space.accounts)
            }
            .task(id: spaceId) {
                guard let space = session.dataSpace else { return }
                await model.observeCategories(space.categories)
            }
            .onChange(of: scenePhase) { _, phase in
                if phase == .active { model.refreshPeriod() }
            }
    }

    /// Liste, titre, recherche, barre d'outils et feuilles. Séparé de `body` pour que le
    /// compilateur vérifie deux expressions courtes plutôt qu'une très longue.
    private var navigationContent: some View {
        @Bindable var model = model
        return content
            .navigationTitle("transactions.title")
            .searchable(text: $model.filters.query, prompt: Text("transactions.search_placeholder"))
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        isShowingFilters = true
                    } label: {
                        Label("transactions.filters", systemImage: model.filters.hasActiveFilters
                              ? "line.3.horizontal.decrease.circle.fill"
                              : "line.3.horizontal.decrease.circle")
                    }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        transactionForm = .create(presetAccountId: model.filters.accountId)
                    } label: {
                        Label("transaction.add", systemImage: "plus")
                    }
                    .disabled(model.accounts.isEmpty)
                }
            }
            .sheet(isPresented: $isShowingFilters) {
                TransactionFiltersSheet(filters: $model.filters, accounts: model.accounts, categories: model.categories)
                    .presentationDetents([.medium, .large])
            }
            .transactionFormSheet($transactionForm, session: session)
    }

    private var deletionAlertTitle: LocalizedStringKey {
        deletionAlert?.titleKey ?? "transactions.delete.failed"
    }

    private var spaceId: ObjectIdentifier? {
        session.dataSpace.map(ObjectIdentifier.init)
    }

    // MARK: - Contenu

    @ViewBuilder
    private var content: some View {
        if !model.hasLoaded {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
        } else if !model.hasTransactions {
            ContentUnavailableView {
                Label("transactions.empty.title", systemImage: "list.bullet.rectangle")
            } description: {
                Text("transactions.empty.message")
            } actions: {
                if !model.accounts.isEmpty {
                    Button("transaction.add") { transactionForm = .create(presetAccountId: nil) }
                        .buttonStyle(.borderedProminent)
                }
            }
        } else {
            List {
                if model.filters.hasActiveFilters {
                    Section {
                        ActiveFiltersRow(filters: model.filters, accounts: model.accounts, categories: model.categories) {
                            model.filters.resetFilters()
                        }
                    }
                }
                if model.sections.isEmpty {
                    Section {
                        ContentUnavailableView {
                            Label("transactions.no_results.title", systemImage: "magnifyingglass")
                        } description: {
                            Text("transactions.no_results.message")
                        }
                        .listRowBackground(Color.clear)
                    }
                } else {
                    ForEach(model.sections, id: \.day) { section in
                        Section {
                            ForEach(section.items) { item in
                                row(item)
                            }
                        } header: {
                            DayHeader(day: section.day)
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
        }
    }

    private func row(_ item: TransactionListItem) -> some View {
        Button {
            transactionForm = .edit(item.transaction)
        } label: {
            TransactionRow(item: item, style: .grouped)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        // Pas de `role: .destructive` : SwiftUI retirerait la ligne AVANT la confirmation.
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button {
                requestDeletion(item.transaction)
            } label: {
                Label("transactions.delete", systemImage: "trash")
            }
            .tint(.red)
        }
        .contextMenu {
            Button(role: .destructive) {
                requestDeletion(item.transaction)
            } label: {
                Label("transactions.delete", systemImage: "trash")
            }
        }
    }

    // MARK: - Suppression

    /// Une transaction de prêt n'est jamais proposée à la suppression (elle se gère depuis le prêt).
    private func requestDeletion(_ transaction: ArzikinaDomain.Transaction) {
        guard let space = session.dataSpace else { return }
        Task {
            let isLinked = (try? await space.transactions.isLinkedToLoan(id: transaction.id)) ?? false
            if isLinked {
                deletionAlert = .linkedToLoan
            } else {
                pendingDeletion = transaction
            }
        }
    }

    private func delete(_ transaction: ArzikinaDomain.Transaction) {
        guard let space = session.dataSpace else { return }
        Task {
            switch await model.delete(transaction, using: space.transactions) {
            case .deleted: session.sync?.requestSync(.localChange)
            case .linkedToLoan: deletionAlert = .linkedToLoan
            case .failed: deletionAlert = .failed
            }
        }
    }
}

/// Rappel des filtres actifs (« Ce mois · Espèces ») et bouton pour les retirer d'un geste.
private struct ActiveFiltersRow: View {

    let filters: TransactionFilters
    let accounts: [Account]
    let categories: [ArzikinaDomain.Category]
    let onReset: @MainActor () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "line.3.horizontal.decrease.circle.fill")
                .foregroundStyle(Brand.primary)
                .accessibilityHidden(true)
            Text(verbatim: summary)
                .font(.subheadline)
                .lineLimit(2)
            Spacer(minLength: 8)
            Button("transactions.filters.reset") { onReset() }
                .font(.subheadline.weight(.semibold))
                .buttonStyle(.borderless)
        }
        .accessibilityElement(children: .combine)
    }

    private var summary: String {
        var parts: [String] = []
        switch filters.kind {
        case .all: break
        case .income: parts.append(DomainDisplay.localized("transactions.filters.kind.income"))
        case .expense: parts.append(DomainDisplay.localized("transactions.filters.kind.expense"))
        }
        switch filters.period {
        case .all: break
        case .thisWeek: parts.append(DomainDisplay.localized("transactions.period.this_week"))
        case .thisMonth: parts.append(DomainDisplay.localized("transactions.period.this_month"))
        }
        if let id = filters.accountId, let account = accounts.first(where: { $0.id == id }) {
            parts.append(account.displayName)
        }
        if let id = filters.categoryId, let category = categories.first(where: { $0.id == id }) {
            parts.append(category.displayName)
        }
        return parts.joined(separator: " · ")
    }
}

#Preview {
    NavigationStack { TransactionsView() }
        .environment(SessionModel.preview())
}
