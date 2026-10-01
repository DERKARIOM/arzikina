import ArzikinaDomain
import GRDB
import XCTest
@testable import ArzikinaData

/// Suppression d'une catégorie : refusée tant qu'elle est utilisée ou gérée par l'app.
final class CategoryRepositoryTests: XCTestCase {

    private var space: UserDataSpace!

    override func setUpWithError() throws {
        space = try UserDataSpace.inMemory()
    }

    override func tearDown() {
        space.close()
    }

    private func saveCategory(_ id: EntityID = "c", name: String = "Tontine") async throws {
        try await space.categories.save(ArzikinaDomain.Category(id: id, name: name, type: .expense))
    }

    private func execute(_ sql: String) async throws {
        try await space.database.writer.write { db in try db.execute(sql: sql) }
    }

    func testUnusedCategoryIsDeletedAndTheDeletionIsQueued() async throws {
        try await saveCategory()
        try await space.database.writer.write { db in try db.execute(sql: "DELETE FROM sync_queue") }

        let result = try await space.categories.delete(id: "c")

        XCTAssertEqual(result, .deleted)
        let category = try await space.categories.category(id: "c")
        XCTAssertNil(category)
        let queued = try await space.database.writer.read { db in
            try Row.fetchAll(db, sql: "SELECT entityType, operation FROM sync_queue")
        }
        XCTAssertEqual(queued.count, 1)
        XCTAssertEqual(queued.first?["operation"], "DELETE")
    }

    func testCategoryUsedByAnActiveTransactionIsKept() async throws {
        try await saveCategory()
        try await space.accounts.save(Account(id: "a", name: "A"))
        try await space.transactions.save(Transaction(id: "t", amount: 5, type: .expense, accountId: "a", categoryId: "c", date: 1))

        let refused = try await space.categories.delete(id: "c")
        XCTAssertEqual(refused, .inUse)
        let kept = try await space.categories.category(id: "c")
        XCTAssertNotNil(kept)

        try await space.transactions.delete(id: "t")
        let allowed = try await space.categories.delete(id: "c")
        XCTAssertEqual(allowed, .deleted, "Une transaction supprimée ne compte plus")
    }

    /// Chaque table qui référence une catégorie bloque la suppression (lignes reçues d'un autre
    /// appareil comprises) ; une ligne supprimée ne bloque pas.
    func testEveryReferencingTableBlocksDeletion() async throws {
        let inserts = [
            "INSERT INTO recurring_transactions (id, type, amount, accountId, categoryId, startDate, frequency, nextExecutionDate, triggerHour, triggerMinute, createdAt, updatedAt%@) VALUES ('x', 'EXPENSE', 1, 'a', 'c', 0, 'MONTHLY', 0, 8, 0, 0, 0%@)",
            "INSERT INTO budgets (id, categoryId, period, limitAmount, currencyCode, createdAt, updatedAt%@) VALUES ('x', 'c', 'MONTHLY', 1, 'XOF', 0, 0%@)",
            "INSERT INTO transaction_templates (id, name, type, amount, categoryId, accountId, createdAt, updatedAt%@) VALUES ('x', 'Pain', 'EXPENSE', 1, 'c', 'a', 0, 0%@)",
            "INSERT INTO financial_plan_items (id, planId, name, amount, categoryId, priority, status, createdAt, updatedAt%@) VALUES ('x', 'p', 'Riz', 1, 'c', 'HIGH', 'PLANNED', 0, 0%@)",
        ]
        XCTAssertEqual(inserts.count + 1, LocalCategoryRepository.referencingTables.count, "Une table ajoutée doit être testée ici")

        for (index, template) in inserts.enumerated() {
            let table = LocalCategoryRepository.referencingTables[index + 1]
            func row(categoryId: String, deleted: Bool) -> String {
                String(format: template, deleted ? ", deletedAt" : "", deleted ? ", 5" : "")
                    .replacingOccurrences(of: "'c'", with: "'\(categoryId)'")
            }

            try await saveCategory("deleted-ref-\(index)")
            try await execute(row(categoryId: "deleted-ref-\(index)", deleted: true))
            let ignored = try await space.categories.delete(id: "deleted-ref-\(index)")
            XCTAssertEqual(ignored, .deleted, "\(table) : ligne supprimée ignorée")
            try await execute("DELETE FROM \(table)")

            try await saveCategory("active-ref-\(index)")
            try await execute(row(categoryId: "active-ref-\(index)", deleted: false))
            let refused = try await space.categories.delete(id: "active-ref-\(index)")
            XCTAssertEqual(refused, .inUse, table)
            try await execute("DELETE FROM \(table)")
        }
    }

    func testAppManagedCategoriesAreNeverDeleted() async throws {
        try await saveCategory("fees", name: "Frais et commissions")
        let result = try await space.categories.delete(id: "fees")
        XCTAssertEqual(result, .managedAutomatically)
        let kept = try await space.categories.category(id: "fees")
        XCTAssertNotNil(kept)
    }

    func testDeletingAnAlreadyDeletedCategoryIsHarmless() async throws {
        let unknown = try await space.categories.delete(id: "nope")
        XCTAssertEqual(unknown, .deleted)
    }
}
