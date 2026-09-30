import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Transport HTTP minimal, derrière un protocole pour que les tests remplacent le réseau par des
/// réponses prévues à l'avance (aucun appel réel pendant les tests).
public protocol HTTPClient: Sendable {
    /// Envoie [request] et renvoie le corps et la réponse HTTP, quel que soit le code de statut.
    /// Ne lève une erreur qu'en cas d'échec de TRANSPORT (pas de réseau, délai dépassé…).
    func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse)
}

/// Implémentation `URLSession` (seul client réseau de l'app, aucune dépendance externe).
public struct URLSessionHTTPClient: HTTPClient, @unchecked Sendable {

    private let session: URLSession

    public init(session: URLSession = URLSessionHTTPClient.makeDefaultSession()) {
        self.session = session
    }

    /// Délais courts et adaptés aux connexions mobiles instables, sans cache HTTP (les données
    /// financières ne doivent jamais être servies depuis un cache intermédiaire).
    public static func makeDefaultSession() -> URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 60
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.urlCache = nil
        return URLSession(configuration: configuration)
    }

    public func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        // `dataTask` + continuation plutôt que `data(for:)` : disponible à l'identique sur les
        // plateformes Apple et sous Linux (tests CI).
        try await withCheckedThrowingContinuation { continuation in
            let task = session.dataTask(with: request) { data, response, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let http = response as? HTTPURLResponse {
                    continuation.resume(returning: (data ?? Data(), http))
                } else {
                    continuation.resume(throwing: URLError(.badServerResponse))
                }
            }
            task.resume()
        }
    }
}
