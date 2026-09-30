import Foundation

/// Informations sur l'application installée, lues depuis l'Info.plist (elles-mêmes issues de
/// `project.yml` : MARKETING_VERSION / CURRENT_PROJECT_VERSION).
enum AppInfo {

    static var version: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "?"
    }

    static var build: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?"
    }

    /// Format iOS habituel : « 0.1.0 (1) ».
    static var versionDescription: String { "\(version) (\(build))" }

    /// Langue réellement utilisée par l'app (résolue par iOS parmi fr/en), nommée dans cette même
    /// langue : « Français » / « English ».
    static var currentLanguageName: String {
        let code = Bundle.main.preferredLocalizations.first ?? "fr"
        let locale = Locale(identifier: code)
        let name = locale.localizedString(forLanguageCode: code) ?? code
        return name.prefix(1).uppercased() + name.dropFirst()
    }
}
