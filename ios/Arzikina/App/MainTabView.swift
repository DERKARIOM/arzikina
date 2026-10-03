import SwiftUI

/// Navigation principale : un `TabView` dont chaque onglet possède sa propre `NavigationStack`
/// (chaque onglet garde ainsi son historique de navigation, comportement attendu sur iOS).
struct MainTabView: View {

    @State private var selection: AppTab = .home
    @Environment(AppRouter.self) private var router

    var body: some View {
        TabView(selection: $selection) {
            ForEach(AppTab.allCases) { tab in
                NavigationStack {
                    tab.rootView
                }
                .tabItem { Label(tab.titleKey, systemImage: tab.systemImage) }
                .tag(tab)
            }
        }
        // Les automatisations s'ouvrent depuis l'Accueil (voir `DashboardView`).
        .onChange(of: router.pendingDestination, initial: true) { _, destination in
            if destination == .automations { selection = .home }
        }
    }
}

#Preview {
    MainTabView()
        .tint(Brand.primary)
        .environment(SessionModel.preview())
        .environment(AppRouter())
        .environment(AppLockModel.preview())
}
