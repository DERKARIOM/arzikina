import ArzikinaDomain
import SwiftUI

/// Ouverture du formulaire de transaction : nouvelle transaction (compte éventuellement
/// présélectionné, ou remplie par un modèle) ou modification d'une transaction existante.
enum TransactionFormRoute: Identifiable {
    case create(presetAccountId: EntityID?)
    /// Modèle utilisé : formulaire pré-rempli, daté d'aujourd'hui (le modèle ne change pas).
    case template(TransactionTemplate)
    case edit(ArzikinaDomain.Transaction)

    var id: String {
        switch self {
        case .create(let accountId): return "create-\(accountId ?? "")"
        case .template(let template): return "template-\(template.id)"
        case .edit(let transaction): return "edit-\(transaction.id)"
        }
    }

    var mode: TransactionFormViewModel.Mode {
        switch self {
        case .create(let accountId): return .create(presetAccountId: accountId)
        case .template(let template):
            let now = EpochMillis((Date().timeIntervalSince1970 * 1000).rounded())
            return .prefilled(TransactionDraft(template: template, now: now, calendar: ArzikinaCalendar.current))
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
