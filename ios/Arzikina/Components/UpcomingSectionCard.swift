import SwiftUI

/// Section du tableau de bord pas encore branchée sur les données (étape 1) : titre, icône et un
/// message « Bientôt disponible ». Sera remplacée section par section au fil des étapes.
struct UpcomingSectionCard: View {
    let titleKey: LocalizedStringKey
    let systemImage: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(titleKey, systemImage: systemImage)
                .font(.headline)
                .foregroundStyle(Brand.primary)
            Text("placeholder.coming_soon")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .arzikinaCard()
    }
}

#Preview {
    UpcomingSectionCard(titleKey: "dashboard.budgets", systemImage: "chart.bar.fill")
        .padding()
        .background(Color(.systemGroupedBackground))
}
