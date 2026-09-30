import ArzikinaDomain
import Foundation

/// Identifie l'appareil auprès du serveur (table `auth_tokens` : `device_id`, `device_label`),
/// pour pouvoir un jour lister ou révoquer les sessions par appareil.
public struct DeviceIdentity: Equatable, Sendable {
    public let id: String
    public let label: String

    public init(id: String, label: String) {
        self.id = id
        self.label = label
    }
}

/// [AuthRepository] adossé à l'API Arzikina (`api/auth/login.php`, `api/auth/register.php`).
///
/// - Le mot de passe n'est envoyé qu'à la connexion ou à l'inscription (HTTPS), jamais stocké.
/// - Le token reçu est conservé dans [sessionStore] (Keychain en production).
/// - Aucune session hors ligne sans connexion préalable : l'app iOS n'a pas de profil local
///   historique (contrairement à Android).
public struct RemoteAuthRepository: AuthRepository {

    private let api: APIClient
    private let sessionStore: SessionStore
    private let device: DeviceIdentity
    private let now: @Sendable () -> EpochMillis

    public init(
        api: APIClient,
        sessionStore: SessionStore,
        device: DeviceIdentity,
        now: @escaping @Sendable () -> EpochMillis = { EpochMillis(Date().timeIntervalSince1970 * 1000) }
    ) {
        self.api = api
        self.sessionStore = sessionStore
        self.device = device
        self.now = now
    }

    public func restoreSession() async -> AuthSession? {
        guard let stored = sessionStore.load() else { return nil }
        let session = Self.session(from: stored)
        if session.isExpired(now: now()) {
            sessionStore.clear()
            return nil
        }
        return session
    }

    public func login(identifier: String, password: String) async throws -> AuthSession {
        if let issue = AuthValidator.validateLogin(identifier: identifier, password: password) {
            throw AuthError.validation(issue)
        }
        let request = LoginRequestDTO(
            identifier: identifier.trimmingCharacters(in: .whitespacesAndNewlines),
            password: password,
            deviceId: device.id,
            deviceLabel: device.label
        )
        return try await authenticate(path: "api/auth/login.php", body: request)
    }

    public func register(_ form: RegistrationForm) async throws -> AuthSession {
        if let issue = AuthValidator.validate(form).first {
            throw AuthError.validation(issue)
        }
        let phone = form.phoneNumber.trimmingCharacters(in: .whitespacesAndNewlines)
        let request = RegisterRequestDTO(
            fullName: form.fullName.trimmingCharacters(in: .whitespacesAndNewlines),
            username: form.username.trimmingCharacters(in: .whitespacesAndNewlines),
            email: form.email.trimmingCharacters(in: .whitespacesAndNewlines),
            phoneNumber: phone.isEmpty ? nil : phone,
            password: form.password,
            securityQuestion: form.securityQuestion.rawValue,
            securityAnswer: form.securityAnswer,
            deviceId: device.id,
            deviceLabel: device.label
        )
        return try await authenticate(path: "api/auth/register.php", body: request)
    }

    /// Supprime la session de cet appareil. Le serveur n'expose pas encore de route de
    /// déconnexion : le token reste techniquement valide jusqu'à son expiration, mais il n'existe
    /// plus nulle part sur l'appareil.
    public func logout() async {
        sessionStore.clear()
    }

    /// Token de la session courante, pour les appels authentifiés (synchronisation). `nil` si
    /// aucune session valide.
    public func accessToken() -> String? {
        guard let stored = sessionStore.load(), stored.expiresAt > now() else { return nil }
        return stored.token
    }

    // MARK: - Interne

    private func authenticate<Body: Encodable>(path: String, body: Body) async throws -> AuthSession {
        let response: AuthResponseDTO
        do {
            response = try await api.post(path, body: body, as: AuthResponseDTO.self)
        } catch let error as APIError {
            throw Self.authError(from: error)
        }
        guard !response.token.isEmpty, !response.userId.isEmpty else { throw AuthError.server }

        let stored = StoredSession(
            token: response.token,
            userId: response.userId,
            fullName: response.fullName ?? "",
            expiresAt: response.expiresAt
        )
        // Échec d'écriture Keychain (très rare) : la session reste utilisable pendant cette
        // ouverture de l'app ; une reconnexion sera simplement demandée au prochain lancement.
        try? sessionStore.save(stored)
        return Self.session(from: stored)
    }

    private static func session(from stored: StoredSession) -> AuthSession {
        AuthSession(userId: stored.userId, fullName: stored.fullName, expiresAt: stored.expiresAt)
    }

    /// Traduit les codes d'erreur STABLES du serveur (voir `login.php`, `register.php`).
    static func authError(from error: APIError) -> AuthError {
        switch error {
        case .transport:
            return .networkUnavailable
        case .invalidResponse:
            return .server
        case .http(let status, let code):
            switch code {
            case "invalid_credentials": return .invalidCredentials
            case "username_taken": return .usernameTaken
            case "email_taken": return .emailTaken
            case "invalid_username": return .validation(.invalidUsername)
            case "invalid_email": return .validation(.invalidEmail)
            case "password_too_short": return .validation(.passwordTooShort)
            default: return status == 401 ? .invalidCredentials : .server
            }
        }
    }
}
