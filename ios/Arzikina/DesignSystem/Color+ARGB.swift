import SwiftUI

extension Color {
    /// Couleur à partir d'un entier ARGB 32 bits, l'encodage utilisé par Android et l'API
    /// (`colorArgb` des comptes et catégories). Accepte la forme signée d'un `Int` Kotlin
    /// (ex. -12404328) comme la forme non signée ; une valeur sans canal alpha est opaque.
    init(argb: Int64) {
        let value = UInt32(truncatingIfNeeded: argb)
        let alpha = (value >> 24) & 0xFF
        self.init(
            .sRGB,
            red: Double((value >> 16) & 0xFF) / 255,
            green: Double((value >> 8) & 0xFF) / 255,
            blue: Double(value & 0xFF) / 255,
            opacity: alpha == 0 ? 1 : Double(alpha) / 255
        )
    }
}
