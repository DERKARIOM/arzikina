import ArzikinaDomain
import SwiftUI

/// Détail d'un compte : sa carte (solde, progression d'un objectif d'épargne) puis toutes ses
/// transactions groupées par jour, avec le solde du compte après chacune — comme Android
/// (`AccountDetailFragment`). Les transferts reçus apparaissent aussi.
///
/// `List` : chargement paresseux des lignes, fluide même avec des milliers de transactions.
struct AccountDetailView: View {

    let accountId: EntityID

    @Environment(SessionModel.self) private var session
    @State private var model = AccountDetailViewModel()

    var body: some View {
        content
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .refreshable {
                await session.sync?.refresh()
            }
            .task(id: session.dataSpace.map(ObjectIdentifier.init)) {
                guard let space = session.dataSpace else { return }
                await model.observe(space.accountOverview, accountId: accountId, calendar: ArzikinaCalendar.current)
            }
    }

    @ViewBuilder
    private var content: some View {
        switch model.state {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
        case .missing:
            ContentUnavailableView {
                Label("account.detail.missing.title", systemImage: "questionmark.folder")
            } description: {
                Text("account.detail.missing.message")
            }
        case .loaded(let detail):
            List {
                Section {
                    header(detail.summary)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }

                if model.sections.isEmpty {
                    Section {
                        Text("account.detail.empty")
                            .foregroundStyle(.secondary)
                    }
                } else {
                    ForEach(model.sections, id: \.day) { section in
                        Section {
                            ForEach(section.items) { item in
                                TransactionRow(item: item, style: .inAccount)
                            }
                        } header: {
                            DayHeader(day: section.day)
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
        }
    }

    @ViewBuilder
    private func header(_ summary: AccountSummary) -> some View {
        if summary.account.type == .creditCard {
            CreditCardView(summary: summary, holderName: session.currentSession?.fullName ?? "")
        } else {
            AccountCard(summary: summary)
        }
    }

    private var title: String {
        if case .loaded(let detail) = model.state { return detail.summary.account.displayName }
        return ""
    }
}

/// En-tête de jour : « Aujourd'hui », « Hier », sinon la date complète dans la langue de l'app.
struct DayHeader: View {

    let day: CalendarDay

    var body: some View {
        let calendar = ArzikinaCalendar.current
        let today = CalendarDay(epochMillis: EpochMillis(Date().timeIntervalSince1970 * 1000), calendar: calendar)
        Group {
            if day == today {
                Text("day.today")
            } else if day == today.adding(.day, -1, calendar: calendar) {
                Text("day.yesterday")
            } else {
                let date = Date(timeIntervalSince1970: TimeInterval(day.startOfDayMillis(calendar: calendar)) / 1000)
                Text(date, format: .dateTime.weekday(.wide).day().month(.wide).year())
            }
        }
    }
}

#Preview {
    NavigationStack { AccountDetailView(accountId: "preview") }
        .environment(SessionModel.preview())
}
