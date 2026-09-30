import SwiftUI

/// Onglet Accueil. Étape 1 : branding + emplacements des futures sections (solde total,
/// dernières transactions, budgets). Aucune donnée n'est affichée tant que la synchronisation
/// n'est pas en place.
struct DashboardView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                BrandHeaderCard()
                UpcomingSectionCard(titleKey: "dashboard.recent_transactions", systemImage: "list.bullet.rectangle.fill")
                UpcomingSectionCard(titleKey: "dashboard.budgets", systemImage: "chart.bar.fill")
            }
            .padding()
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("tab.home")
    }
}

#Preview {
    NavigationStack { DashboardView() }
}
