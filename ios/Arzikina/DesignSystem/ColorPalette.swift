/// Couleurs proposées pour les comptes et les catégories (et plus tard les planifications) — mêmes valeurs
/// qu'Android `ColorPalette.COLORS`, pour qu'un compte ait la même couleur sur tous les appareils.
enum ColorPalette {
    static let colors: [Int64] = [
        0xFF10_B981, // émeraude (par défaut)
        0xFFEF_4444, // rouge
        0xFFF5_9E0B, // ambre
        0xFF3B_82F6, // bleu
        0xFF8B_5CF6, // violet
        0xFFEC_4899, // rose
        0xFF14_B8A6, // turquoise
        0xFF63_66F1, // indigo
        0xFF84_CC16, // vert lime
        0xFF64_748B  // gris ardoise
    ]

    /// Palette, plus [current] si elle n'en fait pas partie (couleur choisie sur un autre appareil
    /// ou couleur d'un élément par défaut) : elle reste sélectionnée et conservée.
    static func choices(including current: Int64) -> [Int64] {
        colors.contains(current) ? colors : colors + [current]
    }
}
