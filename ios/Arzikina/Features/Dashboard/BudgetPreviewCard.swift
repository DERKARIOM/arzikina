import ArzikinaDomain
import SwiftUI

/// Carte « Budget » du tableau de bord : le budget le plus urgent (progression la plus élevée,
/// dépassement compris), comme Android, ou une invite à créer un premier budget.
struct BudgetPreviewCard: View {

    let featured: BudgetSummary?
    let onSeeAll: @MainActor () -> Void
    let onCreate: @MainActor () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Label("dashboard.budget", systemImage: "chart.bar.fill")
                    .font(.headline)
                    .foregroundStyle(Brand.primary)
                Spacer(minLength: 8)
                if featured != nil {
                    Button("dashboard.recent_transactions.see_all") { onSeeAll() }
                        .font(.subheadline.weight(.semibold))
                }
            }
            if let featured {
                Button {
                    onSeeAll()
                } label: {
                    BudgetCard(summary: featured)
                }
                .buttonStyle(.plain)
            } else {
                VStack(alignment: .leading, spacing: 10) {
                    Text("budgets.empty.title")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Button("dashboard.budget.create") { onCreate() }
                        .buttonStyle(.bordered)
                }
                .arzikinaCard()
            }
        }
    }
}
