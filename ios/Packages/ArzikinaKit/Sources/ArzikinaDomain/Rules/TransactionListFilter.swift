import Foundation

/// Filtres de la liste des transactions — Android `TransactionFilters`.
///
/// `accountId` / `categoryId` à `nil` signifient « tous ». La recherche libre ([query]) est à
/// part : elle a son propre champ (et son propre bouton « effacer »).
public struct TransactionFilters: Equatable, Sendable {

    /// Filtre par type. Un transfert n'apparaît qu'avec [all] (ni revenu ni dépense).
    public enum Kind: CaseIterable, Sendable {
        case all, income, expense
    }

    /// Période CIVILE en cours (semaine ISO, du lundi au dimanche, ou mois), comme Android.
    public enum Period: CaseIterable, Sendable {
        case all, thisWeek, thisMonth
    }

    public var query: String
    public var kind: Kind
    public var accountId: EntityID?
    public var categoryId: EntityID?
    public var period: Period

    public init(query: String = "", kind: Kind = .all, accountId: EntityID? = nil, categoryId: EntityID? = nil, period: Period = .all) {
        self.query = query
        self.kind = kind
        self.accountId = accountId
        self.categoryId = categoryId
        self.period = period
    }

    /// Exclut volontairement [query] (voir la doc du type).
    public var hasActiveFilters: Bool {
        kind != .all || accountId != nil || categoryId != nil || period != .all
    }

    /// Réinitialise les filtres en conservant la recherche — Android `resetFilters`.
    public mutating func resetFilters() {
        kind = .all
        accountId = nil
        categoryId = nil
        period = .all
    }
}

/// Une transaction de la liste globale, avec ce qu'il faut pour l'afficher de l'un OU l'autre
/// point de vue : celui de son compte source (par défaut) ou, pour un transfert consulté avec un
/// filtre sur son compte destination, celui du compte qui le reçoit.
///
/// Les transactions de frais n'ont jamais d'entrée propre : elles apparaissent via [feeAmount]
/// sur leur transaction parente (mais comptent dans les soldes).
public struct TransactionLedgerEntry: Identifiable, Equatable, Sendable {
    public var transaction: Transaction
    /// `nil` si la référence est orpheline (compte supprimé ou pas encore synchronisé).
    public var sourceAccount: Account?
    /// Compte destination d'un transfert, `nil` sinon.
    public var destinationAccount: Account?
    public var category: Category?
    public var feeAmount: MinorUnits?
    /// Solde du compte source juste APRÈS cette transaction.
    public var sourceBalanceAfter: MinorUnits?
    /// Solde du compte destination juste APRÈS ce transfert, `nil` hors transfert.
    public var destinationBalanceAfter: MinorUnits?

    public var id: EntityID { transaction.id }

    public init(
        transaction: Transaction,
        sourceAccount: Account?,
        destinationAccount: Account? = nil,
        category: Category? = nil,
        feeAmount: MinorUnits? = nil,
        sourceBalanceAfter: MinorUnits? = nil,
        destinationBalanceAfter: MinorUnits? = nil
    ) {
        self.transaction = transaction
        self.sourceAccount = sourceAccount
        self.destinationAccount = destinationAccount
        self.category = category
        self.feeAmount = feeAmount
        self.sourceBalanceAfter = sourceBalanceAfter
        self.destinationBalanceAfter = destinationBalanceAfter
    }
}

/// Recherche et filtres de la liste des transactions — même logique qu'Android
/// (`TransactionsViewModel.uiState`), appliquée en mémoire comme sur Android : pas de requête SQL
/// dynamique à maintenir pour chaque combinaison de filtres.
public enum TransactionListFilter {

    /// Noms recherchables d'un compte ou d'une catégorie : nom affiché (traduit pour un élément
    /// par défaut, ex. « Salary ») ET nom enregistré (« Salaire »), comme Android.
    public struct SearchableNames: Sendable {
        public var account: @Sendable (Account) -> [String]
        public var category: @Sendable (Category) -> [String]

        public init(account: @escaping @Sendable (Account) -> [String], category: @escaping @Sendable (Category) -> [String]) {
            self.account = account
            self.category = category
        }

