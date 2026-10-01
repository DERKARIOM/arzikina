import Foundation

/// Règles du tableau de bord — portage de `DashboardViewModel` d'Android (calculs uniquement, sans
/// interface).
public enum DashboardRules {

    /// Nombre de dernières transactions affichées (Android `RECENT_TRANSACTIONS_LIMIT`).
    public static let recentTransactionsLimit = 5

    /// Bornes du mois civil qui contient [instant] : `[start, end[` en millisecondes, dans le
    /// fuseau de [calendar] (Android : `YearMonth.now()` dans le fuseau de l'appareil).
    public static func monthInterval(containing instant: EpochMillis, calendar: Calendar) -> (start: EpochMillis, end: EpochMillis) {
        let date = Date(timeIntervalSince1970: TimeInterval(instant) / 1000)
        let interval = calendar.dateInterval(of: .month, for: date)!
        return (millis(interval.start), millis(interval.end))
    }

    /// Solde total PAR DEVISE des comptes inclus dans les statistiques, dans l'ordre d'apparition
    /// des devises parmi [accounts] (aucun taux de change : chaque devise reste séparée).
    /// Un compte sans solde calculé compte pour son solde initial.
    public static func totalBalances(accounts: [Account], balances: [EntityID: MinorUnits]) -> [CurrencyAmount] {
        let included = PersonalStatistics.scope(accounts: accounts, transactions: []).accounts
        var order: [String] = []
        var totals: [String: MinorUnits] = [:]
        for account in included {
            if totals[account.currencyCode] == nil { order.append(account.currencyCode) }
            totals[account.currencyCode, default: 0] += balances[account.id] ?? account.initialBalance
        }
        return order.map { CurrencyAmount(currencyCode: $0, amountMinor: totals[$0]!) }
    }

    /// Revenus et dépenses d'une période, par devise du compte.
    public struct PeriodTotals: Equatable, Sendable {
        public var income: [CurrencyAmount]
        public var expense: [CurrencyAmount]

        public init(income: [CurrencyAmount] = [], expense: [CurrencyAmount] = []) {
            self.income = income
            self.expense = expense
        }
    }

    /// Revenus et dépenses des transactions datées dans `[start, end[`, limitées au périmètre des
    /// statistiques personnelles. Les transferts sont ignorés ; les frais sont des dépenses comme
    /// les autres. Devises triées comme dans [currencyOrder], puis par code.
    public static func periodTotals(
        accounts: [Account],
        transactions: [Transaction],
        start: EpochMillis,
        end: EpochMillis
    ) -> PeriodTotals {
        let scope = PersonalStatistics.scope(accounts: accounts, transactions: transactions)
        let currencyByAccount = Dictionary(uniqueKeysWithValues: scope.accounts.map { ($0.id, $0.currencyCode) })
        var income: [String: MinorUnits] = [:]
        var expense: [String: MinorUnits] = [:]
        for transaction in scope.transactions where transaction.date >= start && transaction.date < end {
            guard let currency = currencyByAccount[transaction.accountId] else { continue }
            switch transaction.type {
            case .income: income[currency, default: 0] += transaction.amount
            case .expense: expense[currency, default: 0] += transaction.amount
            case .transfer: break
            }
        }
        let order = currencyOrder(of: accounts)
        return PeriodTotals(income: sorted(income, by: order), expense: sorted(expense, by: order))
    }

    /// Ordre d'affichage des devises : celui de leur première apparition parmi les comptes inclus.
    public static func currencyOrder(of accounts: [Account]) -> [String] {
        var order: [String] = []
        for account in accounts where !account.isExcludedFromStatistics && !order.contains(account.currencyCode) {
            order.append(account.currencyCode)
        }
        return order
    }

    /// [amounts] triés selon [order] (devises inconnues de [order] à la fin, par code).
    public static func sorted(_ amounts: [String: MinorUnits], by order: [String]) -> [CurrencyAmount] {
        amounts
            .map { CurrencyAmount(currencyCode: $0.key, amountMinor: $0.value) }
            .sorted { lhs, rhs in
                let left = order.firstIndex(of: lhs.currencyCode) ?? Int.max
                let right = order.firstIndex(of: rhs.currencyCode) ?? Int.max
                return left != right ? left < right : lhs.currencyCode < rhs.currencyCode
            }
    }

    /// Identifiants des transactions qui sont la « transaction de frais » d'une autre. Elles ne
    /// s'affichent jamais comme une ligne à part (le frais apparaît sur sa transaction parente),
    /// mais comptent normalement dans les soldes et les dépenses.
    public static func feeTransactionIds(_ transactions: [Transaction]) -> Set<EntityID> {
        Set(transactions.compactMap(\.feeTransactionId))
    }

    private static func millis(_ date: Date) -> EpochMillis {
        EpochMillis((date.timeIntervalSince1970 * 1000).rounded())
    }
}
