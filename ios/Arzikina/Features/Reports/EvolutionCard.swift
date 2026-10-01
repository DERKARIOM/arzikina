import ArzikinaDomain
import Charts
import SwiftUI

/// Évolution des revenus et des dépenses sur les 6 derniers mois (indépendante de la période
/// choisie, comme Android) : barres groupées, légende toujours visible, et les valeurs du mois
/// touché (par défaut le mois en cours) affichées au-dessus du graphique.
struct EvolutionCard: View {

    let snapshot: ReportSnapshot
    @State private var selectedDate: Date?

    private struct Point: Identifiable {
        let date: Date
        let series: Series
        let amount: MinorUnits
        var id: String { "\(date.timeIntervalSince1970)-\(series)" }
    }

    private enum Series: CaseIterable {
        case income, expense

        var titleKey: String { self == .income ? "reports.income" : "reports.expense" }
        var color: Color { self == .income ? ReportColors.income : ReportColors.expense }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("reports.evolution")
                .font(.headline)
            legend
            if let month = focusedMonth {
                monthValues(month)
            }
            Chart(points) { point in
                BarMark(
                    x: .value("month", point.date, unit: .month),
                    y: .value("amount", Money.toMajorDouble(point.amount))
                )
                .foregroundStyle(point.series.color)
                .position(by: .value("series", DomainDisplay.localized(point.series.titleKey)))
                .cornerRadius(4)
                .opacity(isDimmed(point.date) ? 0.35 : 1)
                .accessibilityLabel(Text(verbatim: "\(DomainDisplay.localized(point.series.titleKey)), \(point.date.formatted(.dateTime.month(.wide).year()))"))
                .accessibilityValue(Text(verbatim: format(point.amount)))
            }
            .chartLegend(.hidden) // légende dessinée au-dessus, en texte
            .chartXAxis {
                AxisMarks(values: .stride(by: .month)) { _ in
                    AxisValueLabel(format: .dateTime.month(.abbreviated), centered: true)
                }
            }
            .chartYAxis {
                AxisMarks(position: .leading) { value in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                    AxisValueLabel {
                        if let amount = value.as(Double.self) {
                            Text(verbatim: amount.formatted(.number.notation(.compactName)))
                        }
                    }
                }
            }
            .chartXSelection(value: $selectedDate)
            .frame(height: 200)
        }
        .arzikinaCard()
    }

    // MARK: - Contenu

    private var legend: some View {
        HStack(spacing: 16) {
            ForEach(Series.allCases, id: \.self) { series in
                HStack(spacing: 6) {
                    RoundedRectangle(cornerRadius: 2).fill(series.color).frame(width: 10, height: 10)
                    Text(LocalizedStringKey(series.titleKey))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .accessibilityHidden(true)
    }

    /// Valeurs du mois touché, sinon du mois en cours : chaque montant reste lisible sans
    /// dépendre de la couleur ni de la hauteur d'une barre.
    private func monthValues(_ month: MonthTotals) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(date(month.month), format: .dateTime.month(.wide).year())
                .font(.subheadline.weight(.semibold))
            Spacer(minLength: 8)
            value(month.income, series: .income)
            value(month.expense, series: .expense)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("reports.evolution.month_values \(date(month.month).formatted(.dateTime.month(.wide).year())) \(format(month.income)) \(format(month.expense))"))
    }

    /// Montant précédé du repère de couleur de sa série (le texte reste en couleur de texte).
    private func value(_ amount: MinorUnits, series: Series) -> some View {
        HStack(spacing: 4) {
            RoundedRectangle(cornerRadius: 2).fill(series.color).frame(width: 8, height: 8)
            Text(verbatim: format(amount))
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
        }
    }

    private var focusedMonth: MonthTotals? {
        guard let selectedDate else { return snapshot.evolution.last }
        let calendar = ArzikinaCalendar.current
        let day = CalendarDay(epochMillis: EpochMillis(selectedDate.timeIntervalSince1970 * 1000), calendar: calendar)
        return snapshot.evolution.first { $0.month.year == day.year && $0.month.month == day.month } ?? snapshot.evolution.last
    }

    private func isDimmed(_ month: Date) -> Bool {
        guard selectedDate != nil, let focused = focusedMonth else { return false }
        return month != date(focused.month)
    }

    private var points: [Point] {
        snapshot.evolution.flatMap { month in
            [Point(date: date(month.month), series: .income, amount: month.income),
             Point(date: date(month.month), series: .expense, amount: month.expense)]
        }
    }

    private func date(_ day: CalendarDay) -> Date {
        Date(timeIntervalSince1970: TimeInterval(day.startOfDayMillis(calendar: ArzikinaCalendar.current)) / 1000)
    }

    private func format(_ amount: MinorUnits) -> String {
        Money.format(CurrencyAmount(currencyCode: snapshot.currencyCode, amountMinor: amount))
    }
}
