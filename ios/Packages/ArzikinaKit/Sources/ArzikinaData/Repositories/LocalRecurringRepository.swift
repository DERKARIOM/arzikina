import ArzikinaDomain
import Foundation
import GRDB

/// [RecurringRepository] adossé à la base locale. Toute écriture se fait en UNE transaction SQL et
/// est inscrite dans la file d'envoi.
public struct LocalRecurringRepository: RecurringRepository {

    private let database: AppDatabase
    private let now: Clock
    private let rules: SyncedStore<RecurringTransactionRecord>
    private let occurrences: SyncedStore<RecurringOccurrenceRecord>
    private let transactions: SyncedStore<TransactionRecord>

    public init(database: AppDatabase, now: @escaping Clock = Clocks.system) {
        self.database = database
        self.now = now
        rules = SyncedStore(database: database, entityType: .recurringTransactions, now: now)
        occurrences = SyncedStore(database: database, entityType: .recurringTransactionOccurrences, now: now)
        transactions = SyncedStore(database: database, entityType: .transactions, now: now)
    }

    // MARK: - Lecture

    public func observeOverview() -> AsyncStream<AutomationOverview> {
        database.observe { db in
            let rules = try RecurringTransactionRecord
                .filter(Column("deletedAt") == nil)
                .order(Column("nextExecutionDate"), Column("id"))
                .fetchAll(db).map(\.domain)
            let occurrences = try RecurringOccurrenceRecord.filter(Column("deletedAt") == nil).fetchAll(db).map(\.domain)
            let accounts = Dictionary(uniqueKeysWithValues: try LocalAccountRepository.activeAccounts(db).map { ($0.id, $0) })
            let categories = Dictionary(uniqueKeysWithValues: try CategoryRecord.filter(Column("deletedAt") == nil).fetchAll(db).map { ($0.id, $0.domain) })
            return AutomationOverview.make(rules: rules, occurrences: occurrences, accounts: accounts, categories: categories)
        }
    }

    // MARK: - Génération

    public func generateDueOccurrences(now instant: EpochMillis, calendar: Calendar) async throws -> Int {
        let timestamp = now()
        let store = self
        return try await database.writer.write { db in
            try store.reconcileDuplicates(db, timestamp: timestamp)
            var created = 0
            let activeRules = try RecurringTransactionRecord.filter(Column("deletedAt") == nil && Column("isActive") == true).fetchAll(db)
            for record in activeRules {
                let rule = record.domain
                let existing = Set(try Int64.fetchAll(db, sql: """
                    SELECT scheduledDate FROM recurring_transaction_occurrences WHERE recurringTransactionId = ? AND deletedAt IS NULL
                    """, arguments: [rule.id]))
                let plan = Automations.plan(for: rule, existingDates: existing, now: instant, calendar: calendar)
                for date in plan.newDates {
                    let occurrence = RecurringTransactionOccurrence(
                        id: Automations.occurrenceId(recurringTransactionId: rule.id, scheduledDate: date),
                        recurringTransactionId: rule.id,
                        scheduledDate: date,
                        createdAt: timestamp
                    )
                    // Identifiant déterministe : une ligne supprimée (rejet ancien, autre appareil)
                    // peut déjà le porter ; on ne recrée jamais une échéance supprimée.
                    guard try RecurringOccurrenceRecord.fetchOne(db, key: occurrence.id) == nil else { continue }
                    try store.occurrences.save(db, id: occurrence.id, createdAt: timestamp, timestamp: timestamp) { RecurringOccurrenceRecord(occurrence, meta: $0) }
                    created += 1
                }
                if let updated = plan.updatedRule {
                    try store.rules.save(db, id: updated.id, createdAt: updated.createdAt, timestamp: timestamp) { RecurringTransactionRecord(updated, meta: $0) }
                }
            }
            return created
        }
    }

