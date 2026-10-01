import ArzikinaDomain
import SwiftUI

/// Carte d'un budget — même contenu qu'Android (`BudgetModernAdapter`) : catégorie et statut
/// (« Dépassé » prioritaire), plafond / dépensé / reste, barre de progression avec le repère
/// « Aujourd'hui », période et jours restants avec le rythme de dépense.
///
/// Partagée par la liste des budgets et le tableau de bord (budget mis en avant).
struct BudgetCard: View {

    let summary: BudgetSummary

    private var budget: Budget { summary.budget }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            header
            amounts
            VStack(alignment: .leading, spacing: 6) {
                BudgetProgressBar(
                    progress: summary.progress,
                    todayRatio: summary.showsPace ? summary.pace.elapsedRatio : nil,
                    tint: Color(argb: summary.category?.colorArgb ?? CategoryDraft.defaultColorArgb)
                )
                Text("budget.percent_used \(summary.percent)")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(summary.isOverspent ? Brand.expense : .secondary)
            }
            footer
        }
        .arzikinaCard()
        .accessibilityElement(children: .combine)
    }

    // MARK: - Parties

    private var header: some View {
        HStack(spacing: 12) {
            Image(systemName: summary.category?.systemImage ?? CategoryIcon.other.systemImage)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(Color(argb: summary.category?.colorArgb ?? 0xFF64_748B), in: Circle())
                .accessibilityHidden(true)
            Text(verbatim: summary.category?.displayName ?? DomainDisplay.localized("transaction.uncategorized"))
                .font(.headline)
                .lineLimit(1)
            Spacer(minLength: 8)
            BudgetStatusBadge(status: summary.displayStatus)
        }
    }

    private var amounts: some View {
        HStack(alignment: .top) {
            amountColumn("budget.total", budget.limitAmount, color: .primary)
            Spacer(minLength: 8)
            amountColumn("budget.spent", summary.spent, color: .primary)
            Spacer(minLength: 8)
            amountColumn("budget.remaining", summary.remaining, color: summary.remaining < 0 ? Brand.expense : Brand.income)
        }
    }

    private func amountColumn(_ titleKey: LocalizedStringKey, _ amount: MinorUnits, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(titleKey)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(verbatim: Money.format(CurrencyAmount(currencyCode: budget.currencyCode, amountMinor: amount)))
                .font(.subheadline.weight(.semibold).monospacedDigit())
                .foregroundStyle(color)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
    }

    private var footer: some View {
        HStack(spacing: 8) {
            Label {
                Text(verbatim: periodText)
            } icon: {
                Image(systemName: "calendar")
            }
            .font(.caption)
            .foregroundStyle(.secondary)
            .lineLimit(1)
            Spacer(minLength: 8)
            HStack(spacing: 6) {
                if summary.showsPace {
                    Circle()
                        .fill(paceColor)
                        .frame(width: 8, height: 8)
                        .accessibilityHidden(true)
                }
                Text(daysText)
                    .font(.caption.weight(.medium))
                    .lineLimit(1)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Color(.tertiarySystemFill), in: Capsule())
        }
    }

    // MARK: - Textes

    private var periodText: String {
        guard let start = budget.startDate, let end = budget.endDate else {
            return budget.period.displayName
        }
        let format = Date.FormatStyle.dateTime.day().month(.abbreviated).year()
        return "\(Self.date(start).formatted(format)) → \(Self.date(end).formatted(format))"
    }

    private var daysText: LocalizedStringKey {
        let base: String
        switch summary.pace.periodStatus {
        case .upcoming: return "budget.starts_in_days \(summary.days)"
        case .completed: base = DomainDisplay.localized("budget.completed")
        // Interpolation localisée : le String Catalog choisit le singulier ou le pluriel.
        case .ongoing: base = String(localized: "budget.days_left \(summary.days)")
        }
        return "budget.days_with_pace \(base) \(paceText)"
    }

    private var paceText: String {
        switch summary.pace.paceState {
        case .onTrack: return DomainDisplay.localized("budget.pace.on_track")
        case .ahead: return DomainDisplay.localized("budget.pace.ahead")
        case .over: return DomainDisplay.localized("budget.pace.over")
        }
    }

    private var paceColor: Color {
        switch summary.pace.paceState {
        case .onTrack: return Brand.income
        case .ahead: return Color(argb: 0xFF34_98DB)
        case .over: return Brand.expense
        }
    }

    private static func date(_ millis: EpochMillis) -> Date {
        Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
    }
}

/// Pastille de statut : point coloré + libellé.
struct BudgetStatusBadge: View {
    let status: BudgetSummary.DisplayStatus

    var body: some View {
        HStack(spacing: 6) {
            Circle()
                .fill(color)
                .frame(width: 7, height: 7)
            Text(titleKey)
                .font(.caption.weight(.semibold))
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(color.opacity(0.14), in: Capsule())
    }

    private var titleKey: LocalizedStringKey {
        switch status {
        case .overspent: return "budget.status.overspent"
        case .upcoming: return "budget.status.upcoming"
        case .ongoing: return "budget.status.ongoing"
        case .completed: return "budget.status.completed"
        }
    }

    private var color: Color {
        switch status {
        case .overspent: return Brand.expense
        case .upcoming: return Color(argb: 0xFFF5_9E0B)
        case .ongoing: return Brand.income
        case .completed: return .secondary
        }
    }
}

/// Barre de progression d'un budget (plafonnée à 100 %, rouge en cas de dépassement) et repère
/// « Aujourd'hui » : à gauche du repère, la dépense « attendue » à ce jour.
struct BudgetProgressBar: View {
    let progress: Double
    /// Position du repère (0…1), `nil` pour le masquer (période pas commencée).
    let todayRatio: Double?
    let tint: Color

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color(.tertiarySystemFill))
                Capsule()
                    .fill(progress > 1 ? Brand.expense : tint)
                    .frame(width: max(width * min(max(progress, 0), 1), progress > 0 ? 8 : 0))
                if let todayRatio {
                    RoundedRectangle(cornerRadius: 1)
                        .fill(Color.primary.opacity(0.75))
                        .frame(width: 2, height: 16)
                        .offset(x: min(max(width * todayRatio - 1, 0), width - 2))
                        .accessibilityLabel(Text("budget.today"))
                }
            }
            .frame(height: 16)
            .animation(reduceMotion ? nil : .easeInOut(duration: 0.3), value: progress)
        }
        .frame(height: 16)
    }
}

#Preview {
    let calendar = ArzikinaCalendar.current
    let today = CalendarDay.today()
    let start = today.adding(.day, -10, calendar: calendar).startOfDayMillis(calendar: calendar)
    let end = today.adding(.day, 20, calendar: calendar).startOfDayMillis(calendar: calendar)
    let food = ArzikinaDomain.Category(id: "c", name: "Nourriture", icon: .food, colorArgb: 0xFFF5_9E0B, type: .expense)
    return ScrollView {
        VStack(spacing: 12) {
            BudgetCard(summary: BudgetSummary(budget: Budget(id: "1", categoryId: "c", limitAmount: 5_000_000, startDate: start, endDate: end), category: food, spent: 1_200_000, today: today, calendar: calendar))
            BudgetCard(summary: BudgetSummary(budget: Budget(id: "2", categoryId: "c", period: .monthly, limitAmount: 2_000_000), category: food, spent: 2_600_000, today: today, calendar: calendar))
        }
        .padding()
    }
    .background(Color(.systemGroupedBackground))
}
