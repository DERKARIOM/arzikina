import ArzikinaDomain
import XCTest

final class AndroidBackupTests: XCTestCase {

    private let exportedAt: EpochMillis = 1_790_000_000_000

    /// Deux comptes, une catégorie, une dépense avec frais, un prêt remboursé une fois.
    private func snapshot() -> BackupSnapshot {
        BackupSnapshot(
            accounts: [
                Account(id: "acc-b", name: "Orange Money", type: .mobileMoney, createdAt: 20),
                Account(id: "acc-a", name: "Caisse", createdAt: 10)
            ],
            categories: [Category(id: "cat", name: "Food", icon: .food, type: .expense, createdAt: 1)],
            transactions: [
                Transaction(id: "tx-fee", amount: 100, type: .expense, accountId: "acc-b", date: 50, feeType: .transfer, createdAt: 31),
                Transaction(id: "tx-main", amount: 5_000, type: .expense, accountId: "acc-b", categoryId: "cat", date: 50, paymentMethod: .mobileMoney, feeTransactionId: "tx-fee", createdAt: 30),
                Transaction(id: "tx-loan", amount: 10_000, type: .expense, accountId: "acc-a", date: 60, createdAt: 40),
                Transaction(id: "tx-pay", amount: 2_000, type: .income, accountId: "acc-a", date: 70, createdAt: 41)
            ],
            persons: [Person(id: "p", name: "Aïcha", createdAt: 2)],
            loans: [Loan(id: "loan", personId: "p", accountId: "acc-a", type: .lent, amount: 10_000, amountRepaid: 2_000, remainingAmount: 8_000, startDate: 60, dueDate: 90, transactionId: "tx-loan", createdAt: 40)],
            loanPayments: [LoanPayment(id: "pay", loanId: "loan", accountId: "acc-a", amount: 2_000, date: 70, transactionId: "tx-pay", createdAt: 41)],
            themeMode: "DARK",
            currencyCode: "XOF"
        )
    }

    func testIdsAreNumberedByCreationAndReferencesFollow() {
        let (document, summary) = AndroidBackup.make(snapshot(), exportedAt: exportedAt)
        XCTAssertEqual(document.accounts.map(\.name), ["Caisse", "Orange Money"])
        XCTAssertEqual(document.accounts.map(\.id), [1, 2])
        XCTAssertEqual(document.transactions.map(\.id), [1, 2, 3, 4])
        let main = document.transactions[0]
        XCTAssertEqual(main.amount, 5_000)
        XCTAssertEqual(main.accountId, 2, "Orange Money")
        XCTAssertEqual(main.categoryId, 1)
        XCTAssertEqual(main.feeTransactionId, 2, "Les frais pointent vers la transaction numérotée après elle")
        XCTAssertEqual(main.paymentMethod, "MOBILE_MONEY")
        XCTAssertEqual(document.transactions[1].feeType, "TRANSFER")
        XCTAssertEqual(document.loans.first?.transactionId, 3)
        XCTAssertEqual(document.loans.first?.personId, 1)
        XCTAssertEqual(document.loanPayments.first?.loanId, 1)
        XCTAssertEqual(document.loanPayments.first?.transactionId, 4)
        XCTAssertEqual(summary.accounts, 2)
        XCTAssertEqual(summary.transactions, 4)
        XCTAssertEqual(summary.loans, 1)
        XCTAssertEqual(summary.skipped, 0)
        XCTAssertEqual(document.schemaVersion, 1)
        XCTAssertEqual(document.preferences, .init(themeMode: "DARK", currencyCode: "XOF"))
        XCTAssertTrue(document.savingsGoals.isEmpty && document.receipts.isEmpty)
    }

    /// Android refuse TOUT le fichier si une référence obligatoire manque (`getValue`) : la ligne
    /// est écartée, avec ses dépendants ; une référence facultative est simplement vidée.
    func testDanglingReferencesNeverReachTheFile() {
        var data = snapshot()
        data.transactions.append(Transaction(id: "orphan", amount: 1, type: .expense, accountId: "deleted-account", date: 1, createdAt: 99))
        data.transactions.append(Transaction(id: "soft", amount: 1, type: .expense, accountId: "acc-a", categoryId: "deleted-category", date: 1, createdAt: 98))
        data.budgets = [Budget(id: "b", categoryId: "deleted-category", limitAmount: 1)]
        data.loans[0].transactionId = "deleted-transaction"
        data.templates = [TransactionTemplate(id: "tpl", name: "Riz", type: .expense, amount: 1, categoryId: "cat", accountId: "acc-a", sourceTransactionId: "orphan")]

        let (document, summary) = AndroidBackup.make(data, exportedAt: exportedAt)
        XCTAssertEqual(document.transactions.count, 5, "L'orpheline est écartée, l'autre gardée")
        XCTAssertNil(document.transactions.last?.categoryId, "Catégorie supprimée : référence vidée")
        XCTAssertTrue(document.budgets.isEmpty)
        XCTAssertTrue(document.loans.isEmpty)
        XCTAssertTrue(document.loanPayments.isEmpty, "Le remboursement suit son prêt")
        XCTAssertEqual(document.transactionTemplates.count, 1)
        XCTAssertNil(document.transactionTemplates.first?.sourceTransactionId)
        XCTAssertEqual(summary.skipped, 4, "transaction, budget, prêt, remboursement")
    }

    func testPreferencesFallBackToAndroidDefaults() {
        var data = snapshot()
        data.themeMode = "AUTO"
        data.currencyCode = ""
        let (document, _) = AndroidBackup.make(data, exportedAt: exportedAt)
        XCTAssertEqual(document.preferences, .init(themeMode: "SYSTEM", currencyCode: "XOF"))
    }

    func testJsonUsesAndroidKeysAndOmitsMissingValues() throws {
        let (document, _) = AndroidBackup.make(snapshot(), exportedAt: exportedAt)
        let data = try AndroidBackup.encode(document)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(json["schemaVersion"] as? Int, 1)
        XCTAssertEqual((json["exportedAtEpochMillis"] as? NSNumber)?.int64Value, exportedAt)
        let account = try XCTUnwrap((json["accounts"] as? [[String: Any]])?.first)
        XCTAssertEqual(account["initialBalanceMinor"] as? Int, 0)
        XCTAssertEqual(account["type"] as? String, "CASH")
        XCTAssertNil(account["cardLastFourDigits"], "Valeur absente omise : Android applique son défaut")
        XCTAssertNotNil(json["transactionTemplates"])
        XCTAssertNil(json["user"], "Jamais de profil ni de mot de passe")
        XCTAssertEqual(try AndroidBackup.encode(document), data, "Même données, même fichier")
        // Relecture complète : le document est fidèle à lui-même.
        XCTAssertEqual(try JSONDecoder().decode(AndroidBackupDocument.self, from: data), document)
    }

    func testFileNameUsesTheLocalDay() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Africa/Niamey")!
        // 2026-10-01 23:30 UTC = 2 octobre 00:30 à Niamey.
        XCTAssertEqual(AndroidBackup.fileName(exportedAt: 1_790_897_400_000, calendar: calendar), "arzikina-backup-2026-10-02.json")
    }
}
