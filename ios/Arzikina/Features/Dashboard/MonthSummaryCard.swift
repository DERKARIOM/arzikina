import ArzikinaDomain
import SwiftUI

/// « Dépenses vs revenus » du mois en cours (Android : revenus/dépenses du mois et
/// `IncomeExpenseBarView`) : revenus, dépenses, barre de proportion et différence, pour chaque
/// devise utilisée ce mois-ci.
struct MonthSummaryCard: View {

    let rows: [DashboardViewModel.MonthRow]
    let isAmountHidden: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Label("dashboard.month.title", systemImage: "chart.bar.xaxis")
                .font(.headline)
                .foregroundStyle(Brand.primary)

            ForEach(rows) { row in
                MonthRowView(row: row, isAmountHidden: isAmountHidden)
                if row.id != rows.last?.id { Divider() }
            }
        }
        .arzikinaCard()
    }
}

private struct MonthRowView: View {

    let row: DashboardViewModel.MonthRow
    let isAmountHidden: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                figure("dashboard.month.income", amount: row.income, color: Brand.income)
                Spacer()
                figure("dashboard.month.expense", amount: row.expense, color: Brand.expense, alignment: .trailing)
            }

            ProportionBar(expenseShare: row.expenseShare, hasMovement: row.income + row.expense > 0)
                .accessibilityHidden(true)

            HStack {
                Text("dashboard.month.difference")
                    .foregroundStyle(.secondary)
                Spacer()
                Text(verbatim: text(row.difference))
                    .fontWeight(.semibold)
                    .foregroundStyle(row.difference < 0 ? Brand.expense : .primary)
            }
            .font(.subheadline)
            .accessibilityElement(children: .combine)
        }
    }

    private func figure(_ titleKey: LocalizedStringKey, amount: MinorUnits, color: Color, alignment: HorizontalAlignment = .leading) -> some View {
        VStack(alignment: alignment, spacing: 2) {
            Text(titleKey)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(verbatim: text(amount))
                .font(.headline.monospacedDigit())
                .foregroundStyle(color)
                .contentTransition(.numericText())
        }
        .accessibilityElement(children: .combine)
    }

    private func text(_ amount: MinorUnits) -> String {
        if isAmountHidden { return "•••••• \(Money.symbol(of: row.currencyCode))" }
        return Money.format(CurrencyAmount(currencyCode: row.currencyCode, amountMinor: amount))
    }
}

/// Barre revenus | dépenses : la part de chaque côté est proportionnelle à son montant ; grise si
/// le mois n'a encore aucun mouvement.
private struct ProportionBar: View {

    let expenseShare: Double
    let hasMovement: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { proxy in
            HStack(spacing: 2) {
                Capsule()
                    .fill(hasMovement ? Brand.income : Color(.systemGray5))
                Capsule()
                    .fill(Brand.expense)
                    .frame(width: max(0, (proxy.size.width - 2) * expenseShare))
                    .opacity(expenseShare > 0 ? 1 : 0)
            }
        }
        .frame(height: 8)
        .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: expenseShare)
    }
}
