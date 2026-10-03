import ArzikinaDomain
import SwiftUI
import UniformTypeIdentifiers

/// Réglages › Sauvegarde : exporter toutes ses données dans un fichier restaurable sur Android.
/// Pas de restauration sur iPhone : le serveur est la sauvegarde (voir `AndroidBackup`).
struct BackupSection: View {

    @Environment(SessionModel.self) private var session
    @State private var model = BackupExportViewModel()

    var body: some View {
        Section {
            Button {
                guard let space = session.dataSpace else { return }
                Task { await model.prepare(using: space.backup) }
            } label: {
                HStack {
                    Label("backup.export", systemImage: "square.and.arrow.up")
                    Spacer()
                    if model.isPreparing { ProgressView() }
                }
            }
            .disabled(model.isPreparing || session.dataSpace == nil)
        } header: {
            Text("settings.section.backup")
        } footer: {
            Text("backup.footer")
        }
        .modifier(BackupExportPresentation(model: model))
    }
}

/// Sélecteur d'emplacement et alertes de résultat (séparés pour un `body` court).
private struct BackupExportPresentation: ViewModifier {

    let model: BackupExportViewModel

    func body(content: Content) -> some View {
        content
            .fileExporter(
                isPresented: Binding(get: { model.file != nil }, set: { if !$0 { model.dismissed() } }),
                document: model.file.map { BackupJSONFile(data: $0.data) },
                contentType: .json,
                defaultFilename: model.file?.fileName
            ) { result in
                model.finished(success: (try? result.get()) != nil)
            } onCancellation: {
                model.cancelled()
            }
            .alert(alertTitle, isPresented: Binding(get: { model.outcome != nil }, set: { if !$0 { model.outcome = nil } })) {
                Button("common.ok", role: .cancel) {}
            } message: {
                Text(verbatim: alertMessage)
            }
    }

    private var alertTitle: LocalizedStringKey {
        model.outcome == .failed ? "backup.failed.title" : "backup.saved.title"
    }

    private var alertMessage: String {
        switch model.outcome {
        case .saved(let summary): return BackupSummaryText.message(summary)
        case .failed: return DomainDisplay.localized("backup.failed.message")
        case nil: return ""
        }
    }
}

/// Contenu du fichier pour `fileExporter`.
struct BackupJSONFile: FileDocument {
    static let readableContentTypes: [UTType] = [.json]

    let data: Data

    init(data: Data) {
        self.data = data
    }

    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

/// « 2 comptes · 412 transactions · 1 prêt » (comptes non nuls seulement).
enum BackupSummaryText {
    static func message(_ summary: BackupSummary) -> String {
        var parts: [String] = []
        if summary.accounts > 0 { parts.append(String(localized: "backup.count.accounts \(summary.accounts)")) }
        if summary.categories > 0 { parts.append(String(localized: "backup.count.categories \(summary.categories)")) }
        if summary.transactions > 0 { parts.append(String(localized: "backup.count.transactions \(summary.transactions)")) }
        if summary.budgets > 0 { parts.append(String(localized: "backup.count.budgets \(summary.budgets)")) }
        if summary.loans > 0 { parts.append(String(localized: "backup.count.loans \(summary.loans)")) }
        if summary.automations > 0 { parts.append(String(localized: "backup.count.automations \(summary.automations)")) }
        if summary.plans > 0 { parts.append(String(localized: "backup.count.plans \(summary.plans)")) }
        if summary.templates > 0 { parts.append(String(localized: "backup.count.templates \(summary.templates)")) }
        var message = parts.isEmpty ? DomainDisplay.localized("backup.saved.empty") : parts.joined(separator: " · ")
        if summary.skipped > 0 {
            message += "\n\n" + String(localized: "backup.saved.skipped \(summary.skipped)")
        }
        return message
    }
}
