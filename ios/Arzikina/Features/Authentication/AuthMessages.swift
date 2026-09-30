import ArzikinaDomain
import SwiftUI

/// Textes affichés pour les erreurs d'authentification. Le domaine ne connaît que des cas typés ;
/// c'est ici, et seulement ici, qu'ils deviennent des phrases traduites.
enum AuthMessages {

    static func text(for error: AuthError) -> LocalizedStringKey {
        switch error {
        case .invalidCredentials: return "auth.error.invalid_credentials"
        case .usernameTaken: return "auth.error.username_taken"
        case .emailTaken: return "auth.error.email_taken"
        case .validation(let issue): return text(for: issue)
        case .networkUnavailable: return "auth.error.network"
        case .server: return "auth.error.server"
        }
    }

    static func text(for issue: AuthValidationIssue) -> LocalizedStringKey {
        switch issue {
        case .requiredFieldMissing: return "auth.error.required"
        case .invalidUsername: return "auth.error.invalid_username"
        case .invalidEmail: return "auth.error.invalid_email"
        case .passwordTooShort: return "auth.error.password_too_short"
        case .passwordsDoNotMatch: return "auth.error.passwords_mismatch"
        case .securityAnswerTooShort: return "auth.error.security_answer_too_short"
        }
    }
}

extension SecurityQuestion {
    /// Libellé traduit de la question.
    var titleKey: LocalizedStringKey {
        switch self {
        case .firstPetName: return "security_question.first_pet_name"
        case .birthCity: return "security_question.birth_city"
        case .motherMaidenName: return "security_question.mother_maiden_name"
        case .favoriteTeacher: return "security_question.favorite_teacher"
        case .childhoodBestFriend: return "security_question.childhood_best_friend"
        }
    }
}

/// Message d'erreur sous un champ ou un formulaire.
struct AuthErrorText: View {
    let message: LocalizedStringKey

    var body: some View {
        Label(message, systemImage: "exclamationmark.circle.fill")
            .font(.footnote)
            .foregroundStyle(.red)
            .accessibilityAddTraits(.isStaticText)
    }
}
