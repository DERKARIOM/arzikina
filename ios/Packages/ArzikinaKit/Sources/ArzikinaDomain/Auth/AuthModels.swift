/// Session authentifiée auprès du serveur Arzikina — ce que le reste de l'app a le droit de savoir.
/// Le token lui-même n'apparaît JAMAIS ici : il reste confiné à la couche données (Keychain).
public struct AuthSession: Equatable, Sendable {
    /// Identifiant (UUID) de l'utilisateur côté serveur.
    public let userId: EntityID
    public let fullName: String
    /// Expiration du token (horloge serveur). Au-delà, une nouvelle connexion est nécessaire.
    public let expiresAt: EpochMillis

    public init(userId: EntityID, fullName: String, expiresAt: EpochMillis) {
        self.userId = userId
        self.fullName = fullName
        self.expiresAt = expiresAt
    }

    public func isExpired(now: EpochMillis) -> Bool { now >= expiresAt }
}

/// Questions de sécurité proposées à l'inscription — liste FERMÉE identique à Android
/// (`SecurityQuestion`) et au serveur (`register.php`). Le texte affiché est localisé côté interface.
public enum SecurityQuestion: String, CaseIterable, Codable, Sendable {
    case firstPetName = "FIRST_PET_NAME"
    case birthCity = "BIRTH_CITY"
    case motherMaidenName = "MOTHER_MAIDEN_NAME"
    case favoriteTeacher = "FAVORITE_TEACHER"
    case childhoodBestFriend = "CHILDHOOD_BEST_FRIEND"
}

/// Données saisies à l'inscription.
public struct RegistrationForm: Equatable, Sendable {
    public var fullName: String
    public var username: String
    public var email: String
    /// Optionnel : vide = non renseigné.
    public var phoneNumber: String
    public var password: String
    public var passwordConfirmation: String
    public var securityQuestion: SecurityQuestion
    public var securityAnswer: String

    public init(
        fullName: String = "",
        username: String = "",
        email: String = "",
        phoneNumber: String = "",
        password: String = "",
        passwordConfirmation: String = "",
        securityQuestion: SecurityQuestion = .firstPetName,
        securityAnswer: String = ""
    ) {
        self.fullName = fullName
        self.username = username
        self.email = email
        self.phoneNumber = phoneNumber
        self.password = password
        self.passwordConfirmation = passwordConfirmation
        self.securityQuestion = securityQuestion
        self.securityAnswer = securityAnswer
    }
}

/// Raisons d'échec de l'authentification, sans aucun texte (la présentation les traduit).
public enum AuthError: Error, Equatable, Sendable {
    /// Identifiant ou mot de passe incorrect — jamais lequel des deux (anti-énumération).
    case invalidCredentials
    case usernameTaken
    case emailTaken
    case validation(AuthValidationIssue)
    /// Pas de connexion Internet (ou serveur injoignable).
    case networkUnavailable
    /// Le serveur a répondu une erreur inattendue (panne, réponse illisible…).
    case server
}

/// Problème de saisie détecté avant (ou par) le serveur.
public enum AuthValidationIssue: Equatable, Sendable {
    case requiredFieldMissing(AuthField)
    case invalidUsername
    case invalidEmail
    case passwordTooShort
    case passwordsDoNotMatch
    case securityAnswerTooShort
}

/// Champ de formulaire concerné par une erreur (pour l'afficher au bon endroit).
public enum AuthField: String, CaseIterable, Sendable {
    case identifier
    case fullName
    case username
    case email
    case password
    case passwordConfirmation
    case securityAnswer
}

extension AuthValidationIssue {
    /// Champ à mettre en évidence dans le formulaire.
    public var field: AuthField {
        switch self {
        case .requiredFieldMissing(let field): return field
        case .invalidUsername: return .username
        case .invalidEmail: return .email
        case .passwordTooShort: return .password
        case .passwordsDoNotMatch: return .passwordConfirmation
        case .securityAnswerTooShort: return .securityAnswer
        }
    }
}

/// Authentification auprès du serveur Arzikina (implémentée par la couche données).
public protocol AuthRepository: Sendable {
    /// Session enregistrée sur l'appareil et encore valide, sinon `nil` (une session expirée est
    /// effacée). Ne fait AUCUN appel réseau : permet d'ouvrir l'app hors ligne.
    func restoreSession() async -> AuthSession?

    /// Connexion par nom d'utilisateur OU e-mail. Lève une `AuthError`.
    func login(identifier: String, password: String) async throws -> AuthSession

    /// Création de compte (connecte immédiatement). Lève une `AuthError`.
    func register(_ form: RegistrationForm) async throws -> AuthSession

    /// Oublie la session de CET appareil.
    func logout() async
}
