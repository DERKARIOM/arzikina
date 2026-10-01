import ArzikinaDomain
import Foundation
import XCTest
@testable import ArzikinaData

/// Le solde calculé en SQL par la base locale doit être identique à la règle partagée
/// (`AccountBalances`, elle-même vérifiée contre Android) : même fixture `account-balances.json`.
final class BalanceParityTests: XCTestCase {

    func testSqlBalancesMatchSharedFixture() async throws {
        let fixture = try loadFixture("account-balances.json")
        let space = try UserDataSpace.inMemory()
        defer { space.close() }

        for item in fixture["accounts"] as! [[String: Any]] {
            let id = item["id"] as! String
            try await space.accounts.save(Account(id: id, name: id, initialBalance: (item["initialBalance"] as! NSNumber).int64Value))
        }
        var domainTransactions: [Transaction] = []
        for (index, item) in (fixture["transactions"] as! [[String: Any]]).enumerated() {
            let transaction = Transaction(
                id: "t\(index)",
                amount: (item["amount"] as! NSNumber).int64Value,
                type: TransactionType(rawValue: item["type"] as! String)!,
                accountId: item["accountId"] as! String,
                transferAccountId: item["transferAccountId"] as? String,
                date: EpochMillis(index)
            )
            domainTransactions.append(transaction)
            try await space.transactions.save(transaction)
        }
        // Une transaction supprimée ne doit plus peser dans le solde.
        try await space.transactions.save(Transaction(id: "deleted", amount: 999_999, type: .income, accountId: "cash", date: 0))
        try await space.transactions.delete(id: "deleted")

        var iterator = space.accounts.observeBalances().makeAsyncIterator()
        let firstBalances = await iterator.next()
        let sqlBalances = try XCTUnwrap(firstBalances)

        var expected: [String: Int64] = [:]
        for (key, value) in fixture["expected"] as! [String: Any] {
            expected[key] = (value as! NSNumber).int64Value
        }
        XCTAssertEqual(sqlBalances, expected)

        var iteratorAccounts = space.accounts.observeAccounts().makeAsyncIterator()
        let firstAccounts = await iteratorAccounts.next()
        let accounts = try XCTUnwrap(firstAccounts)
        XCTAssertEqual(sqlBalances, AccountBalances.compute(accounts: accounts, transactions: domainTransactions))
    }

    private func loadFixture(_ name: String) throws -> [String: Any] {
        var url = URL(fileURLWithPath: #filePath)
        for _ in 0..<6 { url.deleteLastPathComponent() }
        let data = try Data(contentsOf: url.appendingPathComponent("shared/test-fixtures/\(name)"))
        return try XCTUnwrap(try JSONSerialization.jsonObject(with: data) as? [String: Any])
    }
}
