import SwiftUI

/// Point d'entrée de l'application iOS Arzikina.
///
/// Étape 1 (« bootstrap ») : uniquement la structure de navigation, le thème et la localisation.
/// La logique métier, le réseau, le stockage local et la synchronisation arriveront dans les étapes
/// suivantes (voir `docs/IOS_BUILD.md` et le document d'architecture iOS), sans changer cette base.
@main
struct ArzikinaApp: App {

    /// Préférence d'apparence NON sensible → `@AppStorage` (UserDefaults), jamais le Keychain.
    @AppStorage(AppearancePreference.storageKey)
    private var appearance: AppearancePreference = .default

    var body: some Scene {
        WindowGroup {
            MainTabView()
                .tint(Brand.primary)
                .preferredColorScheme(appearance.colorScheme)
        }
    }
}
