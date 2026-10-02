import SwiftUI
import UserNotifications

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
    @State private var router = AppRouter()
    @Environment(\.scenePhase) private var scenePhase
    /// Délégué du centre de notifications (référence forte : le système ne la garde pas).
    private let notificationResponder = NotificationResponder()

    init() {
        let container = AppContainer.live()
        let session = SessionModel(
            authRepository: container.authRepository,
            openDataSpace: container.openDataSpace(for:),
            makeSyncEngine: container.makeSyncEngine(for:),
            reminders: container.makeReminderScheduler()
        )
        let router = AppRouter()
        _session = State(initialValue: session)
        _router = State(initialValue: router)
        notificationResponder.onReminderDelivered = { session.automationReminderDelivered() }
        notificationResponder.onReminderOpened = { router.open(.automations) }
        // Avant la fin du lancement : un toucher qui ouvre l'app ne doit pas être perdu.
        UNUserNotificationCenter.current().delegate = notificationResponder
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
                .environment(router)
                .tint(Brand.primary)
                .preferredColorScheme(appearance.colorScheme)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { session.appDidBecomeActive() }
        }
    }
}
