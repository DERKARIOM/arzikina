import ArzikinaDomain
import GRDB

/// [TransactionLedgerRepository] adossé à la base locale.
///
/// Les soldes « après chaque transaction » sont calculés pour CHAQUE compte en un seul passage
/// sur la liste (même règle et même ordre que le détail d'un compte), pour que la liste globale
/// et le détail affichent toujours le même solde pour une même ligne.
public struct LocalTransactionLedgerRepository: TransactionLedgerRepository {

    private let database: AppDatabase

    public init(database: AppDatabase) {
        self.database = database
    }

    public func observeLedger() -> AsyncStream<[TransactionLedgerEntry]> {
        database.observe { db in try Self.ledger(db) }
    }

    static func ledger(_ db: Database) throws -> [TransactionLedgerEntry] {
        // Même ordre que `LocalAccountOverviewRepository.detail` : `id` départage les égalités.
        let transactions = try TransactionRecord.fetchAll(db, sql: """
            SELECT * FROM transactions
            WHERE deletedAt IS NULL
            ORDER BY date DESC, createdAt DESC, id DESC
            """).map(\.domain)
        let accounts = try LocalAccountRepository.activeAccounts(db)
        let accountsById = Dictionary(uniqueKeysWithValues: accounts.map { ($0.id, $0) })
        let categoryRecords = try CategoryRecord.filter(Column("deletedAt") == nil).fetchAll(db)
        let categoriesById = Dictionary(uniqueKeysWithValues: categoryRecords.map { ($0.id, $0.domain) })

        let balances = runningBalances(transactions, accounts: accounts)
        let amountsById = Dictionary(transactions.map { ($0.id, $0.amount) }, uniquingKeysWith: { first, _ in first })
        let feeIds = Set(transactions.compactMap(\.feeTransactionId))

        return transactions
            .filter { !feeIds.contains($0.id) }
            .map { transaction in
                let destinationId = transaction.type == .transfer ? transaction.transferAccountId : nil
                return TransactionLedgerEntry(
                    transaction: transaction,
                    sourceAccount: accountsById[transaction.accountId],
                    destinationAccount: destinationId.flatMap { accountsById[$0] },
                    category: transaction.categoryId.flatMap { categoriesById[$0] },
                    feeAmount: transaction.feeTransactionId.flatMap { amountsById[$0] },
                    sourceBalanceAfter: balances[transaction.accountId]?[transaction.id],
                    destinationBalanceAfter: destinationId.flatMap { balances[$0]?[transaction.id] }
                )
            }
    }

    /// Solde après chaque transaction, par compte puis par transaction. [newestFirst] contient
    /// toutes les transactions non supprimées, frais compris.
    private static func runningBalances(_ newestFirst: [Transaction], accounts: [Account]) -> [EntityID: [EntityID: MinorUnits]] {
        var byAccount: [EntityID: [Transaction]] = [:]
        for transaction in newestFirst {
            byAccount[transaction.accountId, default: []].append(transaction)
            if transaction.type == .transfer, let destination = transaction.transferAccountId, destination != transaction.accountId {
                byAccount[destination, default: []].append(transaction)
            }
        }
        var result: [EntityID: [EntityID: MinorUnits]] = [:]
        for account in accounts {
            result[account.id] = RunningBalances.compute(account: account, transactions: byAccount[account.id] ?? [])
        }
        return result
    }
}
