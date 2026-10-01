import ArzikinaDomain
import SwiftUI

/// Carte d'en-tête du tableau de bord, aux couleurs de la marque : salutation, solde total et
/// bouton pour masquer le solde (utile en public).
///
/// Le solde est affiché PAR DEVISE (aucune conversion : les devises ne sont jamais additionnées) :
/// la première en grand, les autres en dessous. Sans solde à afficher, un tiret (et, s'il n'y a
/// encore aucun compte, un message explicatif) — jamais un montant inventé.
struct BrandHeaderCard: View {

    let firstName: String?
    let balances: [CurrencyAmount]
    /// Affiche « Vos comptes apparaîtront ici après la synchronisation » sous le tiret.
    let showsSyncHint: Bool
    @Binding var isBalanceHidden: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var isVisible = false

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            header
            balance
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

    private var header: some View {
        HStack(spacing: 12) {
            Image("BrandLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 32, height: 32)
                .padding(6)
                .background(.white, in: RoundedRectangle(cornerRadius: Brand.Radius.icon - 4, style: .continuous))
                .accessibilityHidden(true)

            Group {
                if let firstName, !firstName.isEmpty {
                    Text("dashboard.greeting_name \(firstName)")
                } else {
                    Text("dashboard.greeting")
                }
            }
            .font(.headline)

            Spacer()

            if !balances.isEmpty {
                Button {
                    withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.2)) {
                        isBalanceHidden.toggle()
                    }
                } label: {
                    Image(systemName: isBalanceHidden ? "eye.slash" : "eye")
                        .font(.body.weight(.semibold))
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(Text(isBalanceHidden ? LocalizedStringKey("dashboard.balance.show") : LocalizedStringKey("dashboard.balance.hide")))
            }
        }
    }

    private var balance: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("dashboard.total_balance")
                .font(.footnote.weight(.medium))
                .opacity(0.85)

            if let main = balances.first {
                amountText(main)
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .minimumScaleFactor(0.5)
                    .lineLimit(1)
                    .contentTransition(.numericText())
                ForEach(balances.dropFirst(), id: \.currencyCode) { other in
                    amountText(other)
                        .font(.headline.monospacedDigit())
                        .opacity(0.9)
                }
            } else {
                Text(verbatim: "—")
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .accessibilityLabel(Text("dashboard.balance_unavailable"))
                if showsSyncHint {
                    Text("dashboard.sync_pending")
                        .font(.footnote)
                        .opacity(0.85)
                }
            }
        }
    }

    private func amountText(_ amount: CurrencyAmount) -> some View {
        Group {
            if isBalanceHidden {
                Text(verbatim: "•••••• \(Money.symbol(of: amount.currencyCode))")
                    .accessibilityLabel(Text("dashboard.balance.hidden"))
            } else {
                Text(verbatim: Money.format(amount))
            }
        }
    }
}

#Preview {
    BrandHeaderCard(
        firstName: "Awa",
        balances: [CurrencyAmount(currencyCode: "XOF", amountMinor: 125_050_000), CurrencyAmount(currencyCode: "EUR", amountMinor: 32_000)],
        showsSyncHint: false,
        isBalanceHidden: .constant(false)
    )
    .padding()
}
