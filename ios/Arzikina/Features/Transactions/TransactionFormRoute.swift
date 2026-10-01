import ArzikinaDomain
import SwiftUI

/// Ouverture du formulaire de transaction : nouvelle transaction (compte éventuellement
/// présélectionné) ou modification d'une transaction existante.
enum TransactionFormRoute: Identifiable {
    case create(presetAccountId: EntityID?)
    case edit(ArzikinaDomain.Transaction)

    var id: String {
        switch self {
        case .create(let accountId): return "create-\(accountId ?? "")"
        case .edit(let transaction): return "edit-\(transaction.id)"
        }
    }

    var mode: TransactionFormViewModel.Mode {
        switch self {
        case .create(let accountId): return .create(presetAccountId: accountId)
        case .edit(let transaction): return .edit(transaction)
        }
    }
}

extension View {
    /// Présente le formulaire de transaction quand [route] est renseignée (même présentation sur
    /// tous les écrans qui l'ouvrent).
    func transactionFormSheet(_ route: Binding<TransactionFormRoute?>, session: SessionModel) -> some View {
        sheet(item: route) { route in
            if let space = session.dataSpace {
                TransactionFormView(mode: route.mode, transactions: space.transactions)
                    .environment(session)
            }
        }
    }
}
