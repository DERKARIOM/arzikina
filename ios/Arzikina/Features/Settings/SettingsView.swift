import SwiftUI
import UIKit

/// Onglet Réglages : compte (déconnexion), données locales, apparence (Système / Clair / Sombre),
/// langue et version.
///
/// Langue : l'app suit la langue de l'iPhone (français par défaut si elle n'est pas prise en
/// charge). Le choix manuel passe par le réglage de langue PAR APP d'iOS (Réglages › Arzikina ›
/// Langue), que `CFBundleLocalizations` rend disponible — mécanisme recommandé par Apple, qui
/// relance proprement l'app dans la langue choisie.
struct SettingsView: View {

    @AppStorage(AppearancePreference.storageKey)
    private var appearance: AppearancePreference = .default

    @Environment(\.openURL) private var openURL
    @Environment(SessionModel.self) private var session
    @State private var isConfirmingLogout = false

    var body: some View {
        Form {
            Section("settings.section.account") {
                if let current = session.currentSession, !current.fullName.isEmpty {
                    LabeledContent("settings.account.name") {
                        Text(verbatim: current.fullName)
                    }
                }
                Button("settings.account.logout", role: .destructive) {
                    isConfirmingLogout = true
                }
                .confirmationDialog("settings.account.logout_confirm_title", isPresented: $isConfirmingLogout, titleVisibility: .visible) {
                    Button("settings.account.logout", role: .destructive) {
                        Task { await session.logout() }
                    }
                } message: {
                    Text("settings.account.logout_confirm_message")
                }
            }

            if let sync = session.sync {
                SyncSection(sync: sync)
            }

            LocalDataSection()

            Section("settings.section.appearance") {
                Picker("settings.appearance.theme", selection: $appearance) {
                    ForEach(AppearancePreference.allCases) { option in
                        Text(option.titleKey).tag(option)
                    }
                }
            }

            Section {
                LabeledContent("settings.language.current") {
                    Text(verbatim: AppInfo.currentLanguageName)
                }
                Button("settings.language.change", action: openAppSettings)
            } header: {
                Text("settings.section.language")
            } footer: {
                Text("settings.language.footer")
            }

            Section("settings.section.about") {
                LabeledContent("settings.version") {
                    Text(verbatim: AppInfo.versionDescription)
                }
            }
        }
        .navigationTitle("tab.settings")
    }

    private func openAppSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        openURL(url)
    }
}

#Preview {
    NavigationStack { SettingsView() }
        .environment(SessionModel.preview())
}
