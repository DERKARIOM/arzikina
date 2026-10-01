import ArzikinaDomain
import SwiftUI

/// « Dernières transactions » : les 5 plus récentes (transactions de frais incluses dans leur
/// parente), ou un message quand il n'y en a pas encore.
struct RecentTransactionsCard: View {

    let items: [TransactionListItem]
    /// Toucher d'une ligne : ouvre la transaction.
    var onSelect: @MainActor (TransactionListItem) -> Void = { _ in }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("dashboard.recent_transactions", systemImage: "list.bullet.rectangle.fill")
                .font(.headline)
                .foregroundStyle(Brand.primary)

            if items.isEmpty {
                Text("dashboard.recent_transactions.empty")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .padding(.vertical, 8)
            } else {
                ForEach(items) { item in
                    Button {
                        onSelect(item)
                    } label: {
                        TransactionRow(item: item)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    if item.id != items.last?.id {
                        // Aligné sur le texte, après l'icône (40 pt + 12 pt d'espacement).
                        Divider().padding(.leading, 52)
                    }
                }
            }
        }
        .arzikinaCard()
    }
}
