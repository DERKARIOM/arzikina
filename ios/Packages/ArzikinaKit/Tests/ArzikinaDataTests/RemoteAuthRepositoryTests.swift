import ArzikinaDomain
import Foundation
import XCTest
@testable import ArzikinaData
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Transport simulé : renvoie une réponse prévue et mémorise les requêtes reçues.
final class StubHTTPClient: HTTPClient, @unchecked Sendable {
    enum Outcome {
        case response(status: Int, body: String)
        case transportFailure
    }

    private let lock = NSLock()
    private var outcome: Outcome
    private(set) var requests: [URLRequest] = []

    init(_ outcome: Outcome) {
        self.outcome = outcome
    }

    func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        lock.lock()
        requests.append(request)
        let outcome = self.outcome
        lock.unlock()
        switch outcome {
        case .transportFailure:
            throw URLError(.notConnectedToInternet)
        case .response(let status, let body):
            let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: "HTTP/1.1", headerFields: nil)!
            return (Data(body.utf8), response)
        }
    }

    /// Corps JSON de la dernière requête, décodé en dictionnaire.
    func lastBody() throws -> [String: Any] {
        let data = try XCTUnwrap(requests.last?.httpBody)
        return try XCTUnwrap(try JSONSerialization.jsonObject(with: data) as? [String: Any])
    }
}

final class RemoteAuthRepositoryTests: XCTestCase {

    private let now: EpochMillis = 1_790_000_000_000
    private let device = DeviceIdentity(id: "device-123", label: "iPhone (iOS 18.0)")
    private let successBody = #"{"token":"abc123","userId":"user-uuid","expiresAt":1800000000000,"fullName":"Awa Diallo"}"#

    private func makeRepository(_ http: StubHTTPClient, store: SessionStore = InMemorySessionStore()) -> RemoteAuthRepository {
        let api = APIClient(configuration: APIConfiguration(baseURL: URL(string: "https://api.example.test/")!), http: http)
        let fixedNow = now
        return RemoteAuthRepository(api: api, sessionStore: store, device: device, now: { fixedNow })
    }

    private func validForm() -> RegistrationForm {
        RegistrationForm(
            fullName: "  Awa Diallo ",
            username: "awa.diallo",
            email: "awa@example.com",
            phoneNumber: " ",
            password: "motdepasse",
            passwordConfirmation: "motdepasse",
            securityQuestion: .birthCity,
            securityAnswer: "Niamey"
        )
    }

    private func assertThrows(_ expected: AuthError, _ operation: () async throws -> Void, file: StaticString = #filePath, line: UInt = #line) async {
        do {
            try await operation()
            XCTFail("Erreur attendue : \(expected)", file: file, line: line)
        } catch let error as AuthError {
            XCTAssertEqual(error, expected, file: file, line: line)
        } catch {
            XCTFail("Erreur inattendue : \(error)", file: file, line: line)
        }
    }

    // MARK: - Connexion

    func testLoginSendsContractAndStoresSession() async throws {
        let http = StubHTTPClient(.response(status: 200, body: successBody))
        let store = InMemorySessionStore()
        let repository = makeRepository(http, store: store)

        let session = try await repository.login(identifier: "  awa@example.com ", password: "motdepasse")

        XCTAssertEqual(session, AuthSession(userId: "user-uuid", fullName: "Awa Diallo", expiresAt: 1_800_000_000_000))
        XCTAssertEqual(store.load()?.token, "abc123", "Le token est conservé (Keychain en production)")

        let request = try XCTUnwrap(http.requests.last)
        XCTAssertEqual(request.url?.absoluteString, "https://api.example.test/api/auth/login.php")
        XCTAssertEqual(request.httpMethod, "POST")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Content-Type"), "application/json; charset=utf-8")
        let body = try http.lastBody()
        XCTAssertEqual(body["identifier"] as? String, "awa@example.com", "Identifiant nettoyé des espaces")
        XCTAssertEqual(body["password"] as? String, "motdepasse", "Mot de passe envoyé tel quel")
        XCTAssertEqual(body["deviceId"] as? String, "device-123")
        XCTAssertEqual(body["deviceLabel"] as? String, "iPhone (iOS 18.0)")
    }

