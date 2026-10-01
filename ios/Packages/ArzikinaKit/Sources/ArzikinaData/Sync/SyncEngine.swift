import ArzikinaDomain
import Foundation

/// Échec d'une synchronisation, tel que l'interface doit le présenter.
public enum SyncError: Error, Equatable, Sendable {
    /// Aucune session (déconnecté entre-temps).
    case notSignedIn
    /// Le serveur refuse le jeton (expiré ou révoqué) : il faut se reconnecter. Les données
    /// locales et les modifications en attente sont conservées.
    case sessionExpired
    /// Réseau indisponible ou serveur injoignable : réessai automatique plus tard.
    case offline
    /// Erreur du serveur (HTTP [status]) ou réponse inattendue (`status == nil`).
    case server(status: Int?)
}

/// Bilan d'une synchronisation réussie.
public struct SyncReport: Equatable, Sendable {
    /// Modifications locales acceptées par le serveur.
    public var pushed = 0
    /// Entités reçues du serveur et enregistrées.
    public var pulled = 0
    /// Modifications refusées par le serveur, conservées pour un prochain essai.
    public var failed = 0

    public init(pushed: Int = 0, pulled: Int = 0, failed: Int = 0) {
        self.pushed = pushed
        self.pulled = pulled
        self.failed = failed
    }
}

