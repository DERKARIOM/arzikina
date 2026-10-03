import SwiftUI

extension DeviceAuthentication {
    var systemImage: String {
        switch self {
        case .faceID: return "faceid"
        case .touchID: return "touchid"
        case .opticID: return "opticid"
        case .passcode: return "lock.fill"
        }
    }

    /// « Face ID », « Touch ID »… (noms de marque, non traduits) ou « code ».
    var displayName: String {
        switch self {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        case .opticID: return "Optic ID"
        case .passcode: return DomainDisplay.localized("lock.method.passcode")
        }
    }
}

/// Écran de verrouillage, par-dessus une session ouverte — Android `BiometricLockFragment` :
/// la demande s'affiche d'elle-même ; « Déverrouiller » la relance ; « Se déconnecter » permet de
/// revenir au mot de passe (jamais de blocage total).
struct LockScreenView: View {

    @Environment(AppLockModel.self) private var lock
    @Environment(SessionModel.self) private var session
    @State private var isConfirmingLogout = false

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image("BrandLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 96, height: 96)
                .accessibilityHidden(true)
            VStack(spacing: 8) {
                Text("lock.title")
                    .font(.title2.weight(.bold))
                Text("lock.subtitle")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            Spacer()
            Button {
                unlock()
            } label: {
                Label(unlockTitle, systemImage: lock.method?.systemImage ?? "lock.open.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(lock.isAuthenticating)
            Button("lock.logout") { isConfirmingLogout = true }
                .font(.subheadline)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground).ignoresSafeArea())
        .task { unlock() }
        .confirmationDialog("settings.account.logout_confirm_title", isPresented: $isConfirmingLogout, titleVisibility: .visible) {
            Button("settings.account.logout", role: .destructive) {
                Task { await session.logout() }
            }
        } message: {
            Text("lock.logout.message")
        }
    }

    private var unlockTitle: String {
        String(format: DomainDisplay.localized("lock.unlock_with %@"), lock.method?.displayName ?? DomainDisplay.localized("lock.method.passcode"))
    }

    private func unlock() {
        Task { await lock.unlock(reason: DomainDisplay.localized("lock.reason")) }
    }
}

/// Cache de confidentialité : masque les montants dans le sélecteur d'apps et pendant un appel
/// entrant (scène inactive), comme `FLAG_SECURE` sur Android.
struct PrivacyCoverView: View {
    var body: some View {
        ZStack {
            Color("LaunchBackground").ignoresSafeArea()
            Image("BrandLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 120, height: 120)
                .accessibilityHidden(true)
        }
    }
}

#Preview {
    LockScreenView()
        .environment(AppLockModel.preview())
        .environment(SessionModel.preview())
}