    /// Fusionne les échéances de même règle et même jour (générées par plusieurs appareils), voir
    /// `Automations.reconcile`.
    func reconcileDuplicates(_ db: Database, timestamp: Int64) throws {
        let groups = try Row.fetchAll(db, sql: """
            SELECT recurringTransactionId, scheduledDate FROM recurring_transaction_occurrences
            WHERE deletedAt IS NULL GROUP BY recurringTransactionId, scheduledDate HAVING COUNT(*) > 1
            """)
        for group in groups {
            let records = try RecurringOccurrenceRecord.filter(
                Column("recurringTransactionId") == (group["recurringTransactionId"] as String)
                    && Column("scheduledDate") == (group["scheduledDate"] as Int64)
                    && Column("deletedAt") == nil
            ).fetchAll(db)
            let stored = records.map { Automations.StoredOccurrence(occurrence: $0.domain, isKnownByServer: $0.version > 0) }
            guard let plan = Automations.reconcile(stored) else { continue }
            if let keeper = plan.updatedKeeper {
                try occurrences.save(db, id: keeper.id, createdAt: keeper.createdAt, timestamp: timestamp) { RecurringOccurrenceRecord(keeper, meta: $0) }
            }
            for id in plan.removedIds {
                try occurrences.softDelete(db, id: id, timestamp: timestamp)
            }
            for id in plan.duplicateTransactionIds {
                try transactions.softDelete(db, id: id, timestamp: timestamp)
            }
        }
    }

    // MARK: - Validation

    public func accept(occurrenceId: EntityID) async throws {
        let timestamp = now()
        let store = self
        try await database.writer.write { db in
            var occurrence = try store.pendingOccurrence(db, id: occurrenceId)
            guard let rule = try RecurringTransactionRecord.fetchOne(db, key: occurrence.recurringTransactionId)?.domain else {
                throw RecurringWriteError.ruleNotFound
            }
            let transaction = Automations.transaction(for: occurrence, rule: rule, id: EntityIDs.generate(), now: timestamp, calendar: ArzikinaCalendar.current)
            try store.transactions.save(db, id: transaction.id, createdAt: timestamp, timestamp: timestamp) { TransactionRecord(transaction, meta: $0) }
            occurrence.status = .accepted
            occurrence.transactionId = transaction.id
            occurrence.processedAt = timestamp
            try store.occurrences.save(db, id: occurrence.id, createdAt: occurrence.createdAt, timestamp: timestamp) { [occurrence] in RecurringOccurrenceRecord(occurrence, meta: $0) }
        }
    }

    public func reject(occurrenceId: EntityID) async throws {
        let timestamp = now()
        let store = self
        try await database.writer.write { db in
            var occurrence = try store.pendingOccurrence(db, id: occurrenceId)
            occurrence.status = .rejected
            occurrence.processedAt = timestamp
            try store.occurrences.save(db, id: occurrence.id, createdAt: occurrence.createdAt, timestamp: timestamp) { [occurrence] in RecurringOccurrenceRecord(occurrence, meta: $0) }
        }
    }

    public func setActive(ruleId: EntityID, isActive: Bool) async throws {
        let timestamp = now()
        let store = self
        try await database.writer.write { db in
            guard var rule = try RecurringTransactionRecord.fetchOne(db, key: ruleId).flatMap({ $0.deletedAt == nil ? $0.domain : nil }) else {
                throw RecurringWriteError.ruleNotFound
            }
            guard rule.isActive != isActive else { return }
            rule.isActive = isActive
            try store.rules.save(db, id: rule.id, createdAt: rule.createdAt, timestamp: timestamp) { [rule] in RecurringTransactionRecord(rule, meta: $0) }
        }
    }

    /// Échéance encore en attente (elle a pu être traitée entre-temps sur un autre appareil).
    private func pendingOccurrence(_ db: Database, id: EntityID) throws -> RecurringTransactionOccurrence {
        guard let record = try RecurringOccurrenceRecord.fetchOne(db, key: id), record.deletedAt == nil,
              record.status == OccurrenceStatus.pending.rawValue
        else { throw RecurringWriteError.occurrenceNotPending }
        return record.domain
    }
}
