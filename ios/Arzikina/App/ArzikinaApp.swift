import SwiftUI

/// Point d'entrée de l'application iOS Arzikina.
///
/// Construit une seule fois les dépendances réelles (`AppContainer.live()`) et l'état de session
/// partagé, puis délègue l'affichage à `RootView` (connexion ou application principale).
@main
struct ArzikinaApp: App {

    /// Préférence d'apparence NON sensible → `@AppStorage` (UserDefaults), jamais le Keychain.
    @AppStorage(AppearancePreference.storageKey)
    private var appearance: AppearancePreference = .default

    @State private var session: SessionModel
    @Environment(\.scenePhase) private var scenePhase

    init() {
        let container = AppContainer.live()
        _session = State(initialValue: SessionModel(
            authRepository: container.authRepository,
            openDataSpace: container.openDataSpace(for:),
            makeSyncEngine: container.makeSyncEngine(for:)
        ))
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
                .tint(Brand.primary)
                .preferredColorScheme(appearance.colorScheme)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { session.appDidBecomeActive() }
        }
    }
}
