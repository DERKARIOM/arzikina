import SwiftUI

/// Carte arrondie standard (surface système secondaire) — à utiliser pour toute section de
/// contenu hors `List`/`Form`, afin de garder un rendu homogène d'un écran à l'autre.
struct CardModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                Color(.secondarySystemGroupedBackground),
                in: RoundedRectangle(cornerRadius: Brand.Radius.card, style: .continuous)
            )
    }
}

extension View {
    func arzikinaCard() -> some View {
        modifier(CardModifier())
    }
}
