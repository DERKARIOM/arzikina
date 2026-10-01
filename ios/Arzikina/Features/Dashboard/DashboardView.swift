import ArzikinaDomain
import SwiftUI

/// Onglet Accueil : solde total, revenus et dépenses du mois, dernières transactions — à jour en
/// continu à partir de la base locale (saisies et synchronisation). « Tirer pour actualiser »
/// lance une synchronisation.
///
/// Les budgets arriveront avec leur propre étape ; la carte reste annoncée en attendant.
struct DashboardView: View {

    @Environment(SessionModel.self) private var session
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// Préférence d'affichage NON sensible (aucun montant stocké) → UserDefaults.
    @AppStorage("dashboard.isBalanceHidden") private var isBalanceHidden = false
    @State private var model = DashboardViewModel()
    @State private var transactionForm: TransactionFormRoute?
    @State private var isShowingAllTransactions = false

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                BrandHeaderCard(
                    firstName: firstName,
                    balances: model.totalBalances,
                    showsSyncHint: model.hasLoaded && !model.hasAccounts,
                    isBalanceHidden: $isBalanceHidden
                )

                if model.hasLoaded && model.hasAccounts {
                    let monthRows = model.monthRows
                    if !monthRows.isEmpty {
                        MonthSummaryCard(rows: monthRows, isAmountHidden: isBalanceHidden)
                    }
                    RecentTransactionsCard(
                        items: model.recentTransactions,
                        onSelect: { item in transactionForm = .edit(item.transaction) },
                        onSeeAll: { isShowingAllTransactions = true }
                    )
                }

                UpcomingSectionCard(titleKey: "dashboard.budgets", systemImage: "chart.bar.fill")
            }
            .padding()
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.25), value: model.snapshot)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("tab.home")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    transactionForm = .create(presetAccountId: nil)
                } label: {
                    Label("transaction.add", systemImage: "plus")
                }
                .disabled(!model.hasAccounts)
            }
        }
        .transactionFormSheet($transactionForm, session: session)
        .navigationDestination(isPresented: $isShowingAllTransactions) {
            TransactionsView()
        }
        .refreshable {
            await session.sync?.refresh()
        }
        // Relancé à chaque changement d'utilisateur, de base (vidage) ou de MOIS : le mois est
        // recalculé à chaque retour de l'app au premier plan (`scenePhase` lu ci-dessous).
        .task(id: observationKey) {
            guard let space = session.dataSpace else { return }
            let month = observationKey.month
            await model.observe(space.dashboard, monthStart: month.start, monthEnd: month.end)
        }
    }

    private var firstName: String? {
        session.currentSession?.fullName.split(separator: " ").first.map(String.init)
    }

    private struct ObservationKey: Equatable {
        let space: ObjectIdentifier?
        let month: Month
        struct Month: Equatable {
            let start: EpochMillis
            let end: EpochMillis
        }
    }

    private var observationKey: ObservationKey {
        _ = scenePhase // dépendance volontaire : réévalue le mois au retour au premier plan
        let interval = DashboardRules.monthInterval(
            containing: EpochMillis(Date().timeIntervalSince1970 * 1000),
            calendar: ArzikinaCalendar.current
        )
        return ObservationKey(
            space: session.dataSpace.map(ObjectIdentifier.init),
            month: .init(start: interval.start, end: interval.end)
        )
    }
}

#Preview {
    NavigationStack { DashboardView() }
        .environment(SessionModel.preview())
}
