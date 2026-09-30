import Foundation

/// Règles de FORMAT de l'authentification — portage d'Android `util/AuthValidator.kt`, avec les
/// mêmes seuils que le serveur (`register.php`) : un nom ou un mot de passe refusé par l'un l'est
/// par tous. Vérifiées par `shared/test-fixtures/auth-validation.json`.
///
/// L'UNICITÉ (nom d'utilisateur ou e-mail déjà pris) ne peut être vérifiée que par le serveur.
public enum AuthValidator {

    public static let minUsernameLength = 3
    public static let maxUsernameLength = 30
    public static let minPasswordLength = 8
    public static let minSecurityAnswerLength = 2

    /// Volontairement permissif : rejette les erreurs de saisie évidentes, pas un RFC 5322 complet.
    public static func isValidEmail(_ email: String) -> Bool {
        matches(email.trimmingCharacters(in: .whitespacesAndNewlines), #"^[^\s@]+@[^\s@]+\.[^\s@]+$"#)
    }

    /// 3 à 30 caractères : lettres ASCII, chiffres, point ou underscore.
    public static func isValidUsername(_ username: String) -> Bool {
        let trimmed = username.trimmingCharacters(in: .whitespacesAndNewlines)
        return (minUsernameLength...maxUsernameLength).contains(trimmed.count)
            && matches(trimmed, #"^[a-zA-Z0-9._]+$"#)
    }

    /// Longueur comptée en unités UTF-16, exactement comme `String.length` côté Android (un emoji
    /// compte pour 2) : sans cela, un même mot de passe pourrait être accepté sur une plateforme et
    /// refusé sur l'autre.
    public static func isPasswordLongEnough(_ password: String) -> Bool {
        password.utf16.count >= minPasswordLength
    }

    public static func isSecurityAnswerLongEnough(_ answer: String) -> Bool {
        normalizeSecurityAnswer(answer).utf16.count >= minSecurityAnswerLength
    }

    /// Même normalisation qu'Android et le serveur (espaces aux extrémités + minuscules) : la
    /// réponse compte, pas sa mise en forme.
    public static func normalizeSecurityAnswer(_ answer: String) -> String {
        answer.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    // MARK: - Formulaires

    /// Premier problème de la connexion, `nil` si elle peut être envoyée.
    public static func validateLogin(identifier: String, password: String) -> AuthValidationIssue? {
        if isBlank(identifier) { return .requiredFieldMissing(.identifier) }
        if password.isEmpty { return .requiredFieldMissing(.password) }
        return nil
    }

    /// Tous les problèmes de l'inscription, dans l'ordre du formulaire (un par champ au plus).
    public static func validate(_ form: RegistrationForm) -> [AuthValidationIssue] {
        var issues: [AuthValidationIssue] = []
        if isBlank(form.fullName) { issues.append(.requiredFieldMissing(.fullName)) }

        if isBlank(form.username) {
            issues.append(.requiredFieldMissing(.username))
        } else if !isValidUsername(form.username) {
            issues.append(.invalidUsername)
        }

        if isBlank(form.email) {
            issues.append(.requiredFieldMissing(.email))
        } else if !isValidEmail(form.email) {
            issues.append(.invalidEmail)
        }

        if form.password.isEmpty {
            issues.append(.requiredFieldMissing(.password))
        } else if !isPasswordLongEnough(form.password) {
            issues.append(.passwordTooShort)
        }

        if form.passwordConfirmation.isEmpty {
            issues.append(.requiredFieldMissing(.passwordConfirmation))
        } else if form.password != form.passwordConfirmation {
            issues.append(.passwordsDoNotMatch)
        }

        if isBlank(form.securityAnswer) {
            issues.append(.requiredFieldMissing(.securityAnswer))
        } else if !isSecurityAnswerLongEnough(form.securityAnswer) {
            issues.append(.securityAnswerTooShort)
        }
        return issues
    }

    // MARK: - Interne

    private static func isBlank(_ text: String) -> Bool {
        text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private static func matches(_ text: String, _ pattern: String) -> Bool {
        text.range(of: pattern, options: .regularExpression) != nil
    }
}
