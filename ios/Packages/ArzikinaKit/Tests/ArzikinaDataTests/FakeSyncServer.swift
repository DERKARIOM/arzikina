import Foundation
@testable import ArzikinaData

/// Faux serveur en mémoire reproduisant FIDÈLEMENT la logique de `server/api/sync/push.php` et
/// `pull.php` (création idempotente, `not_found`, conflit par `baseVersion` résolu en faveur de
/// la dernière écriture arrivée, suppression douce, champ facultatif absent = conservé / `null` =
/// effacé, pages de 500 lignes triées par `updatedAt` avec `serverTime` capturé avant la lecture).
final class FakeSyncServer: SyncRemote, @unchecked Sendable {

    private let lock = NSLock()
    private var rows: [SyncEntityType: [String: [String: JSONValue]]] = [:]
    private var clock: Int64
    /// Avance de l'horloge serveur à chaque écriture (0 = plusieurs lignes à la même milliseconde).
    var tick: Int64 = 1

    /// Erreur à lever au prochain appel (puis effacée).
    var nextFailure: APIError?
    /// Exécuté pendant un push, AVANT la réponse : simule une modification faite par
    /// l'utilisateur pendant que la requête est en vol.
    var duringPush: (@Sendable () async throws -> Void)?

    private(set) var pushedBodies: [(SyncEntityType, [PushOperation])] = []
    private(set) var pullRequests: [(SyncEntityType, Int64)] = []

    init(clock: Int64 = 1_000_000) {
        self.clock = clock
    }

    // MARK: - Accès direct (préparation / vérification des tests)

    func row(_ type: SyncEntityType, _ id: String) -> [String: JSONValue]? {
        lock.lock(); defer { lock.unlock() }
        return rows[type]?[id]
    }

    func count(_ type: SyncEntityType) -> Int {
        lock.lock(); defer { lock.unlock() }
        return rows[type]?.count ?? 0
    }

    /// Écriture faite par un AUTRE appareil (passe par la même logique que `push.php`).
    @discardableResult
    func otherDevice(_ type: SyncEntityType, _ operation: SyncOperation, _ entity: [String: JSONValue]) -> PushResult {
        lock.lock(); defer { lock.unlock() }
        return apply(type, PushOperation(operation: operation, entity: entity))
    }

    // MARK: - SyncRemote

    func pull(_ type: SyncEntityType, updatedAfter: Int64, token: String) async throws -> PullPage {
        lock.lock(); defer { lock.unlock() }
        if let failure = nextFailure { nextFailure = nil; throw failure }
        pullRequests.append((type, updatedAfter))
        let serverTime = clock
        let page = (rows[type] ?? [:]).values
            .filter { ($0["updatedAt"]?.int64Value ?? 0) > updatedAfter }
            .sorted { ($0["updatedAt"]!.int64Value!, $0["id"]!.stringValue!) < ($1["updatedAt"]!.int64Value!, $1["id"]!.stringValue!) }
            .prefix(RemoteSyncAPI.pullBatchLimit)
        return PullPage(entities: Array(page), serverTime: serverTime)
    }

    func push(_ type: SyncEntityType, operations: [PushOperation], token: String) async throws -> PushResponse {
        try takeFailure()
        if let duringPush { try await duringPush() }
        lock.lock(); defer { lock.unlock() }
        pushedBodies.append((type, operations))
        let results = operations.map { apply(type, $0) }
        return PushResponse(results: results, serverTime: clock)
    }

    private func takeFailure() throws {
        lock.lock(); defer { lock.unlock() }
        if let failure = nextFailure { nextFailure = nil; throw failure }
    }

    // MARK: - Logique de push.php

    private func apply(_ type: SyncEntityType, _ op: PushOperation) -> PushResult {
        let schema = SyncEntitySchema.schema(for: type)
        let entity = op.entity
        let id = entity["id"]?.stringValue ?? UUID().uuidString.lowercased()
        clock += tick
        let now = clock
        var table = rows[type] ?? [:]
        defer { rows[type] = table }

        switch op.operation {
        case .create:
            if let existing = table[id] {
                return PushResult(status: .accepted, entityId: id, serverEntity: existing, errorCode: nil)
            }
            var row: [String: JSONValue] = ["id": .string(id), "userId": .string("u1")]
            for field in schema.fields {
                let value = entity[field.payload]
                if field.nullable {
                    row[field.payload] = (value == nil || value == .null) ? .null : value!
                } else {
                    row[field.payload] = value ?? Self.defaultValue(field.kind)
                }
            }
            row["createdAt"] = .int(entity["createdAt"]?.int64Value ?? now)
            row["updatedAt"] = .int(now)
            row["deletedAt"] = .null
            row["version"] = .int(1)
            table[id] = row
            return PushResult(status: .accepted, entityId: id, serverEntity: row, errorCode: nil)

        case .update, .delete:
            guard var current = table[id] else {
                return PushResult(status: .error, entityId: id, serverEntity: nil, errorCode: "not_found")
            }
            let currentVersion = current["version"]!.int64Value!
            let hasConflict = entity["baseVersion"].map { $0.int64Value != currentVersion } ?? false
            if hasConflict && current["updatedAt"]!.int64Value! >= now {
                return PushResult(status: .conflictResolved, entityId: id, serverEntity: current, errorCode: nil)
            }
            if op.operation == .delete {
                current["deletedAt"] = .int(now)
            } else {
                for field in schema.fields {
                    if field.nullable {
                        if let value = entity[field.payload] { current[field.payload] = value }
                    } else if let value = entity[field.payload] {
                        current[field.payload] = value
                    }
                }
                current["deletedAt"] = .null
            }
            current["updatedAt"] = .int(now)
            current["version"] = .int(currentVersion + 1)
            table[id] = current
            return PushResult(status: hasConflict ? .conflictResolved : .accepted, entityId: id, serverEntity: current, errorCode: nil)
        }
    }

    private static func defaultValue(_ kind: SyncEntitySchema.Kind) -> JSONValue {
        switch kind {
        case .text: return .string("")
        case .integer, .bool: return .int(0)
        case .real: return .double(0)
        }
    }
}
