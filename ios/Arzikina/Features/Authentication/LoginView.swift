import ArzikinaDomain
import SwiftUI

/// Écran de connexion (nom d'utilisateur ou e-mail + mot de passe).
struct LoginView<RegisterDestination: View>: View {

    @Bindable var model: LoginViewModel
    @ViewBuilder let registerDestination: () -> RegisterDestination

    @FocusState private var focusedField: AuthField?

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                AuthHeaderView(titleKey: "login.title", subtitleKey: "login.subtitle")

                VStack(alignment: .leading, spacing: 12) {
                    TextField("login.identifier", text: $model.identifier)
                        .textContentType(.username)
                        .keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .submitLabel(.next)
                        .focused($focusedField, equals: .identifier)
                        .onSubmit { focusedField = .password }
                        .authFieldStyle(isInvalid: model.invalidField == .identifier)

                    SecureField("login.password", text: $model.password)
                        .textContentType(.password)
                        .submitLabel(.go)
                        .focused($focusedField, equals: .password)
                        .onSubmit(submit)
                        .authFieldStyle(isInvalid: model.invalidField == .password)

                    if let error = model.error {
                        AuthErrorText(message: AuthMessages.text(for: error))
                            .transition(.opacity)
                    }
                }

                Button(action: submit) {
                    ZStack {
                        Text("login.submit").opacity(model.isSubmitting ? 0 : 1)
                        if model.isSubmitting { ProgressView().tint(.white) }
                    }
                    .font(.headline)
                    .frame(maxWidth: .infinity, minHeight: 50)
                }
                .buttonStyle(.borderedProminent)
                .buttonBorderShape(.roundedRectangle(radius: Brand.Radius.icon))
                .disabled(model.isSubmitting)

                HStack(spacing: 4) {
                    Text("login.no_account")
                        .foregroundStyle(.secondary)
                    NavigationLink("login.create_account", destination: registerDestination)
                        .fontWeight(.semibold)
                }
                .font(.subheadline)
            }
            .padding(20)
            .animation(.easeInOut(duration: 0.2), value: model.error)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color(.systemGroupedBackground))
        .toolbar(.hidden, for: .navigationBar)
    }

    private func submit() {
        focusedField = nil
        Task { await model.submit() }
    }
}

private struct AuthFieldStyle: ViewModifier {
    let isInvalid: Bool

    func body(content: Content) -> some View {
        content
            .padding(.horizontal, 14)
            .frame(minHeight: 50)
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: Brand.Radius.icon, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Brand.Radius.icon, style: .continuous)
                    .stroke(isInvalid ? Color.red : Color.clear, lineWidth: 1)
            )
    }
}

private extension View {
    func authFieldStyle(isInvalid: Bool) -> some View {
        modifier(AuthFieldStyle(isInvalid: isInvalid))
    }
}
