import ArzikinaDomain
import Observation

/// État et action de l'écran d'inscription.
///
/// Les erreurs de champ n'apparaissent qu'après une première tentative d'envoi (pas de rouge
/// pendant la saisie), puis se mettent à jour à chaque modification.
@MainActor
@Observable
final class RegisterViewModel {

    var form = RegistrationForm() {
        didSet { if hasAttemptedSubmit { refreshFieldIssues() } }
    }
    private(set) var isSubmitting = false
    /// Erreurs par champ (format local, ou « déjà pris » renvoyé par le serveur).
    private(set) var fieldIssues: [AuthField: AuthError] = [:]
    /// Erreur générale (réseau, serveur).
    private(set) var generalError: AuthError?

    @ObservationIgnored private var hasAttemptedSubmit = false
    @ObservationIgnored private let repository: AuthRepository
    @ObservationIgnored private let onAuthenticated: (AuthSession) -> Void

    init(repository: AuthRepository, onAuthenticated: @escaping (AuthSession) -> Void) {
        self.repository = repository
        self.onAuthenticated = onAuthenticated
    }

    func issue(for field: AuthField) -> AuthError? {
        fieldIssues[field]
    }

    func submit() async {
        guard !isSubmitting else { return }
        hasAttemptedSubmit = true
        generalError = nil
        refreshFieldIssues()
        guard fieldIssues.isEmpty else { return }

        isSubmitting = true
        defer { isSubmitting = false }
        do {
            let session = try await repository.register(form)
            onAuthenticated(session)
        } catch let error as AuthError {
            apply(error)
        } catch {
            generalError = .server
        }
    }

    // MARK: - Interne

    private func refreshFieldIssues() {
        var issues: [AuthField: AuthError] = [:]
        for issue in AuthValidator.validate(form) {
            issues[issue.field] = .validation(issue)
        }
        fieldIssues = issues
    }

    /// Place une erreur du serveur sous le champ concerné quand c'est possible.
    private func apply(_ error: AuthError) {
        switch error {
        case .usernameTaken:
            fieldIssues[.username] = error
        case .emailTaken:
            fieldIssues[.email] = error
        case .validation(let issue):
            fieldIssues[issue.field] = error
        case .invalidCredentials, .networkUnavailable, .server:
            generalError = error
        }
    }
}
