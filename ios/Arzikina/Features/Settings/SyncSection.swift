import ArzikinaData
import SwiftUI

/// « Synchronisation » : date de la dernière synchronisation réussie, état en cours ou dernière
/// erreur (en clair, sans jargon), et bouton « Synchroniser maintenant ».
struct SyncSection: View {

    let sync: SyncCoordinator

    var body: some View {
        Section {
            LabeledContent("settings.sync.last") {
                if let lastSuccess = sync.lastSuccess {
                    // « il y a 2 minutes », rafraîchi chaque minute tant que l'écran est affiché.
                    TimelineView(.periodic(from: .now, by: 60)) { _ in
                        Text(lastSuccess, format: .relative(presentation: .named))
                    }
                } else {
                    Text("settings.sync.never")
                }
            }

            Button {
                sync.requestSync(.manual)
            } label: {
                HStack {
                    // `LocalizedStringKey` explicite : un ternaire de littéraux serait un `String`,
                    // affiché tel quel au lieu d'être traduit.
                    Text(sync.isSyncing ? LocalizedStringKey("settings.sync.in_progress") : LocalizedStringKey("settings.sync.now"))
                    Spacer()
                    if sync.isSyncing {
                        ProgressView()
                    }
                }
            }
            .disabled(sync.isSyncing)
        } header: {
            Text("settings.section.sync")
        } footer: {
            footer
        }
    }

    @ViewBuilder
    private var footer: some View {
        if case .failed(let error) = sync.status {
            Label(Self.message(for: error), systemImage: "exclamationmark.triangle")
        } else if sync.rejectedChanges > 0 {
            Text("settings.sync.rejected \(sync.rejectedChanges)")
        } else {
            Text("settings.sync.footer")
        }
    }

    static func message(for error: SyncError) -> LocalizedStringKey {
        switch error {
        case .offline: return "settings.sync.error.offline"
        case .sessionExpired, .notSignedIn: return "settings.sync.error.session"
        case .server: return "settings.sync.error.server"
        }
    }
}
