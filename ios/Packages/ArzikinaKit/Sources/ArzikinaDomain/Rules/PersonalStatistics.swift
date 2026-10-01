/// Point d'entrée UNIQUE de la question « un compte compte-t-il dans les statistiques
/// personnelles ? » — portage d'Android `util/PersonalStatistics`.
///
/// Toute agrégation sur PLUSIEURS comptes (solde total, revenus/dépenses du mois, répartition par
/// catégorie, budgets…) construit d'abord son périmètre avec [scope]. Le solde propre d'UN compte,
/// lui, reste calculé sur toutes ses transactions, que le compte soit exclu ou non.
public enum PersonalStatistics {

    public struct Scope: Equatable, Sendable {
        /// Comptes dont `isExcludedFromStatistics` est faux.
        public let accounts: [Account]
        /// Transactions dont le compte SOURCE fait partie de [accounts]. Les transferts n'ont pas
        /// de traitement particulier : les totaux de revenus/dépenses ne lisent que INCOME/EXPENSE.
        public let transactions: [Transaction]
    }

    public static func scope(accounts: [Account], transactions: [Transaction]) -> Scope {
        let included = accounts.filter { !$0.isExcludedFromStatistics }
        let includedIds = Set(included.map(\.id))
        return Scope(accounts: included, transactions: transactions.filter { includedIds.contains($0.accountId) })
    }
}
