import ArzikinaDomain
import SwiftUI

/// Liste des budgets (depuis « Voir tout » du tableau de bord ou les Réglages, comme Android) :
/// filtre par statut, création, modification, suppression confirmée.
///
/// `List` à cartes : chargement paresseux et gestes natifs (balayage, menu contextuel).
struct BudgetsView: View {

    @Environment(SessionModel.self) private var session
    @Environment(\.scenePhase) private var scenePhase
    @State private var model = BudgetsViewModel()
    @State private var form: BudgetFormRoute?
    @State private var pendingDeletion: Budget?
    @State private var deletionFailed = false

    var body: some View {
        content
            .navigationTitle("budgets.title")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        form = .create
                    } label: {
                        Label("budgets.add", systemImage: "plus")
                    }
                }
            }
            .budgetFormSheet($form, session: session)
            .confirmationDialog(
                "budgets.delete.title",
                isPresented: Binding(get: { pendingDeletion != nil }, set: { if !$0 { pendingDeletion = nil } }),
                titleVisibility: .visible,
                presenting: pendingDeletion
            ) { budget in
                Button("budgets.delete", role: .destructive) { delete(budget) }
            } message: { _ in
                Text("budgets.delete.message")
            }
            .alert("budgets.delete.failed", isPresented: $deletionFailed) {
                Button("common.ok", role: .cancel) {}
            }
            .refreshable {
                await session.sync?.refresh()
            }
            // Relancé au changement d'utilisateur et de JOUR (périodes, statuts, jours restants).
            .task(id: ObservationKey(space: session.dataSpace.map(ObjectIdentifier.init), today: today)) {
                guard let space = session.dataSpace else { return }
                await model.observe(space.budgets, today: today, calendar: ArzikinaCalendar.current)
            }
    }

    private struct ObservationKey: Equatable {
        let space: ObjectIdentifier?
        let today: CalendarDay
    }

    private var today: CalendarDay {
        _ = scenePhase // réévalué au retour au premier plan
        return .today()
    }

    // MARK: - Contenu

    @ViewBuilder
    private var content: some View {
        if !model.hasLoaded {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
        } else if model.summaries.isEmpty {
            ContentUnavailableView {
                Label("budgets.empty.title", systemImage: "chart.bar")
            } description: {
                Text("budgets.empty.message")
            } actions: {
                Button("dashboard.budget.create") { form = .create }
                    .buttonStyle(.borderedProminent)
            }
        } else {
            List {
                Section {
                    Picker("budgets.filter", selection: Bindable(model).filter) {
                        Text("budgets.filter.all").tag(BudgetStatusFilter.all)
                        Text("budgets.filter.upcoming").tag(BudgetStatusFilter.upcoming)
                        Text("budgets.filter.ongoing").tag(BudgetStatusFilter.ongoing)
                        Text("budgets.filter.completed").tag(BudgetStatusFilter.completed)
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())

                let visible = model.visibleSummaries
                if visible.isEmpty {
                    Text("budgets.empty.filtered")
                        .foregroundStyle(.secondary)
                        .listRowBackground(Color.clear)
                } else {
                    ForEach(visible) { summary in
                        row(summary)
                    }
                }
            }
            .listStyle(.plain)
            .listRowSpacing(12)
            .scrollContentBackground(.hidden)
            .background(Color(.systemGroupedBackground))
        }
    }

    private func row(_ summary: BudgetSummary) -> some View {
        Button {
            form = .edit(summary.budget)
        } label: {
            BudgetCard(summary: summary)
        }
        .buttonStyle(.plain)
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
        .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
        // Pas de `role: .destructive` : la ligne disparaîtrait AVANT la confirmation.
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button {
                pendingDeletion = summary.budget
            } label: {
                Label("budgets.delete", systemImage: "trash")
            }
            .tint(.red)
        }
        .contextMenu {
            Button(role: .destructive) {
                pendingDeletion = summary.budget
            } label: {
                Label("budgets.delete", systemImage: "trash")
            }
        }
    }

    private func delete(_ budget: Budget) {
        guard let space = session.dataSpace else { return }
        Task {
            if await model.delete(budget, using: space.budgets) {
                session.sync?.requestSync(.localChange)
            } else {
                deletionFailed = true
            }
        }
    }
}

#Preview {
    NavigationStack { BudgetsView() }
        .environment(SessionModel.preview())
}
