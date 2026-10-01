import ArzikinaDomain
import SwiftUI

/// Carte d'un compte, sur le dégradé de sa couleur — même contenu qu'Android `AccountCardBinder` :
/// icône, nom, type, solde courant, indicateur « exclu des statistiques » et, pour un objectif
/// d'épargne, la progression (épargné / cible, barre, pourcentage, objectif atteint ou dépassé).
///
/// Partagée par la liste des comptes et l'en-tête du détail d'un compte.
struct AccountCard: View {

    let summary: AccountSummary

    private var account: Account { summary.account }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                Image(systemName: account.icon.systemImage)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(Color(argb: account.colorArgb))
                    .frame(width: 40, height: 40)
                    .background(.white, in: Circle())
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: account.displayName)
                        .font(.headline)
                        .lineLimit(1)
                    Text(verbatim: account.cardSubtitle)
                        .font(.caption)
                        .opacity(0.85)
                }
                Spacer(minLength: 8)
                if account.isExcludedFromStatistics {
                    ExcludedFromStatisticsBadge()
                }
            }

            Text(verbatim: Money.format(CurrencyAmount(currencyCode: account.currencyCode, amountMinor: summary.balance)))
                .font(.system(size: 26, weight: .bold, design: .rounded))
                .minimumScaleFactor(0.6)
                .lineLimit(1)
                .contentTransition(.numericText())

            if let goal = summary.savingsGoal {
                SavingsGoalProgressView(goal: goal, currencyCode: account.currencyCode)
            }
        }
        .foregroundStyle(.white)
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AccountCardBackground.gradient(argb: account.colorArgb), in: RoundedRectangle(cornerRadius: Brand.Radius.card, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

/// Indicateur discret d'un compte exclu des statistiques personnelles.
struct ExcludedFromStatisticsBadge: View {
    var body: some View {
        Image(systemName: "chart.pie")
            .font(.footnote.weight(.semibold))
            .overlay {
                Image(systemName: "line.diagonal")
                    .font(.footnote.weight(.bold))
            }
            .padding(6)
            .background(Color.white.opacity(0.2), in: Circle())
            .accessibilityLabel(Text("accounts.excluded_from_statistics"))
    }
}

/// « 150 000 F CFA / 500 000 F CFA », barre (plafonnée à 100 %), pourcentage et statut.
struct SavingsGoalProgressView: View {

    let goal: SavingsGoalSnapshot
    let currencyCode: String

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(verbatim: "\(format(goal.saved)) / \(format(goal.target))")
                    .font(.caption.monospacedDigit())
                Spacer()
                Text(verbatim: "\(goal.percent) %")
                    .font(.caption.weight(.semibold).monospacedDigit())
            }
            ProgressView(value: Double(min(goal.percent, 100)), total: 100)
                .tint(.white)
                .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: goal.percent)
            if goal.isExceeded {
                Text("account.savings_goal.exceeded \(format(goal.exceededBy))")
                    .font(.caption.weight(.semibold))
            } else if goal.isReached {
                Text("account.savings_goal.reached")
                    .font(.caption.weight(.semibold))
            }
        }
    }

    private func format(_ amount: MinorUnits) -> String {
        Money.format(CurrencyAmount(currencyCode: currencyCode, amountMinor: amount))
    }
}

/// Carte de crédit, présentée comme une carte bancaire — Android `AccountCardCreditBinder`.
/// Seuls les 4 derniers chiffres sont connus (le numéro complet et le CVV ne quittent jamais
/// l'appareil Android qui les a saisis et ne sont pas synchronisés).
struct CreditCardView: View {

    let summary: AccountSummary
    let holderName: String

    private var account: Account { summary.account }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                Text(verbatim: account.displayName)
                    .font(.headline)
                    .lineLimit(1)
                Spacer()
                if account.isExcludedFromStatistics {
                    ExcludedFromStatisticsBadge()
                }
                Image(systemName: "wave.3.right")
                    .font(.headline)
                    .accessibilityHidden(true)
            }

            Text(verbatim: "•••• •••• •••• \(account.cardLastFourDigits ?? "----")")
                .font(.title3.monospaced().weight(.semibold))
                .accessibilityLabel(Text("accounts.card.last_digits \(account.cardLastFourDigits ?? "----")"))

            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: holderName.uppercased())
                        .font(.caption.weight(.semibold))
                        .lineLimit(1)
                    Text(verbatim: "••/••")
                        .font(.caption2.monospaced())
                        .opacity(0.8)
                        .accessibilityHidden(true)
                }
                Spacer()
                Text(verbatim: Money.format(CurrencyAmount(currencyCode: account.currencyCode, amountMinor: summary.balance)))
                    .font(.headline.monospacedDigit())
                    .contentTransition(.numericText())
            }
        }
        .foregroundStyle(.white)
        .padding(20)
        .frame(maxWidth: .infinity, minHeight: 190, alignment: .leading)
        .background(AccountCardBackground.gradient(argb: account.colorArgb), in: RoundedRectangle(cornerRadius: Brand.Radius.card, style: .continuous))
        .accessibilityElement(children: .combine)
    }
}

#Preview {
    ScrollView {
        VStack(spacing: 12) {
            AccountCard(summary: AccountSummary(account: Account(id: "1", name: "Espèces", icon: .cash, colorArgb: 0xFF42_B998), balance: 12_500_000))
            AccountCard(summary: AccountSummary(account: Account(id: "2", name: "Moto", icon: .savings, colorArgb: 0xFF63_66F1, type: .savingsGoal, savingsTargetAmount: 50_000_000), balance: 15_000_000))
            CreditCardView(summary: AccountSummary(account: Account(id: "3", name: "Visa", icon: .creditCard, colorArgb: 0xFF1E_293B, type: .creditCard, cardLastFourDigits: "4242"), balance: -2_000_000), holderName: "Awa Diallo")
        }
        .padding()
    }
}