    func testLoginInvalidCredentials() async {
        let http = StubHTTPClient(.response(status: 401, body: #"{"error":"invalid_credentials","message":"Identifiant ou mot de passe incorrect."}"#))
        let store = InMemorySessionStore()
        let repository = makeRepository(http, store: store)
        await assertThrows(.invalidCredentials) { _ = try await repository.login(identifier: "awa", password: "faux") }
        XCTAssertNil(store.load(), "Aucune session enregistrée après un échec")
    }

    func testLoginOffline() async {
        let repository = makeRepository(StubHTTPClient(.transportFailure))
        await assertThrows(.networkUnavailable) { _ = try await repository.login(identifier: "awa", password: "motdepasse") }
    }

    func testLoginServerErrorWithHtmlBody() async {
        let repository = makeRepository(StubHTTPClient(.response(status: 500, body: "<html>Internal Server Error</html>")))
        await assertThrows(.server) { _ = try await repository.login(identifier: "awa", password: "motdepasse") }
    }

    func testLoginMalformedSuccessBody() async {
        let repository = makeRepository(StubHTTPClient(.response(status: 200, body: #"{"unexpected":true}"#)))
        await assertThrows(.server) { _ = try await repository.login(identifier: "awa", password: "motdepasse") }
    }

    func testLoginValidatesBeforeCallingServer() async {
        let http = StubHTTPClient(.response(status: 200, body: successBody))
        let repository = makeRepository(http)
        await assertThrows(.validation(.requiredFieldMissing(.identifier))) { _ = try await repository.login(identifier: "  ", password: "x") }
        await assertThrows(.validation(.requiredFieldMissing(.password))) { _ = try await repository.login(identifier: "awa", password: "") }
        XCTAssertTrue(http.requests.isEmpty, "Aucun appel réseau pour une saisie incomplète")
    }

    func testMissingFullNameDefaultsToEmpty() async throws {
        let repository = makeRepository(StubHTTPClient(.response(status: 200, body: #"{"token":"t","userId":"u","expiresAt":1800000000000}"#)))
        let session = try await repository.login(identifier: "awa", password: "motdepasse")
        XCTAssertEqual(session.fullName, "")
    }

    // MARK: - Inscription

    func testRegisterSendsTrimmedFieldsAndOmitsEmptyPhone() async throws {
        let http = StubHTTPClient(.response(status: 201, body: successBody))
        let repository = makeRepository(http)

        _ = try await repository.register(validForm())

        XCTAssertEqual(http.requests.last?.url?.absoluteString, "https://api.example.test/api/auth/register.php")
        let body = try http.lastBody()
        XCTAssertEqual(body["fullName"] as? String, "Awa Diallo")
        XCTAssertEqual(body["username"] as? String, "awa.diallo")
        XCTAssertEqual(body["email"] as? String, "awa@example.com")
        XCTAssertNil(body["phoneNumber"], "Téléphone vide non envoyé")
        XCTAssertEqual(body["securityQuestion"] as? String, "BIRTH_CITY")
        XCTAssertEqual(body["securityAnswer"] as? String, "Niamey", "Normalisée par le serveur, envoyée telle quelle")
        XCTAssertEqual(body["deviceId"] as? String, "device-123")
    }

    func testRegisterConflicts() async {
        let usernameTaken = makeRepository(StubHTTPClient(.response(status: 409, body: #"{"error":"username_taken","message":"..."}"#)))
        await assertThrows(.usernameTaken) { _ = try await usernameTaken.register(self.validForm()) }

        let emailTaken = makeRepository(StubHTTPClient(.response(status: 409, body: #"{"error":"email_taken","message":"..."}"#)))
        await assertThrows(.emailTaken) { _ = try await emailTaken.register(self.validForm()) }
    }

    func testRegisterServerValidationCodes() async {
        let cases: [(String, AuthError)] = [
            ("invalid_username", .validation(.invalidUsername)),
            ("invalid_email", .validation(.invalidEmail)),
            ("password_too_short", .validation(.passwordTooShort)),
            ("registration_failed", .server)
        ]
        for (code, expected) in cases {
            let repository = makeRepository(StubHTTPClient(.response(status: 400, body: #"{"error":"\#(code)","message":"..."}"#)))
            await assertThrows(expected) { _ = try await repository.register(self.validForm()) }
        }
    }

    func testRegisterValidatesLocallyFirst() async {
        let http = StubHTTPClient(.response(status: 201, body: successBody))
        let repository = makeRepository(http)
        var form = validForm()
        form.passwordConfirmation = "autre-chose"
        await assertThrows(.validation(.passwordsDoNotMatch)) { _ = try await repository.register(form) }
        XCTAssertTrue(http.requests.isEmpty)
    }

    // MARK: - Session

    func testRestoreValidSessionWithoutNetwork() async {
        let store = InMemorySessionStore(session: StoredSession(token: "t", userId: "u", fullName: "Awa", expiresAt: now + 1))
        let http = StubHTTPClient(.transportFailure)
        let repository = makeRepository(http, store: store)
        let session = await repository.restoreSession()
        XCTAssertEqual(session?.userId, "u")
        XCTAssertTrue(http.requests.isEmpty, "La restauration ne fait aucun appel réseau")
        XCTAssertEqual(repository.accessToken(), "t")
    }

    func testRestoreExpiredSessionClearsIt() async {
        let store = InMemorySessionStore(session: StoredSession(token: "t", userId: "u", fullName: "Awa", expiresAt: now))
        let repository = makeRepository(StubHTTPClient(.transportFailure), store: store)
        let session = await repository.restoreSession()
        XCTAssertNil(session)
        XCTAssertNil(store.load(), "Session expirée effacée")
        XCTAssertNil(repository.accessToken())
    }

    func testLogoutClearsSession() async {
        let store = InMemorySessionStore(session: StoredSession(token: "t", userId: "u", fullName: "Awa", expiresAt: now + 1))
        let repository = makeRepository(StubHTTPClient(.transportFailure), store: store)
        await repository.logout()
        XCTAssertNil(store.load())
        let session = await repository.restoreSession()
        XCTAssertNil(session)
    }
}