/// Moteur de synchronisation d'un espace de données avec l'API Arzikina (`push.php`/`pull.php`),
/// selon le même contrat qu'Android et le Web — sans aucune modification du serveur.
///
/// Déroulement : d'abord ENVOYER les modifications locales en attente (pour que le serveur les
/// arbitre), puis RECEVOIR tout ce qui a changé sur le serveur depuis le dernier passage.
///
/// Les conflits sont résolus par le serveur (dernière écriture arrivée gagne, conflit journalisé) :
/// le moteur adopte toujours l'état final renvoyé par le serveur.
///
/// `actor` : une seule synchronisation à la fois ; un appel pendant une synchronisation en cours
/// attend simplement son résultat au lieu d'en lancer une seconde.
public actor SyncEngine {

    /// Opérations par requête `push.php` (requêtes courtes, adaptées aux réseaux mobiles).
    static let pushBatchSize = 100
    /// Garde-fou contre une boucle de pages infinie (500 × 2 000 = 1 million de lignes par type).
    static let maxPullPages = 2_000

    private let store: SyncStore
    private let remote: SyncRemote
    private let accessToken: @Sendable () -> String?
    private let now: Clock
    private var running: Task<SyncReport, Error>?

    /// Moteur adossé à l'API réelle.
    public init(space: UserDataSpace, api: APIClient, accessToken: @escaping @Sendable () -> String?, now: @escaping Clock = Clocks.system) {
        self.init(space: space, remote: RemoteSyncAPI(api: api), accessToken: accessToken, now: now)
    }

    init(space: UserDataSpace, remote: SyncRemote, accessToken: @escaping @Sendable () -> String?, now: @escaping Clock) {
        self.store = SyncStore(database: space.database)
        self.remote = remote
        self.accessToken = accessToken
        self.now = now
    }

    /// Synchronise maintenant (ou rejoint la synchronisation déjà en cours).
    public func synchronize() async throws -> SyncReport {
        if let running {
            return try await running.value
        }
        let task = Task { try await self.run() }
        running = task
        defer { running = nil }
        return try await task.value
    }

    /// Date (horloge de l'appareil) de la dernière synchronisation réussie, en continu.
    public nonisolated func observeLastSuccessfulSync() -> AsyncStream<EpochMillis?> {
        store.observeLastSuccessfulSync()
    }

    // MARK: - Déroulement

    private func run() async throws -> SyncReport {
        guard let token = accessToken() else { throw SyncError.notSignedIn }
        var report = SyncReport()
        do {
            for schema in SyncEntitySchema.all {
                try await push(schema, token: token, into: &report)
            }
            for schema in SyncEntitySchema.all {
                report.pulled += try await pull(schema, token: token)
            }
        } catch let error as APIError {
            throw Self.syncError(from: error)
        }
        try await store.setLastSuccessfulSync(now())
        return report
    }

    // MARK: Envoi

    private func push(_ schema: SyncEntitySchema, token: String, into report: inout SyncReport) async throws {
        // Deux passes au plus : la seconde renvoie comme créations les modifications que le
        // serveur ne connaissait pas (`not_found`, voir `SyncStore.PushOutcome.resendAsCreate`).
        for _ in 0..<2 {
            let changes = try await store.outgoingChanges(for: schema)
            guard !changes.isEmpty else { return }
            var needsResend = false

            for start in stride(from: 0, to: changes.count, by: Self.pushBatchSize) {
                let batch = Array(changes[start..<min(start + Self.pushBatchSize, changes.count)])
                let response = try await remote.push(
                    schema.type,
                    operations: batch.map { PushOperation(operation: $0.operation, entity: $0.payload) },
                    token: token
                )
                // Les résultats arrivent dans l'ordre des opérations envoyées.
                guard response.results.count == batch.count else { throw APIError.invalidResponse }

                for (change, result) in zip(batch, response.results) {
                    let outcome = Self.outcome(of: result, for: change.operation)
                    switch outcome {
                    case .applied: report.pushed += 1
                    case .resendAsCreate: needsResend = true
                    case .dropped: break
                    case .failed: report.failed += 1
                    }
                    try await store.apply(outcome, to: change, schema: schema, now: now())
                }
            }
            if !needsResend { return }
        }
    }

    static func outcome(of result: PushResult, for operation: SyncOperation) -> SyncStore.PushOutcome {
        switch result.status {
        case .accepted, .conflictResolved:
            guard let entity = result.serverEntity else { return .failed(code: "missing_server_entity") }
            return .applied(serverEntity: entity)
        case .error:
            guard result.errorCode == "not_found" else { return .failed(code: result.errorCode ?? "unknown") }
            return operation == .delete ? .dropped : .resendAsCreate
        }
    }

    // MARK: Réception

    /// Reçoit toutes les pages de [schema] depuis le dernier curseur.
    ///
    /// PAGINATION — `pull.php` renvoie au plus 500 lignes triées par `updated_at` et un
    /// `serverTime` capturé AVANT sa requête. Reprendre à `serverTime` après une page PLEINE
    /// sauterait les lignes restantes (toutes antérieures à `serverTime`). Le moteur reprend donc :
    /// - page pleine → au `updatedAt` de la DERNIÈRE ligne reçue, moins 1 ms : les lignes de cette
    ///   même milliseconde non encore reçues seront incluses (celles déjà reçues reviendront une
    ///   seconde fois, sans effet : l'enregistrement est idempotent) ;
    /// - page incomplète → à `serverTime` : tout ce qui existait avant la requête a été reçu.
    /// Aucune modification du serveur n'est nécessaire.
    private func pull(_ schema: SyncEntitySchema, token: String) async throws -> Int {
        var cursor = try await store.cursor(for: schema.type)
        var received = 0
        for _ in 0..<Self.maxPullPages {
            let page = try await remote.pull(schema.type, updatedAfter: cursor, token: token)
            let isFull = page.entities.count >= RemoteSyncAPI.pullBatchLimit
            let next = Self.nextCursor(after: page, current: cursor, isFull: isFull)
            received += try await store.applyPulled(page.entities, schema: schema, newCursor: next)
            cursor = next
            if !isFull { break }
        }
        return received
    }

    static func nextCursor(after page: PullPage, current: Int64, isFull: Bool) -> Int64 {
        guard isFull else { return max(current, page.serverTime) }
        let lastUpdatedAt = page.entities.compactMap { $0["updatedAt"]?.int64Value }.max() ?? current
        // Cas extrême : 500 lignes ou plus partagent la même milliseconde. Reculer d'1 ms ne
        // ferait plus progresser le curseur (boucle infinie) : on avance quand même.
        return lastUpdatedAt - 1 > current ? lastUpdatedAt - 1 : max(current + 1, lastUpdatedAt)
    }

    // MARK: Erreurs

    static func syncError(from error: APIError) -> SyncError {
        switch error {
        case .transport: return .offline
        case .http(let status, _) where status == 401: return .sessionExpired
        case .http(let status, _): return .server(status: status)
        case .invalidResponse: return .server(status: nil)
        }
    }
}
