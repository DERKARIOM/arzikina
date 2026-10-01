import SwiftUI

/// Aiguillage principal selon l'état de connexion (voir `SessionModel`).
struct RootView: View {

    @Environment(SessionModel.self) private var session
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
            }
        }
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.25), value: session.state)
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
