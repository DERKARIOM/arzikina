import SwiftUI

/// Aiguillage principal selon l'état de connexion (voir `SessionModel`).
struct RootView: View {

    @Environment(SessionModel.self) private var session
    @Environment(AppLockModel.self) private var lock
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Group {
            switch session.state {
            case .restoring:
                LaunchView()
            case .signedOut:
                AuthFlowView(
                    repository: session.authRepository,
                    sessionExpired: session.sessionExpired,
                    onAuthenticated: { session.didAuthenticate($0) },
                    onRegistered: { session.didRegister($0) }
                )
            case .signedIn:
                MainTabView()
                    .overlay {
                        // Par-dessus (et non à la place) de l'app : la navigation et les
                        // formulaires en cours sont retrouvés intacts après déverrouillage.
                        if lock.isLocked {
                            LockScreenView()
                                .transition(.opacity)
                        }
                    }
            }
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.25), value: session.state)
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.2), value: lock.isLocked)
        .onChange(of: session.state) { _, state in
            if state == .signedOut { lock.sessionEnded() }
        }
        .task { await session.restore() }
    }
}

/// Affiché le temps de relire la session (en pratique quelques millisecondes) : même fond et même
/// logo que l'écran de lancement iOS, pour une transition invisible.
private struct LaunchView: View {
    var body: some View {
        ZStack {
            Color("LaunchBackground").ignoresSafeArea()
            Image("BrandLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 170, height: 170)
                .accessibilityHidden(true)
        }
    }
}
