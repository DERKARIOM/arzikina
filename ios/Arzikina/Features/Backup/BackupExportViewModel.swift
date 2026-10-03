import ArzikinaDomain
import Foundation
import Observation

/// Export « Sauvegarder mes données » — Android `BackupViewModel` (export seulement, voir
/// `AndroidBackup`) : lit l'instantané, produit le fichier, puis la vue le confie au système
/// (Fichiers, iCloud Drive, AirDrop…) via `fileExporter`.
@MainActor
@Observable
final class BackupExportViewModel {

    /// Fichier prêt à être enregistré.
    struct ExportFile: Equatable {
        let data: Data
        let fileName: String
        let summary: BackupSummary
    }

    enum Outcome: Equatable {
        case saved(BackupSummary)
        case failed
    }

    private(set) var isPreparing = false
    /// Non nil : le sélecteur d'emplacement est affiché.
    var file: ExportFile?
    /// Résultat à annoncer (alerte), effacé une fois lu.
    var outcome: Outcome?

    /// Bilan du fichier en cours d'enregistrement, gardé à part : le système peut fermer le
    /// sélecteur (et vider [file]) avant d'annoncer le résultat.
    @ObservationIgnored private var pendingSummary: BackupSummary?
    @ObservationIgnored private let now: () -> EpochMillis
    @ObservationIgnored private let calendar: Calendar

    init(
        now: @escaping () -> EpochMillis = { EpochMillis((Date().timeIntervalSince1970 * 1000).rounded()) },
        calendar: Calendar = .current
    ) {
        self.now = now
        self.calendar = calendar
    }

    /// Prépare le fichier (lecture et encodage hors du fil principal grâce au dépôt asynchrone).
    func prepare(using repository: BackupRepository) async {
        guard !isPreparing, file == nil else { return }
        isPreparing = true
        defer { isPreparing = false }
        do {
            let snapshot = try await repository.snapshot()
            let exportedAt = now()
            let (document, summary) = AndroidBackup.make(snapshot, exportedAt: exportedAt)
            let data = try AndroidBackup.encode(document)
            pendingSummary = summary
            file = ExportFile(data: data, fileName: AndroidBackup.fileName(exportedAt: exportedAt, calendar: calendar), summary: summary)
        } catch {
            outcome = .failed
        }
    }

    /// Le fichier a été enregistré (ou l'enregistrement a échoué).
    func finished(success: Bool) {
        if let summary = pendingSummary {
            outcome = success ? .saved(summary) : .failed
        }
        pendingSummary = nil
        file = nil
    }

    /// Sélecteur fermé : sans enregistrement (annulation), rien à annoncer.
    func dismissed() {
        file = nil
    }

    func cancelled() {
        pendingSummary = nil
        file = nil
    }
}
