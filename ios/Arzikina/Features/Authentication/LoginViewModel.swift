import ArzikinaDomain
import Observation

/// État et action de l'écran de connexion. Les règles (champs obligatoires, codes d'erreur du
/// serveur) vivent dans le domaine et la couche données ; ce ViewModel ne fait qu'orchestrer.
@MainActor
@Observable
final class LoginViewModel {

    var identifier = ""
    var password = ""
    private(set) var isSubmitting = false
    private(set) var error: AuthError?
    /// Explique à l'utilisateur pourquoi il doit se reconnecter (session expirée).
    private(set) var showsSessionExpiredNotice: Bool

    @ObservationIgnored private let repository: AuthRepository
    @ObservationIgnored private let onAuthenticated: (AuthSession) -> Void

    init(repository: AuthRepository, sessionExpired: Bool = false, onAuthenticated: @escaping (AuthSession) -> Void) {
        self.repository = repository
        self.showsSessionExpiredNotice = sessionExpired
        self.onAuthenticated = onAuthenticated
    }

    /// Champ à mettre en évidence (saisie incomplète).
    var invalidField: AuthField? {
        if case .validation(let issue) = error { return issue.field }
        return nil
    }

    func submit() async {
        guard !isSubmitting else { return }
        error = nil
        showsSessionExpiredNotice = false
        isSubmitting = true
        defer { isSubmitting = false }
        do {
            let session = try await repository.login(identifier: identifier, password: password)
            password = ""
            onAuthenticated(session)
        } catch let authError as AuthError {
            error = authError
        } catch {
            self.error = .server
        }
    }
}
