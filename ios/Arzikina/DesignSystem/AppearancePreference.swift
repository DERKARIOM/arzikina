import SwiftUI

/// Thème choisi dans Réglages — mêmes options qu'Android (`ThemeMode` : Système / Clair / Sombre),
/// avec le même défaut : **Sombre** tant que l'utilisateur n'a rien choisi.
///
/// Stocké dans UserDefaults (`@AppStorage`) : préférence d'affichage non sensible. Elle deviendra
/// synchronisable (`user_preferences.themeMode` côté API) avec la couche de synchronisation.
enum AppearancePreference: String, CaseIterable, Identifiable {
    case system
    case light
    case dark

    static let storageKey = "appearance"
    static let `default`: AppearancePreference = .dark

    var id: String { rawValue }

    /// `nil` = suivre le réglage de l'iPhone.
    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }

    var titleKey: LocalizedStringKey {
        switch self {
        case .system: return "settings.appearance.system"
        case .light: return "settings.appearance.light"
        case .dark: return "settings.appearance.dark"
        }
    }
}
