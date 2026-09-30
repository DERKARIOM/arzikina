import Foundation

/// Devises proposées dans les sélecteurs — même liste et mêmes symboles qu'Android
/// (`SupportedCurrency`). Un compte peut porter n'importe quel code ISO 4217 : cette liste n'est
/// qu'un ensemble d'options courantes, privilégiant l'Afrique de l'Ouest.
public enum SupportedCurrency: String, CaseIterable, Codable, Sendable {
    case xof = "XOF"
    case ngn = "NGN"
    case ghs = "GHS"
    case eur = "EUR"
    case usd = "USD"

    public var code: String { rawValue }

    public var symbol: String {
        switch self {
        case .xof: return "F CFA"
        case .ngn: return "₦"
        case .ghs: return "GH₵"
        case .eur: return "€"
        case .usd: return "$"
        }
    }

    /// Devise principale par défaut (`Constants.DEFAULT_CURRENCY_CODE` côté Android).
    public static let defaultCode = SupportedCurrency.xof.code
}

/// Thème de l'interface — mêmes valeurs qu'Android (`ThemeMode`) et que l'API
/// (`user_preferences.themeMode`). Sombre par défaut.
public enum ThemeMode: String, CaseIterable, Codable, Sendable {
    case system = "SYSTEM"
    case light = "LIGHT"
    case dark = "DARK"

    public static let `default`: ThemeMode = .dark
}

/// Langue de l'interface — même logique qu'Android (`AppLanguage` + `AppLanguageResolver`) :
/// seule la langue PRINCIPALE compte (fr-NE → français, en-NG → anglais) et toute langue non prise
/// en charge retombe sur le français.
public enum AppLanguage: String, CaseIterable, Sendable {
    case french = "fr"
    case english = "en"

    public static let `default`: AppLanguage = .french

    /// Langue prise en charge correspondant à [tag] (« fr-NE », « en_NG », « EN »…), sinon `nil`.
    public static func from(languageTag tag: String?) -> AppLanguage? {
        guard let tag else { return nil }
        let primary = tag
            .trimmingCharacters(in: .whitespaces)
            .split(whereSeparator: { $0 == "-" || $0 == "_" })
            .first
            .map { $0.lowercased() } ?? ""
        return AppLanguage(rawValue: primary)
    }

    /// Première langue prise en charge parmi [preferred] (ordre de préférence du système), sinon
    /// le français.
    public static func resolve(preferred: [String]) -> AppLanguage {
        preferred.lazy.compactMap { from(languageTag: $0) }.first ?? .default
    }
}

/// Préférences synchronisables d'un utilisateur (`user_preferences` côté API). Le verrou
/// biométrique n'en fait volontairement PAS partie : c'est un réglage propre à chaque appareil.
public struct UserPreferences: Equatable, Sendable {
    public var themeMode: ThemeMode
    public var currencyCode: String

    public init(themeMode: ThemeMode = .default, currencyCode: String = SupportedCurrency.defaultCode) {
        self.themeMode = themeMode
        self.currencyCode = currencyCode
    }
}
