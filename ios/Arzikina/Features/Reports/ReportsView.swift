import ArzikinaDomain
import SwiftUI

/// Onglet Rapports — Android « Statistiques » : période (préréglages ou dates libres), totaux
/// revenus / dépenses / solde, répartition par catégorie (dépenses ou revenus) et évolution des
/// 6 derniers mois, toujours à jour à partir de la base locale.
struct ReportsView: View {

    @Environment(SessionModel.self) private var session
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = ReportsViewModel()

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                PeriodCard(model: model, today: today)
                if let snapshot = model.snapshot {
                    TotalsCard(snapshot: snapshot)
                    BreakdownCard(snapshot: snapshot, type: Bindable(model).breakdownType)
                    EvolutionCard(snapshot: snapshot)
                    Text("reports.currency_note \(Money.symbol(of: snapshot.currencyCode))")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 4)
                } else {
                    ProgressView().padding(.top, 40)
                }
            }
            .padding()
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.25), value: model.snapshot)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("tab.reports")
        .refreshable {
            await session.sync?.refresh()
        }
        .task(id: observationKey) {
            guard let space = session.dataSpace else { return }
            await model.observe(space.reports, today: observationKey.today)
        }
    }

    private struct ObservationKey: Equatable {
        let space: ObjectIdentifier?
        let today: CalendarDay
        let selection: StatsPeriodSelection
        let type: BreakdownType
    }

    private var today: CalendarDay {
        _ = scenePhase // réévalué au retour au premier plan
        return .today()
    }

    private var observationKey: ObservationKey {
        ObservationKey(space: session.dataSpace.map(ObjectIdentifier.init), today: today, selection: model.selection, type: model.breakdownType)
    }
}

// MARK: - Période

private struct PeriodCard: View {
    let model: ReportsViewModel
    let today: CalendarDay

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Menu {
                    ForEach(StatsPeriodPreset.allCases, id: \.self) { preset in
                        Button {
                            model.select(preset, today: today)
                        } label: {
                            if preset == model.selection.preset {
                                Label(preset.titleKey, systemImage: "checkmark")
                            } else {
                                Text(preset.titleKey)
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "calendar")
                        Text(model.selection.preset.titleKey)
                            .font(.headline)
                        Image(systemName: "chevron.up.chevron.down")
                            .font(.caption.weight(.semibold))
                    }
                    .foregroundStyle(Brand.primary)
                }
                Spacer()
                if model.selection.preset != .month {
                    Button("reports.period.reset") { model.resetPeriod() }
                        .font(.subheadline)
                }
            }
            if let period = model.period(today: today) {
                Text(verbatim: Self.rangeText(period))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            if model.selection.preset == .custom {
                DatePicker("reports.period.start", selection: Binding(get: { model.customStart }, set: { model.changeCustomStart($0) }), displayedComponents: .date)
                DatePicker("reports.period.end", selection: Binding(get: { model.customEnd }, set: { model.changeCustomEnd($0) }), displayedComponents: .date)
                switch model.periodError(today: today) {
                case .missingDates: FormErrorText(key: "reports.period.error.missing")
                case .startAfterEnd: FormErrorText(key: "reports.period.error.start_after_end")
                case nil: EmptyView()
                }
            }
        }
        .arzikinaCard()
    }

    private static func rangeText(_ period: (start: CalendarDay, end: CalendarDay)) -> String {
        let calendar = ArzikinaCalendar.current
        let format = Date.FormatStyle.dateTime.day().month(.abbreviated).year()
        let date = { (day: CalendarDay) in Date(timeIntervalSince1970: TimeInterval(day.startOfDayMillis(calendar: calendar)) / 1000) }
        return "\(date(period.start).formatted(format)) → \(date(period.end).formatted(format))"
    }
}

// MARK: - Totaux

private struct TotalsCard: View {
    let snapshot: ReportSnapshot

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            figure("reports.income", snapshot.income, color: ReportColors.income)
            Divider()
            figure("reports.expense", snapshot.expense, color: ReportColors.expense)
            Divider()
            figure("reports.net", snapshot.net, color: .primary)
        }
        .fixedSize(horizontal: false, vertical: true)
        .arzikinaCard()
    }

    private func figure(_ titleKey: LocalizedStringKey, _ amount: MinorUnits, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                if color != .primary {
                    RoundedRectangle(cornerRadius: 2).fill(color).frame(width: 10, height: 10)
                        .accessibilityHidden(true)
                }
                Text(titleKey)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Text(verbatim: Money.format(CurrencyAmount(currencyCode: snapshot.currencyCode, amountMinor: amount)))
                .font(.subheadline.weight(.semibold).monospacedDigit())
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .contentTransition(.numericText())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

extension StatsPeriodPreset {
    var titleKey: LocalizedStringKey {
        switch self {
        case .month: return "reports.period.month"
        case .previousMonth: return "reports.period.previous_month"
        case .last7Days: return "reports.period.last_7_days"
        case .last30Days: return "reports.period.last_30_days"
        case .year: return "reports.period.year"
        case .custom: return "reports.period.custom"
        }
    }
}

/// Couleurs des graphiques revenus / dépenses, validées (lisibilité en clair et en sombre,
/// distinction pour les daltoniens) : vert profond de la marque et rouge des dépenses.
enum ReportColors {
    static let income = Brand.primaryDeep
    static let expense = Brand.expense
}

#Preview {
    NavigationStack { ReportsView() }
        .environment(SessionModel.preview())
}
