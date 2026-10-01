import Foundation

/// Page renvoyée par `GET api/sync/pull.php`.
struct PullPage: Decodable, Sendable {
    let entities: [[String: JSONValue]]
    /// Horloge du serveur capturée AVANT sa requête SQL.
    let serverTime: Int64
}

/// Une opération envoyée à `POST api/sync/push.php`.
struct PushOperation: Encodable, Sendable {
    let operation: SyncOperation
    /// `id`, `baseVersion` et les champs de l'entité (clés de l'API).
    let entity: [String: JSONValue]
}

/// Résultat d'une opération de `push.php`, dans l'ordre des opérations envoyées.
struct PushResult: Decodable, Sendable {
    enum Status: String, Decodable, Sendable {
        case accepted
        case conflictResolved = "conflict_resolved"
        case error
    }

    let status: Status
    let entityId: String?
    /// État FINAL de l'entité sur le serveur (absent en cas d'erreur).
    let serverEntity: [String: JSONValue]?
    let errorCode: String?
}

struct PushResponse: Decodable, Sendable {
    let results: [PushResult]
    let serverTime: Int64
}

/// Accès aux deux endpoints de synchronisation, derrière un protocole : les tests du moteur
/// utilisent un faux serveur en mémoire, sans réseau.
protocol SyncRemote: Sendable {
    func pull(_ type: SyncEntityType, updatedAfter: Int64, token: String) async throws -> PullPage
    func push(_ type: SyncEntityType, operations: [PushOperation], token: String) async throws -> PushResponse
}

/// Implémentation réelle : l'API Arzikina existante, SANS aucune modification côté serveur.
struct RemoteSyncAPI: SyncRemote {

    let api: APIClient

    func pull(_ type: SyncEntityType, updatedAfter: Int64, token: String) async throws -> PullPage {
        try await api.get(
            "api/sync/pull.php",
            query: ["entity_type": type.rawValue, "updated_after": String(updatedAfter)],
            bearerToken: token
        )
    }

    func push(_ type: SyncEntityType, operations: [PushOperation], token: String) async throws -> PushResponse {
        try await api.post("api/sync/push.php", body: PushBody(entityType: type, operations: operations), bearerToken: token)
    }

    private struct PushBody: Encodable {
        let entityType: SyncEntityType
        let operations: [PushOperation]
    }
}
