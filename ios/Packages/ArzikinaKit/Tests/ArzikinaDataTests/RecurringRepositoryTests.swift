import ArzikinaDomain
import GRDB
import XCTest
@testable import ArzikinaData

final class RecurringRepositoryTests: XCTestCase {

    private var space: UserDataSpace!
    private let calendar = ArzikinaCalendar.make(timeZone: TimeZone(identifier: "Africa/Niamey")!)

    override func setUpWithError() throws {
        space = try UserDataSpace.inMemory()
    }

    override func tearDown() {
        space.close()
    }

    private func day(_ month: Int, _ day: Int, hour: Int = 0) -> EpochMillis {
        CalendarDay(year: 2026, month: month, day: day).millis(hour: hour, minute: 0, calendar: calendar)
    }

    private func insertRule(next: EpochMillis, frequency: RecurringFrequency = .monthly) async throws {
        try await space.accounts.save(Account(id: "a", name: "A", initialBalance: 100_000))
        try await space.database.writer.write { db in
            let rule = RecurringTransaction(id: "r", type: .expense, amount: 5_000, accountId: "a", description: "Loyer", startDate: next, frequency: frequency, nextExecutionDate: next, triggerHour: 8, triggerMinute: 30, createdAt: 1)
            var record = RecurringTransactionRecord(rule, meta: SyncMetadata(createdAt: 1, updatedAt: 1, deletedAt: nil, version: 3))
            record.version = 3
            try record.insert(db)
        }
    }

    private func overview() async throws -> AutomationOverview {
        var iterator = space.recurring.observeOverview().makeAsyncIterator()
        let value = await iterator.next()
        return try XCTUnwrap(value)
    }

    func testGenerationIsIdempotentAndAdvancesTheRule() async throws {
        try await insertRule(next: day(8, 31))
        let first = try await space.recurring.generateDueOccurrences(now: day(9, 30, hour: 9), calendar: calendar)
        let second = try await space.recurring.generateDueOccurrences(now: day(9, 30, hour: 9), calendar: calendar)
        XCTAssertEqual(first, 2)
        XCTAssertEqual(second, 0, "Rien n'est généré deux fois")

        let state = try await overview()
        XCTAssertEqual(state.pending.map(\.scheduledDate), [day(8, 31), day(9, 30)])
        XCTAssertEqual(state.pending.first?.id, Automations.occurrenceId(recurringTransactionId: "r", scheduledDate: day(8, 31)))
        XCTAssertEqual(state.upcoming.map(\.scheduledDate), [day(10, 30)])
    }

    func testAcceptCreatesTheTransactionAtTheTriggerTimeAndRejectCreatesNothing() async throws {
        try await insertRule(next: day(8, 31))
        try await space.recurring.generateDueOccurrences(now: day(9, 30, hour: 9), calendar: calendar)
        let ids = try await overview().pending.compactMap(\.occurrence?.id)

        try await space.recurring.accept(occurrenceId: ids[0])
        try await space.recurring.reject(occurrenceId: ids[1])
        do {
            try await space.recurring.accept(occurrenceId: ids[1])
            XCTFail("Déjà traitée")
        } catch let error as RecurringWriteError {
            XCTAssertEqual(error, .occurrenceNotPending)
        }

        let state = try await overview()
        XCTAssertTrue(state.pending.isEmpty)
        let accepted = try XCTUnwrap(state.history.first { $0.occurrence?.status == .accepted }?.occurrence)
        let transactionId = try XCTUnwrap(accepted.transactionId)
        let transaction = try await space.transactions.transaction(id: transactionId)
        XCTAssertEqual(transaction?.amount, 5_000)
        XCTAssertEqual(transaction?.date, CalendarDay(year: 2026, month: 8, day: 31).millis(hour: 8, minute: 30, calendar: calendar))
        XCTAssertEqual(transaction?.description, "Loyer")
        XCTAssertNil(state.history.first { $0.occurrence?.status == .rejected }?.occurrence?.transactionId)
    }

