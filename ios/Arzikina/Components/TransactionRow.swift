import ArzikinaDomain
import SwiftUI

/// Ligne de transaction compacte — même contenu que la ligne Android (`TransactionItemBinder`) :
/// icône de catégorie sur sa couleur, catégorie (ou « Transfert d'argent »), « Compte • date
/// [• moyen de paiement] », montant coloré selon le type (sans signe +/-) et, s'il y a lieu,
/// l'indicateur discret « + Frais X ».
///
/// Deux présentations, comme Android (`showDescriptionSubtitle`) :
/// - [Style.compact] (tableau de bord) : sous-titre « Compte • date [• moyen de paiement] » ;
/// - [Style.grouped] (listes groupées par jour : Transactions, détail d'un compte) : la description en sous-titre
///   (masquée si vide) et, sous le montant, le solde du compte après la transaction.
struct TransactionRow: View {

    enum Style {
        case compact
        case grouped
    }

    let item: TransactionListItem
    var style: Style = .compact

    private var transaction: ArzikinaDomain.Transaction { item.transaction }
    private var isTransfer: Bool { transaction.type == .transfer }

    var body: some View {
        HStack(spacing: 12) {
            icon
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: title)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                if let subtitle {
                    Text(verbatim: subtitle)
                        .font(.caption)
                        .italic(style == .grouped)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 2) {
                Text(verbatim: format(transaction.amount))
                    .font(.subheadline.weight(.semibold).monospacedDigit())
                    .foregroundStyle(amountColor)
                if let fee = item.feeAmount {
                    Text("transaction.fee_indicator \(format(fee))")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                if style == .grouped, let balance = item.runningBalance {
                    Text(verbatim: "(\(format(balance)))")
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.secondary)
                        .accessibilityLabel(Text("transaction.balance_after \(format(balance))"))
                }
            }
        }
        .padding(.vertical, 6)
        .accessibilityElement(children: .combine)
    }

    // MARK: - Contenu

    private var icon: some View {
        Image(systemName: isTransfer ? "arrow.left.arrow.right" : (item.category?.systemImage ?? CategoryIcon.other.systemImage))
            .font(.system(size: 16, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 40, height: 40)
            .background(iconColor, in: Circle())
            .accessibilityHidden(true)
    }

    private var iconColor: Color {
        if isTransfer { return Brand.transfer }
        return item.category.map { Color(argb: $0.colorArgb) } ?? Color(.systemGray3)
    }

    private var title: String {
        if isTransfer { return DomainDisplay.localized("transaction.category.transfer") }
        return item.category?.displayName ?? DomainDisplay.localized("transaction.uncategorized")
    }

    private var subtitle: String? {
        if style == .grouped {
            let description = transaction.description.trimmingCharacters(in: .whitespacesAndNewlines)
            return description.isEmpty ? nil : description
        }
        let account = item.account?.displayName ?? DomainDisplay.localized("transaction.unknown_account")
        let date = Date(timeIntervalSince1970: TimeInterval(transaction.date) / 1000)
            .formatted(.dateTime.day().month(.abbreviated))
        var parts = [account, date]
        if let method = transaction.paymentMethod { parts.append(method.displayName) }
        return parts.joined(separator: " • ")
    }

    private var amountColor: Color {
        switch transaction.type {
        case .income: return Brand.income
        case .expense: return Brand.expense
        case .transfer: return Brand.transfer
        }
    }

    /// Montant dans la devise du compte de la ligne (Android fait de même pour les frais).
    private func format(_ amount: MinorUnits) -> String {
        guard let currency = item.account?.currencyCode else { return Money.formatAmount(amount) }
        return Money.format(CurrencyAmount(currencyCode: currency, amountMinor: amount))
    }
}

#Preview {
    List {
        TransactionRow(item: TransactionListItem(
            transaction: ArzikinaDomain.Transaction(id: "1", amount: 250_000, type: .expense, accountId: "a", categoryId: "c", date: 1_727_000_000_000, paymentMethod: .mobileMoney, feeTransactionId: "f"),
            account: Account(id: "a", name: "Mobile Money", currencyCode: "XOF"),
            category: ArzikinaDomain.Category(id: "c", name: "Nourriture", icon: .food, colorArgb: 0xFFF5_9E0B, type: .expense),
            feeAmount: 5_000
        ))
        TransactionRow(item: TransactionListItem(
            transaction: ArzikinaDomain.Transaction(id: "2", amount: 1_000_000, type: .transfer, accountId: "a", transferAccountId: "b", date: 1_727_000_000_000),
            account: Account(id: "a", name: "Espèces", currencyCode: "XOF")
        ))
    }
}
