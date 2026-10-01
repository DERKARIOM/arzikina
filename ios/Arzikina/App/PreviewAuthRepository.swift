import ArzikinaData
import ArzikinaDomain

/// Dépôt factice pour les aperçus SwiftUI (Xcode sur Mac) : aucune requête réseau. Volontairement
/// compilé dans toutes les configurations : les `#Preview` le référencent, et ils sont compilés
/// aussi en Release (build CI).
struct PreviewAuthRepository: AuthRepository {
    var session = AuthSession(userId: "preview", fullName: "Awa Diallo", expiresAt: .max)

    func restoreSession() async -> AuthSession? { session }
    func login(identifier: String, password: String) async throws -> AuthSession { session }
    func register(_ form: RegistrationForm) async throws -> AuthSession { session }
    func logout() async {}
}

extension SessionModel {
    /// Session factice pour les aperçus SwiftUI : base en mémoire, aucun réseau.
    static func preview() -> SessionModel {
        SessionModel(authRepository: PreviewAuthRepository()) { session in
            (try! UserDataSpace.inMemory(userId: session.userId), true)
        }
    }
}
