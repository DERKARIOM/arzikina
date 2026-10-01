import ArzikinaDomain
import SwiftUI

/// Prêts et emprunts (Réglages › Budget et finances, comme Android) : reste à recevoir et à
/// rembourser, filtres par type et par statut, recherche, et accès au détail de chacun.
struct LoansView: View {

    @Environment(SessionModel.self) private var session
    @Environment(\.scenePhase) private var scenePhase
    @State private var model = LoansViewModel()
    @State private var isCreating = false

    var body: some View {
        @Bindable var model = model
        content
            .navigationTitle("loans.title")
            .searchable(text: $model.filters.query, prompt: Text("loans.search_placeholder"))
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        isCreating = true
                    } label: {
                        Label("loans.add", systemImage: "plus")
                    }
                }
            }
            .sheet(isPresented: $isCreating) {
                if let space = session.dataSpace {
                    LoanFormView(mode: .create, loans: space.loans, accounts: space.accounts)
                        .environment(session)
                }
            }
            .navigationDestination(for: LoanRoute.self) { route in
                LoanDetailView(loanId: route.id)
            }
            .refreshable { await session.sync?.refresh() }
            .task(id: ObservationKey(space: session.dataSpace.map(ObjectIdentifier.init), day: today)) {
                guard let space = session.dataSpace else { return }
                await model.observe(space.loans, now: EpochMillis(Date().timeIntervalSince1970 * 1000), calendar: ArzikinaCalendar.current)
            }
    }

    private struct ObservationKey: Equatable {
        let space: ObjectIdentifier?
        let day: CalendarDay
    }

    /// Statuts (« en retard ») recalculés au retour au premier plan et au changement de jour.
    private var today: CalendarDay {
        _ = scenePhase
        return .today()
    }

    @ViewBuilder
    private var content: some View {
        if !model.hasLoaded {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
        } else if model.summaries.isEmpty {
            ContentUnavailableView {
                Label("loans.empty.title", systemImage: "person.2")
            } description: {
                Text("loans.empty.message")
            } actions: {
                Button("loans.add") { isCreating = true }
                    .buttonStyle(.borderedProminent)
            }
        } else {
            let visible = model.visible
            List {
                Section {
                    LoansSummaryCard(summaries: visible)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }
                Section {
                    filters
                }
                if visible.isEmpty {
                    Section {
                        ContentUnavailableView {
                            Label("loans.no_results.title", systemImage: "magnifyingglass")
                        } description: {
                            Text("loans.no_results.message")
                        }
                        .listRowBackground(Color.clear)
                    }
                } else {
                    Section {
                        ForEach(visible) { summary in
                            NavigationLink(value: LoanRoute(id: summary.id)) {
                                LoanRow(summary: summary)
                            }
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
        }
    }

    private var filters: some View {
        @Bindable var model = model
        return Group {
            Picker("loans.filter.type", selection: $model.filters.type) {
                Text("loans.filter.all").tag(LoanType?.none)
                Text("loans.type.lent.plural").tag(LoanType?.some(.lent))
                Text("loans.type.borrowed.plural").tag(LoanType?.some(.borrowed))
            }
            .pickerStyle(.segmented)
            Picker("loans.filter.status", selection: $model.filters.status) {
                Text("loans.filter.all").tag(LoanStatus?.none)
                ForEach([LoanStatus.ongoing, .overdue, .upcoming, .repaid], id: \.self) { status in
                    Text(status.titleKey).tag(LoanStatus?.some(status))
                }
            }
        }
    }
}

/// Navigation vers le détail d'un prêt.
struct LoanRoute: Hashable {
    let id: EntityID
}

/// Reste à recevoir (prêts) et à rembourser (emprunts), par devise, sur les lignes visibles.
private struct LoansSummaryCard: View {
    let summaries: [LoanSummary]

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            column("loans.summary.receivable", LoanList.remainingByCurrency(summaries, type: .lent), count: summaries.filter { $0.loan.type == .lent }.count, color: Brand.income)
            Divider()
            column("loans.summary.owed", LoanList.remainingByCurrency(summaries, type: .borrowed), count: summaries.filter { $0.loan.type == .borrowed }.count, color: Brand.expense)
        }
        .fixedSize(horizontal: false, vertical: true)
        .arzikinaCard()
    }

    private func column(_ titleKey: LocalizedStringKey, _ amounts: [CurrencyAmount], count: Int, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(titleKey)
                .font(.caption)
                .foregroundStyle(.secondary)
            if amounts.isEmpty {
                Text(verbatim: Money.format(CurrencyAmount(currencyCode: SupportedCurrency.defaultCode, amountMinor: 0)))
                    .font(.headline.monospacedDigit())
            } else {
                ForEach(amounts, id: \.currencyCode) { amount in
                    Text(verbatim: Money.format(amount))
                        .font(.headline.monospacedDigit())
                        .foregroundStyle(color)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                }
            }
            Text("loans.summary.count \(count)")
                .font(.caption2)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// Ligne d'un prêt : titre, personne, statut, progression et reste.
struct LoanRow: View {
    let summary: LoanSummary

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: loanTitle(summary))
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    Text(verbatim: loanPersonLine(summary))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                Spacer(minLength: 8)
                LoanStatusBadge(status: summary.status)
            }
            ProgressView(value: Double(summary.progressPercent), total: 100)
                .tint(summary.status == .overdue ? Brand.expense : Brand.primary)
            HStack {
                Text("loans.repaid \(format(summary.amountRepaid)) \(format(summary.loan.amount))")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
                Spacer()
                Text("loans.remaining \(format(summary.remaining))")
                    .font(.caption.weight(.semibold).monospacedDigit())
            }
        }
        .padding(.vertical, 4)
        .accessibilityElement(children: .combine)
    }

    private func format(_ amount: MinorUnits) -> String {
        Money.format(CurrencyAmount(currencyCode: summary.currencyCode, amountMinor: amount))
    }
}

#Preview {
    NavigationStack { LoansView() }
        .environment(SessionModel.preview())
}
