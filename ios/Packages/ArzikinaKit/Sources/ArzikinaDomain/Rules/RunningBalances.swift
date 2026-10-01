/// Solde d'un compte juste APRÈS chacune de ses transactions — portage d'Android
/// `computeRunningBalances` (`presentation/transactions/RunningBalance.kt`), vérifié par
/// `shared/test-fixtures/running-balance.json`.
public enum RunningBalances {

    /// [transactions] : les transactions qui concernent [account] (source OU destination d'un
    /// transfert, transactions de frais comprises), de la PLUS RÉCENTE à la plus ancienne.
    ///
    /// On part du solde courant (solde initial + toutes les transactions) et on remonte le temps :
    /// le solde après une transaction est celui d'avant la suivante, plus récente.
    /// Retourne le solde par identifiant de transaction.
    public static func compute(account: Account, transactions newestFirst: [Transaction]) -> [EntityID: MinorUnits] {
        let signed = newestFirst.compactMap { transaction -> (EntityID, MinorUnits)? in
            if transaction.accountId == account.id {
                return (transaction.id, transaction.signedAmount)
            }
            if transaction.type == .transfer, transaction.transferAccountId == account.id {
                return (transaction.id, transaction.amount)
            }
            return nil
        }
        var balance = account.initialBalance + signed.reduce(0) { $0 + $1.1 }
        var result: [EntityID: MinorUnits] = [:]
        for (id, amount) in signed {
            result[id] = balance
            balance -= amount
        }
        return result
    }
}
