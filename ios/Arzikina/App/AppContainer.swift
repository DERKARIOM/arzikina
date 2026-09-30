import ArzikinaData
import ArzikinaDomain
import Foundation
import UIKit

/// Racine de composition : SEUL endroit qui choisit les implémentations concrètes (API de
/// production, Keychain…). Les écrans et ViewModels ne reçoivent que des protocoles du domaine,
/// ce qui les garde indépendants du réseau et testables.
@MainActor
final class AppContainer {

    let authRepository: AuthRepository

    init(authRepository: AuthRepository) {
        self.authRepository = authRepository
    }

    /// Dépendances réelles de l'application.
    static func live() -> AppContainer {
        let sessionStore = KeychainSessionStore()
        clearKeychainAfterReinstall(sessionStore)
        let api = APIClient(configuration: apiConfiguration(), http: URLSessionHTTPClient())
        let repository = RemoteAuthRepository(api: api, sessionStore: sessionStore, device: deviceIdentity())
        return AppContainer(authRepository: repository)
    }

    // MARK: - Configuration

    /// URL de l'API lue dans l'Info.plist (`ArzikinaAPIBaseURL`, voir project.yml), sinon la
    /// production.
    private static func apiConfiguration() -> APIConfiguration {
        guard let value = Bundle.main.object(forInfoDictionaryKey: "ArzikinaAPIBaseURL") as? String,
              let url = URL(string: value)
        else { return .production }
        return APIConfiguration(baseURL: url)
    }

    /// Le Keychain survit à la suppression de l'app : sans ce nettoyage, une réinstallation
    /// rouvrirait silencieusement l'ancienne session. UserDefaults, lui, est effacé avec l'app.
    private static func clearKeychainAfterReinstall(_ store: SessionStore) {
        let key = "hasCompletedFirstLaunch"
        guard !UserDefaults.standard.bool(forKey: key) else { return }
        store.clear()
        UserDefaults.standard.set(true, forKey: key)
    }

    /// Identifiant propre à l'app sur cet iPhone (`identifierForVendor`) et libellé lisible,
    /// enregistrés côté serveur avec chaque session.
    private static func deviceIdentity() -> DeviceIdentity {
        let device = UIDevice.current
        let id = device.identifierForVendor?.uuidString ?? fallbackDeviceId()
        return DeviceIdentity(id: id, label: "\(device.model) (iOS \(device.systemVersion))")
    }

    /// Repli (rare) quand iOS ne fournit pas encore `identifierForVendor` : un UUID stable, non
    /// sensible, conservé dans UserDefaults.
    private static func fallbackDeviceId() -> String {
        let key = "fallbackDeviceId"
        if let existing = UserDefaults.standard.string(forKey: key) { return existing }
        let generated = UUID().uuidString
        UserDefaults.standard.set(generated, forKey: key)
        return generated
    }
}
