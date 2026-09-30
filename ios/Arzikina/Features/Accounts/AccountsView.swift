import SwiftUI

/// Onglet Comptes. Étape 1 : état vide natif (`ContentUnavailableView`). Les comptes classiques,
/// Mobile Money, cartes et objectifs d'épargne arriveront avec le stockage local et la sync.
struct AccountsView: View {
    var body: some View {
        ContentUnavailableView {
            Label("accounts.empty.title", systemImage: "creditcard")
        } description: {
            Text("accounts.empty.message")
        }
        .navigationTitle("tab.accounts")
    }
}

#Preview {
    NavigationStack { AccountsView() }
}
