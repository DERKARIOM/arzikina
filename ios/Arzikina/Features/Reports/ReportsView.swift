import SwiftUI

/// Onglet Rapports. Étape 1 : état vide natif. Les graphiques (Swift Charts) viendront avec les
/// données synchronisées.
struct ReportsView: View {
    var body: some View {
        ContentUnavailableView {
            Label("reports.empty.title", systemImage: "chart.pie")
        } description: {
            Text("reports.empty.message")
        }
        .navigationTitle("tab.reports")
    }
}

#Preview {
    NavigationStack { ReportsView() }
}
