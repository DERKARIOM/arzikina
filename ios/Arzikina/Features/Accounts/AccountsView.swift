import ArzikinaDomain
import SwiftUI

/// Onglet Comptes : trois groupes (Comptes, Cartes bancaires, Épargne), une carte par compte avec
/// son solde courant, et le détail d'un compte au toucher. Mis à jour en continu (saisies locales
/// et synchronisation) ; « tirer pour actualiser » lance une synchronisation.
///
/// Bouton « + » : nouveau compte, avec le type présélectionné selon le groupe affiché.
struct AccountsView: View {

    @Environment(SessionModel.self) private var session
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = AccountsViewModel()
    @State private var isCreating = false

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                Picker("accounts.group.picker", selection: $model.selectedGroup) {
                    ForEach(AccountGroup.allCases, id: \.self) { group in
                        Text(group.titleKey).tag(group)
                    }
                }
                .pickerStyle(.segmented)
                .padding(.bottom, 4)

                if model.hasLoaded && model.visibleSummaries.isEmpty {
                    emptyState
                } else {
                    ForEach(model.visibleSummaries) { summary in
                        NavigationLink(value: AccountRoute(id: summary.id)) {
                            card(for: summary)
                        }
                        .buttonStyle(PressableCardStyle())
                    }
                }
            }
            .padding()
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.25), value: model.selectedGroup)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("tab.accounts")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    isCreating = true
                } label: {
                    Label("accounts.add", systemImage: "plus")
                }
                .disabled(session.dataSpace == nil)
            }
        }
        .sheet(isPresented: $isCreating) {
            if let space = session.dataSpace {
                AccountFormView(mode: .create(initialType: model.selectedGroup.defaultType), repository: space.accounts)
            }
        }
        .navigationDestination(for: AccountRoute.self) { route in
            AccountDetailView(accountId: route.id)
        }
        .refreshable {
            await session.sync?.refresh()
        }
        .task(id: session.dataSpace.map(ObjectIdentifier.init)) {
            guard let space = session.dataSpace else { return }
            await model.observe(space.accountOverview)
        }
    }

    @ViewBuilder
    private func card(for summary: AccountSummary) -> some View {
        if summary.account.type == .creditCard {
            CreditCardView(summary: summary, holderName: session.currentSession?.fullName ?? "")
        } else {
            AccountCard(summary: summary)
        }
    }

    private var emptyState: some View {
        ContentUnavailableView {
            Label(model.selectedGroup.emptyTitleKey, systemImage: model.selectedGroup.systemImage)
        } description: {
            Text(model.selectedGroup.emptyMessageKey)
        }
        .padding(.top, 40)
    }
}

/// Destination de navigation vers le détail d'un compte.
struct AccountRoute: Hashable {
    let id: EntityID
}

/// Léger enfoncement au toucher d'une carte (retour visuel discret, sans surlignage de liste).
struct PressableCardStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.98 : 1)
            .opacity(configuration.isPressed ? 0.9 : 1)
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
    }
}

private extension AccountGroup {
    /// Type présélectionné pour un compte créé depuis ce groupe.
    var defaultType: AccountType {
        switch self {
        case .accounts: return .cash
        case .bankCards: return .creditCard
        case .savingsGoals: return .savingsGoal
        }
    }

    var titleKey: LocalizedStringKey {
        switch self {
        case .accounts: return "accounts.group.accounts"
        case .bankCards: return "accounts.group.bank_cards"
        case .savingsGoals: return "accounts.group.savings_goals"
        }
    }

    var emptyTitleKey: LocalizedStringKey {
        switch self {
        case .accounts: return "accounts.empty.title"
        case .bankCards: return "accounts.empty.bank_cards.title"
        case .savingsGoals: return "accounts.empty.savings_goals.title"
        }
    }

    var emptyMessageKey: LocalizedStringKey {
        switch self {
        case .accounts: return "accounts.empty.message"
        case .bankCards: return "accounts.empty.bank_cards.message"
        case .savingsGoals: return "accounts.empty.savings_goals.message"
        }
    }

    var systemImage: String {
        switch self {
        case .accounts: return "wallet.pass"
        case .bankCards: return "creditcard"
        case .savingsGoals: return "target"
        }
    }
}

#Preview {
    NavigationStack { AccountsView() }
        .environment(SessionModel.preview())
}
