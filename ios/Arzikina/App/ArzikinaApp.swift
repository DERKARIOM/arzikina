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
    @State private var appLock: AppLockModel
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
        _appLock = State(initialValue: container.makeAppLock())
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
                .overlay { privacyCover }
                .environment(session)
                .environment(router)
                .environment(appLock)
                .tint(Brand.primary)
                .preferredColorScheme(appearance.colorScheme)
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .active:
                appLock.appBecameActive()
                session.appDidBecomeActive()
            case .background:
                appLock.appEnteredBackground()
            default:
                break
            }
        }
    }

    /// Masque les montants dès que la scène n'est plus active (sélecteur d'apps, Centre de
    /// contrôle, appel entrant) — sauf pendant Face ID, qui rend lui aussi la scène inactive.
    @ViewBuilder
    private var privacyCover: some View {
        if showsPrivacyCover {
            PrivacyCoverView()
        }
    }

    private var showsPrivacyCover: Bool {
        guard scenePhase != .active, !appLock.isAuthenticating, case .signedIn = session.state else { return false }
        return true
    }
}
