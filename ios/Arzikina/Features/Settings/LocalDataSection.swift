import ArzikinaData
import SwiftUI

/// « Données sur cet iPhone » : espace occupé par la base locale, modifications pas encore
/// envoyées au serveur, et vidage des données locales (avec confirmation).
struct LocalDataSection: View {

    @Environment(SessionModel.self) private var session
    @State private var pendingChanges = 0
    @State private var sizeOnDisk: Int64 = 0
    @State private var isConfirmingClear = false
    @State private var clearFailed = false

    var body: some View {
        Section {
            LabeledContent("settings.local_data.size") {
                Text(verbatim: ByteCountFormatter.string(fromByteCount: sizeOnDisk, countStyle: .file))
            }
            LabeledContent("settings.local_data.pending") {
                Text(verbatim: "\(pendingChanges)")
            }
            Button("settings.local_data.clear", role: .destructive) {
                isConfirmingClear = true
            }
            // Jamais pendant une synchronisation : la base serait fermée en pleine écriture.
            .disabled(session.sync?.isSyncing == true)
            .confirmationDialog("settings.local_data.clear_confirm_title", isPresented: $isConfirmingClear, titleVisibility: .visible) {
                Button("settings.local_data.clear", role: .destructive, action: clear)
            } message: {
                if pendingChanges > 0 {
                    Text("settings.local_data.clear_confirm_pending \(pendingChanges)")
                } else {
                    Text("settings.local_data.clear_confirm_message")
                }
            }
        } header: {
            Text("settings.section.local_data")
        } footer: {
            if !session.isDataPersistent {
                Text("settings.local_data.temporary")
            } else if clearFailed {
                Text("settings.local_data.clear_failed")
            }
        }
        // Relancé à chaque nouvel espace (connexion, vidage — même utilisateur mais nouvelle base) :
        // suit le compteur en continu.
        .task(id: session.dataSpace.map(ObjectIdentifier.init)) {
            guard let space = session.dataSpace else { return }
            sizeOnDisk = space.sizeOnDisk()
            for await count in space.observePendingChangesCount() {
                pendingChanges = count
                sizeOnDisk = space.sizeOnDisk()
            }
        }
    }

    private func clear() {
        do {
            try session.clearLocalData()
            clearFailed = false
            pendingChanges = 0
            sizeOnDisk = session.dataSpace?.sizeOnDisk() ?? 0
        } catch {
            clearFailed = true
        }
    }
}
