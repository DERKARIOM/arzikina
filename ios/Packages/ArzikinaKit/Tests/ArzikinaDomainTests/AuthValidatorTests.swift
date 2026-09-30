import Foundation
import XCTest
@testable import ArzikinaDomain

/// Règles de formulaire propres à iOS (ordre et champ des erreurs) ; les règles de format
/// elles-mêmes sont vérifiées par les fixtures partagées (`auth-validation.json`).
final class AuthValidatorTests: XCTestCase {

    func testLoginValidation() {
        XCTAssertEqual(AuthValidator.validateLogin(identifier: " ", password: "x"), .requiredFieldMissing(.identifier))
        XCTAssertEqual(AuthValidator.validateLogin(identifier: "awa", password: ""), .requiredFieldMissing(.password))
        XCTAssertNil(AuthValidator.validateLogin(identifier: "awa", password: "x"))
    }

    func testEmptyRegistrationReportsEveryRequiredField() {
        let issues = AuthValidator.validate(RegistrationForm())
        XCTAssertEqual(issues.map(\.field), [.fullName, .username, .email, .password, .passwordConfirmation, .securityAnswer])
    }

    func testRegistrationIssuesInFormOrder() {
        let form = RegistrationForm(
            fullName: "Awa",
            username: "a",
            email: "pas-un-email",
            password: "court",
            passwordConfirmation: "different",
            securityAnswer: "x"
        )
        XCTAssertEqual(AuthValidator.validate(form), [.invalidUsername, .invalidEmail, .passwordTooShort, .passwordsDoNotMatch, .securityAnswerTooShort])
    }

    func testValidRegistration() {
        let form = RegistrationForm(
            fullName: "Awa Diallo",
            username: "awa.diallo",
            email: "awa@example.com",
            password: "motdepasse",
            passwordConfirmation: "motdepasse",
            securityAnswer: "Niamey"
        )
        XCTAssertTrue(AuthValidator.validate(form).isEmpty)
    }

    func testSessionExpiry() {
        let session = AuthSession(userId: "u", fullName: "Awa", expiresAt: 1_000)
        XCTAssertFalse(session.isExpired(now: 999))
        XCTAssertTrue(session.isExpired(now: 1_000))
    }

    /// Contrat avec le serveur (`register.php`) et Android (`SecurityQuestion`).
    func testSecurityQuestionRawValues() {
        XCTAssertEqual(SecurityQuestion.allCases.map(\.rawValue),
                       ["FIRST_PET_NAME", "BIRTH_CITY", "MOTHER_MAIDEN_NAME", "FAVORITE_TEACHER", "CHILDHOOD_BEST_FRIEND"])
    }
}
