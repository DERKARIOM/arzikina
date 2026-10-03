import SwiftUI

/// Réglages › Sécurité : verrouillage par Face ID / Touch ID / code — Android « Verrouillage
/// biométrique ». Activer ou désactiver demande une authentification.
struct SecuritySection: View {

    @Environment(AppLockModel.self) private var lock

    var body: some View {
        Section {
            if let method = lock.method {
                Toggle(isOn: Binding(get: { lock.isEnabled }, set: { enabled in
                    Task { await lock.setEnabled(enabled, reason: DomainDisplay.localized(enabled ? "lock.reason.enable" : "lock.reason.disable")) }
                })) {
                    Label {
                        Text(verbatim: String(format: DomainDisplay.localized("settings.security.lock %@"), method.displayName))
                    } icon: {
                        Image(systemName: method.systemImage)
                    }
                }
                .disabled(lock.isAuthenticating)
            } else {
                Label("settings.security.unavailable", systemImage: "lock.slash")
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text("settings.section.security")
        } footer: {
            Text("settings.security.footer")
        }
    }
}
