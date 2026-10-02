import ArzikinaDomain
import Foundation
import Observation

/// Comptes et catégories proposés par les formulaires d'automatisation (règle et échéance
/// modifiée) — une seule implémentation pour les deux écrans.
///
/// Les catégories sont lues toutes ensemble puis filtrées EN MÉMOIRE selon le type choisi :
/// changer de type ne relance pas de lecture.
@MainActor
@Observable
final class AutomationChoices {

    private(set) var accounts: [Account] = []
    private var allCategories: [ArzikinaDomain.Category] = []

    func observeAccounts(_ repository: AccountRepository, onFirstLoad: @MainActor ([Account]) -> Void = { _ in }) async {
        var isFirst = true
        for await accounts in repository.observeAccounts() {
            self.accounts = accounts
            if isFirst {
                isFirst = false
                onFirstLoad(accounts)
            }
        }
    }

    func observeCategories(_ repository: CategoryRepository) async {
        for await categories in repository.observeCategories(type: nil) {
            allCategories = categories
        }
    }

    /// Catégories de [type] choisissables (jamais celles des prêts ou des frais), dans le même
    /// ordre que le formulaire de transaction.
    func categories(for type: TransactionType) -> [ArzikinaDomain.Category] {
        allCategories.filter { $0.type == type && TransactionForm.isSelectable($0) }
    }

    /// Devise du compte [accountId].
    func currencyCode(of accountId: EntityID?) -> String {
        accounts.first { $0.id == accountId }?.currencyCode ?? SupportedCurrency.defaultCode
    }
}
