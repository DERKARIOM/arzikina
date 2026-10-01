import ArzikinaDomain
import SwiftUI

/// Automatisations (Réglages › Budget et finances, comme Android) : échéances à valider ou
/// rejeter, prochaines échéances, règles (pause / reprise) et historique.
///
/// Les échéances dues sont créées après chaque synchronisation (au lancement, au retour au
/// premier plan…) ; la création et la modification des règles arrivent à l'étape suivante.
struct AutomationsView: View {

    @Environment(SessionModel.self) private var session
    @State private var model = AutomationsViewModel()
    @State private var pendingRejection: AutomationItem?

    var body: some View {
        content
            .navigationTitle("automations.title")
            .confirmationDialog(
                "automations.reject.title",
                isPresented: Binding(get: { pendingRejection != nil }, set: { if !$0 { pendingRejection = nil } }),
                titleVisibility: .visible,
                presenting: pendingRejection
            ) { item in
                Button("automations.reject", role: .destructive) { reject(item) }
            } message: { _ in
                Text("automations.reject.message")
            }
            .alert("automations.action_failed", isPresented: Binding(get: { model.actionFailed }, set: { if !$0 { model.dismissFailure() } })) {
                Button("common.ok", role: .cancel) {}
            }
            .refreshable { await session.sync?.refresh() }
            .task(id: session.dataSpace.map(ObjectIdentifier.init)) {
                guard let space = session.dataSpace else { return }
                await model.observe(space.recurring)
            }
    }

    @ViewBuilder
    private var content: some View {
        if !model.hasLoaded {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
        } else if model.isEmpty {
            ContentUnavailableView {
                Label("automations.empty.title", systemImage: "arrow.triangle.2.circlepath")
            } description: {
                Text("automations.empty.message")
            }
        } else {
            List {
                if !model.overview.pending.isEmpty {
                    Section {
                        ForEach(model.overview.pending) { item in
                            pendingRow(item)
                        }
                    } header: {
                        Text("automations.pending \(model.overview.pending.count)")
                    } footer: {
                        Text("automations.pending.footer")
                    }
                }
                if !model.overview.upcoming.isEmpty {
                    Section("automations.upcoming") {
                        ForEach(model.overview.upcoming) { item in
                            AutomationRow(item: item, detail: .scheduled)
                        }
                    }
                }
                Section("automations.rules") {
                    ForEach(model.overview.rules) { rule in
                        ruleRow(rule)
                    }
                }
                if !model.overview.history.isEmpty {
                    Section("automations.history") {
                        ForEach(model.overview.history.prefix(AutomationsViewModel.historyLimit)) { item in
                            AutomationRow(item: item, detail: .status)
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
        }
    }

    private func pendingRow(_ item: AutomationItem) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            AutomationRow(item: item, detail: .scheduled)
            HStack(spacing: 12) {
                Button {
                    accept(item)
                } label: {
                    Label("automations.accept", systemImage: "checkmark")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                Button {
                    pendingRejection = item
                } label: {
                    Label("automations.reject", systemImage: "xmark")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }
            .disabled(model.busyIds.contains(item.id))
        }
        .padding(.vertical, 4)
    }

    private func ruleRow(_ rule: RecurringTransaction) -> some View {
        Toggle(isOn: Binding(get: { rule.isActive }, set: { setActive(rule, $0) })) {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: rule.displayTitle())
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Text(verbatim: "\(rule.frequency.displayName) · \(Money.formatAmount(rule.amount))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .disabled(model.busyIds.contains(rule.id))
        .accessibilityHint(Text(rule.isActive ? "automations.rule.pause_hint" : "automations.rule.resume_hint"))
    }

    // MARK: - Actions

    private func accept(_ item: AutomationItem) {
        guard let space = session.dataSpace, let id = item.occurrence?.id else { return }
        Task {
            if await model.accept(id, using: space.recurring) { session.sync?.requestSync(.localChange) }
        }
    }

    private func reject(_ item: AutomationItem) {
        guard let space = session.dataSpace, let id = item.occurrence?.id else { return }
        Task {
            if await model.reject(id, using: space.recurring) { session.sync?.requestSync(.localChange) }
        }
    }

    private func setActive(_ rule: RecurringTransaction, _ isActive: Bool) {
        guard let space = session.dataSpace else { return }
        Task {
            if await model.setActive(rule.id, isActive, using: space.recurring) { session.sync?.requestSync(.localChange) }
        }
    }
}

/// Ligne d'échéance : catégorie, description, compte, date et heure prévues (ou statut), montant.
struct AutomationRow: View {

    enum Detail { case scheduled, status }

    let item: AutomationItem
    let detail: Detail

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: item.category?.systemImage ?? CategoryIcon.other.systemImage)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(item.category.map { Color(argb: $0.colorArgb) } ?? Color(.systemGray3), in: Circle())
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: item.rule.displayTitle(category: item.category))
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Text(verbatim: subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 8)
            Text(verbatim: amount)
                .font(.subheadline.weight(.semibold).monospacedDigit())
                .foregroundStyle(item.rule.type == .income ? Brand.income : Brand.expense)
        }
        .accessibilityElement(children: .combine)
    }

    private var amount: String {
        guard let currency = item.account?.currencyCode else { return Money.formatAmount(item.rule.amount) }
        return Money.format(CurrencyAmount(currencyCode: currency, amountMinor: item.rule.amount))
    }

    private var subtitle: String {
        let account = item.account?.displayName ?? DomainDisplay.localized("transaction.unknown_account")
        switch detail {
        case .scheduled:
            let calendar = ArzikinaCalendar.current
            let instant = Recurrence.triggerInstant(day: item.scheduledDate, hour: item.rule.triggerHour, minute: item.rule.triggerMinute, calendar: calendar)
            let date = Date(timeIntervalSince1970: TimeInterval(instant) / 1000)
            return "\(account) · \(date.formatted(.dateTime.day().month(.abbreviated).hour().minute()))"
        case .status:
            let status: String
            switch item.occurrence?.status {
            case .accepted: status = DomainDisplay.localized("automations.status.accepted")
            case .modified: status = DomainDisplay.localized("automations.status.modified")
            case .rejected: status = DomainDisplay.localized("automations.status.rejected")
            case .pending, nil: status = ""
            }
            let date = Date(timeIntervalSince1970: TimeInterval(item.scheduledDate) / 1000)
            return "\(status) · \(date.formatted(.dateTime.day().month(.abbreviated).year()))"
        }
    }
}

extension RecurringTransaction {
    /// Description de la règle, sinon nom de la catégorie, sinon « Transaction automatique ».
    func displayTitle(category: ArzikinaDomain.Category? = nil) -> String {
        let trimmed = description.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { return trimmed }
        return category?.displayName ?? DomainDisplay.localized("automations.fallback_name")
    }
}

extension RecurringFrequency {
    var displayName: String {
        switch self {
        case .once: return DomainDisplay.localized("automations.frequency.once")
        case .daily: return DomainDisplay.localized("automations.frequency.daily")
        case .weekly: return DomainDisplay.localized("automations.frequency.weekly")
        case .biweekly: return DomainDisplay.localized("automations.frequency.biweekly")
        case .monthly: return DomainDisplay.localized("automations.frequency.monthly")
        case .quarterly: return DomainDisplay.localized("automations.frequency.quarterly")
        case .semiannual: return DomainDisplay.localized("automations.frequency.semiannual")
        case .yearly: return DomainDisplay.localized("automations.frequency.yearly")
        }
    }
}
