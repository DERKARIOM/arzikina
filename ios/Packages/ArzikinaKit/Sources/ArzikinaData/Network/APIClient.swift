import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Adresse du serveur Arzikina — la même API qu'Android (`RemoteConfig.BASE_URL`) et le Web.
public struct APIConfiguration: Equatable, Sendable {
    /// Doit se terminer par `/` (les chemins d'endpoint n'en commencent jamais).
    public let baseURL: URL

    public init(baseURL: URL) {
        self.baseURL = baseURL
    }

    /// Serveur de production.
    public static let production = APIConfiguration(baseURL: URL(string: "https://api.opal-niger.com/")!)
}

/// Échec d'un appel à l'API, avant toute interprétation métier.
public enum APIError: Error, Equatable, Sendable {
    /// Pas de réseau, serveur injoignable, délai dépassé…
    case transport
    /// Réponse HTTP non 2xx. [code] est l'identifiant STABLE renvoyé par le serveur
    /// (`{"error": "username_taken", ...}`, voir `server/api/utils/json_response.php`), `nil` si
    /// le corps n'était pas le JSON attendu (ex. page d'erreur HTML d'un proxy).
    case http(status: Int, code: String?)
    /// Réponse 2xx dont le corps ne correspond pas au contrat attendu.
    case invalidResponse
}

/// Client JSON de l'API Arzikina : construit les requêtes, encode/décode le JSON (clés camelCase,
/// comme l'API) et normalise les erreurs en [APIError].
public struct APIClient: Sendable {

    private let configuration: APIConfiguration
    private let http: HTTPClient

    public init(configuration: APIConfiguration, http: HTTPClient) {
        self.configuration = configuration
        self.http = http
    }

    /// `POST <baseURL><path>` avec [body] en JSON ; décode la réponse en [Response].
    public func post<Body: Encodable, Response: Decodable>(
        _ path: String,
        body: Body,
        bearerToken: String? = nil,
        as responseType: Response.Type = Response.self
    ) async throws -> Response {
        var request = URLRequest(url: configuration.baseURL.appendingPathComponent(path))
        request.httpMethod = "POST"
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let bearerToken {
            request.setValue("Bearer \(bearerToken)", forHTTPHeaderField: "Authorization")
        }
        do {
            request.httpBody = try JSONEncoder().encode(body)
        } catch {
            throw APIError.invalidResponse
        }
        return try await send(request, as: responseType)
    }

    private func send<Response: Decodable>(_ request: URLRequest, as responseType: Response.Type) async throws -> Response {
        let data: Data
        let response: HTTPURLResponse
        do {
            (data, response) = try await http.send(request)
        } catch {
            throw APIError.transport
        }
        guard (200..<300).contains(response.statusCode) else {
            let payload = try? JSONDecoder().decode(ErrorPayload.self, from: data)
            throw APIError.http(status: response.statusCode, code: payload?.error)
        }
        do {
            return try JSONDecoder().decode(responseType, from: data)
        } catch {
            throw APIError.invalidResponse
        }
    }

    /// Corps d'erreur standard du serveur : `{"error": "<code>", "message": "..."}`. Le message,
    /// rédigé côté serveur en français, n'est jamais affiché tel quel (l'app traduit le code).
    private struct ErrorPayload: Decodable {
        let error: String
    }
}
