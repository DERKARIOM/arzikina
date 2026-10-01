import ArzikinaDomain
import SwiftUI

/// Répartition par catégorie (dépenses ou revenus) : barres classées, chacune avec l'icône et le
/// nom de sa catégorie (l'identité ne repose jamais sur la seule couleur), le montant et la part.
/// Au-delà de 7 catégories, le reste est regroupé en « Autres ».
struct BreakdownCard: View {

    let snapshot: ReportSnapshot
    @Binding var type: BreakdownType

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("reports.breakdown")
                    .font(.headline)
                Spacer()
                Picker("reports.breakdown", selection: $type) {
                    Text("reports.expense").tag(BreakdownType.expense)
                    Text("reports.income").tag(BreakdownType.income)
                }
                .pickerStyle(.segmented)
                .fixedSize()
                .labelsHidden()
            }
            if snapshot.breakdown.isEmpty {
                Text(type == .expense ? "reports.breakdown.empty.expense" : "reports.breakdown.empty.income")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .padding(.vertical, 8)
            } else {
                VStack(spacing: 12) {
                    ForEach(snapshot.breakdown) { share in
                        ShareRow(share: share, currencyCode: snapshot.currencyCode)
                    }
                }
            }
        }
        .arzikinaCard()
    }
}

private struct ShareRow: View {
    let share: CategoryShare
    let currencyCode: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                Image(systemName: share.isOther ? "ellipsis" : (share.category?.systemImage ?? CategoryIcon.other.systemImage))
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 28, height: 28)
                    .background(color, in: Circle())
                    .accessibilityHidden(true)
                Text(verbatim: name)
                    .font(.subheadline)
                    .lineLimit(1)
                Spacer(minLength: 8)
                Text(verbatim: Money.format(CurrencyAmount(currencyCode: currencyCode, amountMinor: share.amount)))
                    .font(.subheadline.weight(.semibold).monospacedDigit())
                Text(verbatim: share.share.formatted(.percent.precision(.fractionLength(0))))
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
                    .frame(minWidth: 36, alignment: .trailing)
            }
            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color(.tertiarySystemFill))
                    Capsule().fill(color)
                        .frame(width: max(proxy.size.width * share.share, 4))
                }
            }
            .frame(height: 6)
            .padding(.leading, 38)
            .accessibilityHidden(true)
        }
        .accessibilityElement(children: .combine)
    }

    private var name: String {
        if share.isOther { return DomainDisplay.localized("reports.breakdown.other") }
        return share.category?.displayName ?? DomainDisplay.localized("transaction.uncategorized")
    }

    private var color: Color {
        if share.isOther { return Color(.systemGray) }
        return share.category.map { Color(argb: $0.colorArgb) } ?? Color(.systemGray3)
    }
}