    /// Android a généré la même échéance (autre identifiant) pendant que l'iPhone la validait.
    func testDuplicatesFromAnotherDeviceAreMerged() async throws {
        try await insertRule(next: day(9, 30))
        try await space.recurring.generateDueOccurrences(now: day(9, 30, hour: 9), calendar: calendar)
        let before = try await overview()
        let localId = try XCTUnwrap(before.pending.first?.occurrence?.id)
        try await space.recurring.accept(occurrenceId: localId)

        // Reçue par synchronisation : connue du serveur (version 1), encore en attente.
        try await space.database.writer.write { db in
            try db.execute(sql: """
                INSERT INTO recurring_transaction_occurrences (id, recurringTransactionId, scheduledDate, status, createdAt, updatedAt, version)
                VALUES ('android', 'r', ?, 'PENDING', 0, 0, 1)
                """, arguments: [self.day(9, 30)])
        }
        try await space.recurring.generateDueOccurrences(now: day(9, 30, hour: 10), calendar: calendar)

        let state = try await overview()
        XCTAssertTrue(state.pending.isEmpty, "Plus de doublon en attente")
        XCTAssertEqual(state.history.map(\.id), ["android"])
        XCTAssertEqual(state.history.first?.occurrence?.status, .accepted, "La validation de l'iPhone est reportée sur la copie du serveur")
        let queue = try await space.database.writer.read { db in
            try Row.fetchAll(db, sql: "SELECT entityId, operation FROM sync_queue WHERE entityType = 'recurring_transaction_occurrences'")
        }
        XCTAssertEqual(queue.count, 1, "La copie locale, jamais envoyée, disparaît sans rien envoyer")
        XCTAssertEqual(queue.first?["entityId"], "android")
        XCTAssertEqual(queue.first?["operation"], "UPDATE")
    }

    func testPausedRulesGenerateNothing() async throws {
        try await insertRule(next: day(8, 31))
        try await space.recurring.setActive(ruleId: "r", isActive: false)
        let created = try await space.recurring.generateDueOccurrences(now: day(12, 1), calendar: calendar)
        XCTAssertEqual(created, 0)
        let state = try await overview()
        XCTAssertTrue(state.upcoming.isEmpty)
        XCTAssertEqual(state.rules.first?.isActive, false)
    }

    // MARK: - Plusieurs appareils (faux serveur avec la contrainte d'unicité règle + jour)

    private func syncRuleToServer(_ server: FakeSyncServer) async throws {
        try await insertRule(next: day(9, 30))
        try await space.database.writer.write { db in
            try db.execute(sql: "UPDATE recurring_transactions SET version = 0")
            try SyncQueue.enqueue(db, type: .accounts, entityId: "a", operation: .create, now: 1)
            try SyncQueue.enqueue(db, type: .recurringTransactions, entityId: "r", operation: .create, now: 1)
        }
        _ = try await SyncEngine(space: space, remote: server, accessToken: { "t" }, now: Clocks.system).synchronize()
    }

    func testTwoIPhonesGenerateTheSameOccurrenceOnce() async throws {
        let server = FakeSyncServer()
        try await syncRuleToServer(server)
        let other = try UserDataSpace.inMemory(userId: "u1")
        defer { other.close() }
        let engineA = SyncEngine(space: space, remote: server, accessToken: { "t" }, now: Clocks.system)
        let engineB = SyncEngine(space: other, remote: server, accessToken: { "t" }, now: Clocks.system)
        _ = try await engineB.synchronize() // reçoit la règle

        // Les deux génèrent hors ligne, puis synchronisent.
        try await space.recurring.generateDueOccurrences(now: day(9, 30, hour: 9), calendar: calendar)
        try await other.recurring.generateDueOccurrences(now: day(9, 30, hour: 9), calendar: calendar)
        let reportA = try await engineA.synchronize()
        let reportB = try await engineB.synchronize()

        XCTAssertEqual(reportA.failed, 0)
        XCTAssertEqual(reportB.failed, 0, "Même identifiant : le second envoi est une répétition acceptée")
        XCTAssertEqual(server.count(.recurringTransactionOccurrences), 1)
    }

    func testAnIPhoneOccurrenceCollidingWithAndroidsIsDroppedAfterSync() async throws {
        let server = FakeSyncServer()
        try await syncRuleToServer(server)
        // Android a déjà envoyé SA copie (identifiant aléatoire).
        _ = server.otherDevice(.recurringTransactionOccurrences, .create, [
            "id": .string("android"), "recurringTransactionSyncId": .string("r"), "scheduledDate": .int(day(9, 30)),
            "status": .string("PENDING"), "baseVersion": .null
        ])
        // L'iPhone génère la sienne hors ligne, puis synchronise : envoi refusé, copie Android reçue.
        try await space.recurring.generateDueOccurrences(now: day(9, 30, hour: 9), calendar: calendar)
        let engine = SyncEngine(space: space, remote: server, accessToken: { "t" }, now: Clocks.system)
        _ = try await engine.synchronize()
        try await space.recurring.generateDueOccurrences(now: day(9, 30, hour: 9), calendar: calendar)
        let report = try await engine.synchronize()

        XCTAssertEqual(report.failed, 0, "Plus rien de bloqué dans la file d'envoi")
        let state = try await overview()
        XCTAssertEqual(state.pending.map(\.id), ["android"])
        let pending = try await space.pendingChangesCount()
        XCTAssertEqual(pending, 0)
    }
}
