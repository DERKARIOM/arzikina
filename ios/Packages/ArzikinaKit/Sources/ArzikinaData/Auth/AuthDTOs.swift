/// Corps de `POST api/auth/login.php` (voir `server/api/auth/login.php`).
struct LoginRequestDTO: Encodable, Equatable {
    let identifier: String
    let password: String
    let deviceId: String
    let deviceLabel: String
}

/// Corps de `POST api/auth/register.php` (voir `server/api/auth/register.php`). Les champs `nil`
/// ne sont pas envoyés.
struct RegisterRequestDTO: Encodable, Equatable {
    let fullName: String
    let username: String
    let email: String
    let phoneNumber: String?
    let password: String
    let securityQuestion: String
    let securityAnswer: String
    let deviceId: String
    let deviceLabel: String
}

/// Réponse commune de `login.php` et `register.php`.
struct AuthResponseDTO: Decodable, Equatable {
    let token: String
    let userId: String
    /// Expiration du token, millisecondes epoch (horloge serveur).
    let expiresAt: Int64
    /// Peut manquer ou être vide pour un compte ancien (voir `login.php`).
    let fullName: String?
}