        /// Noms enregistrés seulement (tests, ou tant qu'aucune traduction n'est fournie).
        public static let storedNames = SearchableNames(account: { [$0.name] }, category: { [$0.name] })
    }

    /// Lignes à afficher, dans l'ordre de [entries] (du plus récent au plus ancien).
    ///
    /// - Un transfert apparaît pour son compte source ET pour son compte destination. Filtré sur
    ///   le compte destination, la ligne passe au point de vue de ce compte (compte affiché, autre
    ///   compte, solde après) — sinon le transfert serait introuvable avec ce filtre.
    /// - [today] : jour de référence des périodes « cette semaine » / « ce mois ».
    public static func apply(
        _ entries: [TransactionLedgerEntry],
        filters: TransactionFilters,
        today: CalendarDay,
        calendar: Calendar,
        names: SearchableNames
    ) -> [TransactionListItem] {
        let query = filters.query.trimmingCharacters(in: .whitespacesAndNewlines)
        return entries.compactMap { entry in
            let transaction = entry.transaction
            guard matchesKind(transaction.type, filters.kind),
                  matchesAccount(transaction, filters.accountId),
                  filters.categoryId == nil || transaction.categoryId == filters.categoryId,
                  matchesPeriod(transaction.date, filters.period, today: today, calendar: calendar)
            else { return nil }

            let item = listItem(entry, viewedFrom: filters.accountId)
            guard matchesQuery(item, query, names: names) else { return nil }
            return item
        }
    }

    /// Ligne vue depuis le compte filtré : celui qui REÇOIT un transfert si c'est lui qui est
    /// filtré, le compte source dans tous les autres cas.
    static func listItem(_ entry: TransactionLedgerEntry, viewedFrom accountId: EntityID?) -> TransactionListItem {
        let transaction = entry.transaction
        let isTransferReceived = accountId != nil
            && transaction.type == .transfer
            && transaction.transferAccountId == accountId
            && transaction.accountId != accountId
        let isTransfer = transaction.type == .transfer
        return TransactionListItem(
            transaction: transaction,
            account: isTransferReceived ? entry.destinationAccount : entry.sourceAccount,
            transferAccount: isTransfer ? (isTransferReceived ? entry.sourceAccount : entry.destinationAccount) : nil,
            category: entry.category,
            feeAmount: entry.feeAmount,
            runningBalance: isTransferReceived ? entry.destinationBalanceAfter : entry.sourceBalanceAfter
        )
    }

    private static func matchesKind(_ type: TransactionType, _ kind: TransactionFilters.Kind) -> Bool {
        switch kind {
        case .all: return true
        case .income: return type == .income
        case .expense: return type == .expense
        }
    }

    private static func matchesAccount(_ transaction: Transaction, _ accountId: EntityID?) -> Bool {
        guard let accountId else { return true }
        return transaction.accountId == accountId
            || (transaction.type == .transfer && transaction.transferAccountId == accountId)
    }

    private static func matchesPeriod(_ date: EpochMillis, _ period: TransactionFilters.Period, today: CalendarDay, calendar: Calendar) -> Bool {
        switch period {
        case .all: return true
        case .thisWeek: return DatePeriods.isInCurrentPeriod(date, period: .weekly, today: today, calendar: calendar)
        case .thisMonth: return DatePeriods.isInCurrentPeriod(date, period: .monthly, today: today, calendar: calendar)
        }
    }

    /// Description, catégorie ou compte DE LA LIGNE contenant [query], sans tenir compte de la
    /// casse — comme Android (`contains(ignoreCase = true)`).
    private static func matchesQuery(_ item: TransactionListItem, _ query: String, names: SearchableNames) -> Bool {
        guard !query.isEmpty else { return true }
        func contains(_ text: String) -> Bool { text.range(of: query, options: .caseInsensitive) != nil }
        if contains(item.transaction.description) { return true }
        if let category = item.category, names.category(category).contains(where: contains) { return true }
        if let account = item.account, names.account(account).contains(where: contains) { return true }
        return false
    }
}
