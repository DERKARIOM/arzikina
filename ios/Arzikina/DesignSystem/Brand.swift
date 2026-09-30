import SwiftUI

/// Identité visuelle Arzikina — seul point d'accès aux couleurs de marque dans le code.
///
/// Les valeurs vivent dans `Assets.xcassets` (variantes clair/sombre gérées par iOS) ; elles
/// reprennent exactement la palette Android (`res/values/colors.xml`) :
/// - `BrandPrimary`      #42B998 (arzikina_primary)
/// - `BrandPrimaryDeep`  #1F9A78 (bande de la carte du logo)
/// - `LaunchBackground`  #101720 en sombre (fond de l'icône), blanc en clair
///
/// Les surfaces (fonds, cartes, séparateurs) utilisent les couleurs SYSTÈME d'iOS
/// (`systemGroupedBackground`…) : elles suivent automatiquement le mode clair/sombre et les
/// réglages d'accessibilité (contraste augmenté), sans rien dupliquer.
enum Brand {
    static let primary = Color("BrandPrimary")
    static let primaryDeep = Color("BrandPrimaryDeep")

    /// Dégradé des cartes « héros » (solde, objectifs…).
    static let heroGradient = LinearGradient(
        colors: [primary, primaryDeep],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    /// Rayons d'arrondi partagés — continus (« squircle »), comme les composants iOS natifs.
    enum Radius {
        static let card: CGFloat = 20
        static let hero: CGFloat = 24
        static let icon: CGFloat = 14
    }
}
