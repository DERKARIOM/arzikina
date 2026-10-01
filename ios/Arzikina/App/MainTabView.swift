import SwiftUI

/// Navigation principale : un `TabView` dont chaque onglet possède sa propre `NavigationStack`
/// (chaque onglet garde ainsi son historique de navigation, comportement attendu sur iOS).
struct MainTabView: View {

    @State private var selection: AppTab = .home

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
    }
}

#Preview {
    MainTabView()
        .tint(Brand.primary)
        .environment(SessionModel.preview())
}
