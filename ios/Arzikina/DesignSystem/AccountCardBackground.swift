import SwiftUI
import UIKit

/// Dégradé des cartes de compte, calculé à partir de la couleur du compte — même rendu
/// qu'Android `AccountCardGradient` : la couleur éclaircie (luminosité × 1,15) en haut à gauche,
/// assombrie (× 0,70) en bas à droite.
enum AccountCardBackground {

    static func gradient(argb: Int64) -> LinearGradient {
        let base = UIColor(Color(argb: argb))
        return LinearGradient(
            colors: [Color(adjusted(base, brightness: 1.15)), Color(adjusted(base, brightness: 0.70))],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    private static func adjusted(_ color: UIColor, brightness factor: CGFloat) -> UIColor {
        var hue: CGFloat = 0, saturation: CGFloat = 0, brightness: CGFloat = 0, alpha: CGFloat = 0
        guard color.getHue(&hue, saturation: &saturation, brightness: &brightness, alpha: &alpha) else { return color }
        return UIColor(hue: hue, saturation: saturation, brightness: min(max(brightness * factor, 0), 1), alpha: alpha)
    }
}
