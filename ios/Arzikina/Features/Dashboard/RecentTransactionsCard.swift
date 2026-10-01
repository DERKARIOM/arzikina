import ArzikinaDomain
import SwiftUI

/// « Dernières transactions » : les 5 plus récentes (transactions de frais incluses dans leur
/// parente), ou un message quand il n'y en a pas encore, et l'accès à la liste complète.
struct RecentTransactionsCard: View {

    let items: [TransactionListItem]
    /// Toucher d'une ligne : ouvre la transaction.
    var onSelect: @MainActor (TransactionListItem) -> Void = { _ in }
    /// « Voir tout » : ouvre la liste complète (masqué tant qu'il n'y a aucune transaction).
    var onSeeAll: (@MainActor () -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label("dashboard.recent_transactions", systemImage: "list.bullet.rectangle.fill")
                    .font(.headline)
                    .foregroundStyle(Brand.primary)
                Spacer(minLength: 8)
                if let onSeeAll, !items.isEmpty {
                    Button("dashboard.recent_transactions.see_all") { onSeeAll() }
                        .font(.subheadline.weight(.semibold))
                }
            }

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
