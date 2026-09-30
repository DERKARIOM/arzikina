import SwiftUI

/// Les onglets principaux de l'application — mêmes sections que la Bottom Navigation Android
/// (Accueil, Comptes, Rapports, Paramètres), présentées avec les conventions iOS (TabView +
/// une NavigationStack par onglet).
///
/// Ajouter un onglet = ajouter un cas ici et sa vue racine dans [rootView] ; `MainTabView` n'a
/// pas à changer.
enum AppTab: String, CaseIterable, Identifiable, Hashable {
    case home
    case accounts
    case reports
    case settings

    var id: String { rawValue }

    var titleKey: LocalizedStringKey {
        switch self {
        case .home: return "tab.home"
        case .accounts: return "tab.accounts"
        case .reports: return "tab.reports"
        case .settings: return "tab.settings"
        }
    }

    /// Symboles SF Symbols (natifs, s'adaptent automatiquement à la taille de texte et au thème).
    var systemImage: String {
        switch self {
        case .home: return "house.fill"
        case .accounts: return "creditcard.fill"
        case .reports: return "chart.pie.fill"
        case .settings: return "gearshape.fill"
        }
    }

    @ViewBuilder
    var rootView: some View {
        switch self {
        case .home: DashboardView()
        case .accounts: AccountsView()
        case .reports: ReportsView()
        case .settings: SettingsView()
        }
    }
}
