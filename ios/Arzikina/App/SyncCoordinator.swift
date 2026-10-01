import ArzikinaData
import Foundation
import Network
import Observation

/// Décide QUAND synchroniser l'espace de données de l'utilisateur connecté, et expose l'état de la
/// synchronisation à l'interface. Le COMMENT (envoi, réception, conflits) est entièrement dans
/// `SyncEngine` (ArzikinaKit), testé sans interface.
///
/// Déclencheurs :
/// - ouverture de l'espace (lancement de l'app avec session, connexion) ;
/// - retour de l'app au premier plan ;
/// - retour du réseau après une coupure (`NWPathMonitor`) ;
/// - bouton « Synchroniser maintenant » des Réglages.
///
/// Après chaque synchronisation (réussie ou non, l'app fonctionne hors ligne), [afterSync] crée les
/// échéances d'automatisation dues : APRÈS la réception, pour fusionner d'abord celles qu'un autre
/// appareil aurait déjà créées ; ce qui a été créé est aussitôt envoyé.
///
/// Les déclenchements AUTOMATIQUES rapprochés sont regroupés (au plus un toutes les
/// [automaticInterval] secondes) pour ménager la batterie et le forfait data ; le bouton manuel,
/// lui, synchronise toujours.
@MainActor
@Observable
final class SyncCoordinator {

    enum Status: Equatable {
        case idle
        case syncing
        case failed(SyncError)
    }

    private(set) var status: Status = .idle
    /// Dernière synchronisation réussie (persistée dans la base de l'utilisateur).
    private(set) var lastSuccess: Date?
    /// Modifications refusées par le serveur lors de la dernière synchronisation.
    private(set) var rejectedChanges = 0

    var isSyncing: Bool { status == .syncing }

    @ObservationIgnored private let engine: SyncEngine
    @ObservationIgnored private let onSessionExpired: @MainActor () -> Void
    @ObservationIgnored private let afterSync: @Sendable () async -> Int
    @ObservationIgnored private let automaticInterval: TimeInterval
    @ObservationIgnored private var lastAttempt: Date?
    @ObservationIgnored private var pathMonitor: NWPathMonitor?
    @ObservationIgnored private var observationTask: Task<Void, Never>?
    @ObservationIgnored private var wasOnline = true
    @ObservationIgnored private var needsFollowUpRun = false

    init(
        engine: SyncEngine,
        automaticInterval: TimeInterval = 30,
        afterSync: @escaping @Sendable () async -> Int = { 0 },
        onSessionExpired: @escaping @MainActor () -> Void
    ) {
        self.engine = engine
        self.automaticInterval = automaticInterval
        self.afterSync = afterSync
        self.onSessionExpired = onSessionExpired
    }

    /// Démarre le suivi (date de dernière synchro, réseau) et lance une première synchronisation.
    func start() {
        observationTask = Task { [weak self, engine] in
            for await millis in engine.observeLastSuccessfulSync() {
                self?.lastSuccess = millis.map { Date(timeIntervalSince1970: TimeInterval($0) / 1000) }
            }
        }
        startNetworkMonitoring()
        requestSync(.automatic)
    }

    /// Arrête tout suivi (déconnexion, vidage des données).
    func stop() {
        observationTask?.cancel()
        observationTask = nil
        pathMonitor?.cancel()
        pathMonitor = nil
    }

    enum Trigger {
        case automatic
        case manual
        /// L'utilisateur vient d'enregistrer une modification : envoi immédiat, sans attendre le
        /// délai entre deux synchronisations automatiques. Si une synchronisation est déjà en
        /// cours, une passe de plus est faite juste après (sinon la modification attendrait le
        /// prochain déclencheur).
        case localChange
    }

    func requestSync(_ trigger: Trigger) {
        guard !isSyncing else {
            if trigger == .localChange { needsFollowUpRun = true }
            return
        }
        if trigger == .automatic, let lastAttempt, Date().timeIntervalSince(lastAttempt) < automaticInterval {
            return
        }
        lastAttempt = Date()
        status = .syncing
        Task { await run() }
    }

    /// Synchronise et attend la fin (geste « tirer pour actualiser »). Si une synchronisation est
    /// déjà en cours, l'attend au lieu d'en lancer une seconde (voir `SyncEngine.synchronize`).
    func refresh() async {
        lastAttempt = Date()
        status = .syncing
        await run()
    }

    private func run() async {
        do {
            var report = try await engine.synchronize()
            while needsFollowUpRun {
                needsFollowUpRun = false
                report = try await engine.synchronize()
            }
            rejectedChanges = report.failed
            status = .idle
        } catch let error as SyncError {
            status = .failed(error)
            // Jeton refusé par le serveur, ou session expirée localement entre-temps.
            if error == .sessionExpired || error == .notSignedIn { onSessionExpired() }
        } catch {
            status = .failed(.server(status: nil))
        }
        // Échéances dues : créées même hors ligne ; envoyées tout de suite si le serveur répond.
        if await afterSync() > 0, status == .idle {
            _ = try? await engine.synchronize()
        }
    }

    private func startNetworkMonitoring() {
        let monitor = NWPathMonitor()
        monitor.pathUpdateHandler = { [weak self] path in
            let online = path.status == .satisfied
            Task { @MainActor in self?.networkChanged(online: online) }
        }
        monitor.start(queue: DispatchQueue(label: "com.naniger.arzikina.network"))
        pathMonitor = monitor
    }

    private func networkChanged(online: Bool) {
        defer { wasOnline = online }
        // Retour du réseau : on renvoie sans attendre ce qui a été saisi hors ligne.
        guard online, !wasOnline else { return }
        lastAttempt = nil
        requestSync(.automatic)
    }
}
