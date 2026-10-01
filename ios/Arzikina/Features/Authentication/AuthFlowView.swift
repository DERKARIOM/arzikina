import ArzikinaDomain
import SwiftUI

/// Parcours d'authentification : connexion, avec accès à l'inscription.
struct AuthFlowView: View {

    @State private var loginModel: LoginViewModel
    private let repository: AuthRepository
    private let onAuthenticated: (AuthSession) -> Void

    init(repository: AuthRepository, sessionExpired: Bool = false, onAuthenticated: @escaping (AuthSession) -> Void) {
        self.repository = repository
        self.onAuthenticated = onAuthenticated
        _loginModel = State(initialValue: LoginViewModel(repository: repository, sessionExpired: sessionExpired, onAuthenticated: onAuthenticated))
    }

    var body: some View {
        NavigationStack {
            LoginView(model: loginModel) {
                RegisterView(model: RegisterViewModel(repository: repository, onAuthenticated: onAuthenticated))
            }
        }
    }
}

/// En-tête de marque des écrans d'authentification.
struct AuthHeaderView: View {
    let titleKey: LocalizedStringKey
    let subtitleKey: LocalizedStringKey

    var body: some View {
        VStack(spacing: 12) {
            Image("BrandLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 64, height: 64)
                .padding(12)
                .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: Brand.Radius.hero, style: .continuous))
                .accessibilityHidden(true)
            Text(titleKey)
                .font(.title.bold())
                .multilineTextAlignment(.center)
            Text(subtitleKey)
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 24)
    }
}
