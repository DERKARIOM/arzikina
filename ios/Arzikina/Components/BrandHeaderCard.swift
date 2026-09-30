import SwiftUI

/// Carte d'en-tête du tableau de bord : logo, nom et accroche Arzikina, puis l'emplacement du
/// solde total. Tant que la synchronisation n'existe pas, le solde affiche un tiret et un message
/// explicatif — jamais un montant inventé.
struct BrandHeaderCard: View {

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var isVisible = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 12) {
                Image("BrandLogo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 40, height: 40)
                    .padding(8)
                    .background(.white, in: RoundedRectangle(cornerRadius: Brand.Radius.icon, style: .continuous))
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: "Arzikina")
                        .font(.title2.bold())
                    Text("brand.tagline")
                        .font(.subheadline)
                        .opacity(0.9)
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("dashboard.total_balance")
                    .font(.footnote.weight(.medium))
                    .opacity(0.85)
                Text(verbatim: "—")
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .accessibilityLabel(Text("dashboard.balance_unavailable"))
                Text("dashboard.sync_pending")
                    .font(.footnote)
                    .opacity(0.85)
            }
        }
        .foregroundStyle(.white)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Brand.heroGradient, in: RoundedRectangle(cornerRadius: Brand.Radius.hero, style: .continuous))
        // Apparition discrète (fondu + léger glissement), désactivée si « Réduire les animations ».
        .opacity(isVisible ? 1 : 0)
        .offset(y: isVisible || reduceMotion ? 0 : 8)
        .onAppear {
            withAnimation(reduceMotion ? nil : .easeOut(duration: 0.35)) {
                isVisible = true
            }
        }
    }
}

#Preview {
    BrandHeaderCard()
        .padding()
}
