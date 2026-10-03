import ArzikinaDomain
import GRDB
import XCTest
@testable import ArzikinaData

final class BackupRepositoryTests: XCTestCase {

    private var space: UserDataSpace!

    override func setUpWithError() throws {
        space = try UserDataSpace.inMemory()
    }

    override func tearDown() {
        space.close()
    }

    /// Lignes vivantes seulement ; planifications (reçues par la synchronisation) et préférences
    /// comprises.
    func testSnapshotReadsEveryLiveRow() async throws {
        try await space.accounts.save(Account(id: "a", name: "Caisse", createdAt: 1))
        try await space.database.writer.write { db in
            try db.execute(sql: """
                INSERT INTO transaction_templates (id, name, type, amount, categoryId, accountId, createdAt, updatedAt, deletedAt)
                VALUES ('gone', 'Pain', 'EXPENSE', 1, 'c', 'a', 0, 0, 5)
                """)
            try db.execute(sql: """
                INSERT INTO financial_plans (id, name, availableAmount, periodType, icon, colorArgb, status, createdAt, updatedAt)
                VALUES ('plan', 'Mariage', 500000, 'NONE', 'RING', 1, 'ACTIVE', 3, 3)
                """)
            try db.execute(sql: """
                INSERT INTO financial_plan_items (id, planId, name, amount, priority, status, createdAt, updatedAt)
                VALUES ('item', 'plan', 'Salle', 200000, 'ESSENTIAL', 'TO_PLAN', 4, 4)
                """)
            try db.execute(sql: """
                INSERT INTO user_preferences (id, themeMode, currencyCode, createdAt, updatedAt)
                VALUES ('prefs', 'LIGHT', 'XAF', 0, 0)
                """)
        }

        let snapshot = try await space.backup.snapshot()
        XCTAssertEqual(snapshot.accounts.map(\.id), ["a"])
        XCTAssertTrue(snapshot.templates.isEmpty, "Un modèle supprimé n'est pas exporté")
        XCTAssertEqual(snapshot.plans.first?.icon, .ring)
        XCTAssertEqual(snapshot.planItems.first?.priority, .essential)
        XCTAssertEqual(snapshot.themeMode, "LIGHT")
        XCTAssertEqual(snapshot.currencyCode, "XAF")

        let (document, summary) = AndroidBackup.make(snapshot, exportedAt: 10)
        XCTAssertEqual(document.financialPlanItems.first?.planId, document.financialPlans.first?.id)
        XCTAssertEqual(summary.plans, 1)
    }

    func testEmptyDatabaseGivesAnEmptyButValidSnapshot() async throws {
        let snapshot = try await space.backup.snapshot()
        XCTAssertNil(snapshot.themeMode)
        let (document, summary) = AndroidBackup.make(snapshot, exportedAt: 10)
        XCTAssertEqual(document.preferences.currencyCode, "XOF")
        XCTAssertEqual(summary, BackupSummary())
    }
}
