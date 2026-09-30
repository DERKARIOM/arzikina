/// Solde COURANT des comptes — portage d'Android `computeCurrentBalances` (même formule que le
/// serveur `account_balances.php` et le Web) :
///
///     solde = solde initial
///           + revenus du compte
///           − dépenses et transferts sortants du compte
///           + transferts entrants vers le compte
///
/// Les transactions passées ici doivent déjà exclure les transactions supprimées.
public enum AccountBalances {

    /// Solde de chaque compte de [accounts], indexé par son identifiant.
    public static func compute(accounts: [Account], transactions: [Transaction]) -> [EntityID: MinorUnits] {
        var deltaByAccount: [EntityID: MinorUnits] = [:]
        for transaction in transactions {
            deltaByAccount[transaction.accountId, default: 0] += transaction.signedAmount
            if transaction.type == .transfer, let destinationId = transaction.transferAccountId {
                deltaByAccount[destinationId, default: 0] += transaction.amount
            }
        }
        var balances: [EntityID: MinorUnits] = [:]
        for account in accounts {
            balances[account.id] = account.initialBalance + (deltaByAccount[account.id] ?? 0)
        }
        return balances
    }
}
