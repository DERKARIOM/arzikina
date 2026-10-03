import Foundation

/// Export au format de sauvegarde d'Android (`data/backup/BackupDto.kt`, schéma 1) : le fichier
/// produit sur iPhone se restaure dans l'app Android (Réglages › Sauvegarde › Restaurer).
///
/// Pas de restauration sur iOS (décision de l'étape 17) : le serveur est la sauvegarde de
/// référence. Recréer des données synchronisées avec de nouveaux identifiants créerait des
/// doublons sur tous les appareils, ou ferait revenir des éléments supprimés ailleurs.
///
/// Correspondance des identifiants : Android attend des entiers, iOS a des UUID de
/// synchronisation. Chaque table numérote ses lignes 1, 2, 3… ; Android les renumérote de toute
/// façon à l'import (`remapIds`). Les références OBLIGATOIRES pour Android (`getValue`) doivent
/// exister dans le fichier, sinon TOUTE la restauration échoue : une ligne qui désigne un élément
/// absent est donc écartée (et ses dépendants avec elle), une référence facultative est vidée.
///
/// Jamais exportés : profil et mot de passe (`user`, iOS n'en a aucun haché), reçus (absents
/// d'iOS), réglages propres à l'appareil (verrouillage).
public enum AndroidBackup {

    public static let schemaVersion = 1
    /// Devise par défaut d'Android (`Constants.DEFAULT_CURRENCY_CODE`).
    public static let defaultCurrencyCode = "XOF"
    private static let themeModes: Set<String> = ["SYSTEM", "LIGHT", "DARK"]

    /// Document à écrire et bilan de ce qu'il contient.
    public static func make(_ snapshot: BackupSnapshot, exportedAt: EpochMillis) -> (document: AndroidBackupDocument, summary: BackupSummary) {
        var summary = BackupSummary()
        var skipped = 0

        let accountIds = IdTable(snapshot.accounts.sorted(by: creation).map(\.id))
        let accounts = snapshot.accounts.sorted(by: creation).map { AndroidBackupDocument.AccountDTO($0, id: accountIds[$0.id]!) }

        let categoryIds = IdTable(snapshot.categories.sorted(by: creation).map(\.id))
        let categories = snapshot.categories.sorted(by: creation).map { AndroidBackupDocument.CategoryDTO($0, id: categoryIds[$0.id]!) }

        // Transactions : le compte est obligatoire ; les frais pointent vers une autre transaction
        // du fichier (numérotée APRÈS le tri, d'où les deux passes).
        let keptTransactions = snapshot.transactions.sorted(by: creation).filter { accountIds[$0.accountId] != nil }
        skipped += snapshot.transactions.count - keptTransactions.count
        let transactionIds = IdTable(keptTransactions.map(\.id))
        let transactions = keptTransactions.map { transaction in
            AndroidBackupDocument.TransactionDTO(
                transaction,
                id: transactionIds[transaction.id]!,
                accountId: accountIds[transaction.accountId]!,
                transferAccountId: transaction.transferAccountId.flatMap { accountIds[$0] },
                categoryId: transaction.categoryId.flatMap { categoryIds[$0] },
                feeTransactionId: transaction.feeTransactionId.flatMap { transactionIds[$0] }
            )
        }

        let keptBudgets = snapshot.budgets.sorted(by: creation).filter { categoryIds[$0.categoryId] != nil }
        skipped += snapshot.budgets.count - keptBudgets.count
        let budgets = keptBudgets.enumerated().map { index, budget in
            AndroidBackupDocument.BudgetDTO(budget, id: Int64(index + 1), categoryId: categoryIds[budget.categoryId]!)
        }

        let personIds = IdTable(snapshot.persons.sorted(by: creation).map(\.id))
        let persons = snapshot.persons.sorted(by: creation).map { AndroidBackupDocument.PersonDTO($0, id: personIds[$0.id]!) }

        let keptLoans = snapshot.loans.sorted(by: creation).filter {
            personIds[$0.personId] != nil && accountIds[$0.accountId] != nil && transactionIds[$0.transactionId] != nil
        }
        skipped += snapshot.loans.count - keptLoans.count
        let loanIds = IdTable(keptLoans.map(\.id))
        let loans = keptLoans.map { loan in
            AndroidBackupDocument.LoanDTO(
                loan,
                id: loanIds[loan.id]!,
                personId: personIds[loan.personId]!,
                accountId: accountIds[loan.accountId]!,
                transactionId: transactionIds[loan.transactionId]!,
                giftTransactionId: loan.giftTransactionId.flatMap { transactionIds[$0] }
            )
        }

        let keptPayments = snapshot.loanPayments.sorted(by: creation).filter {
            loanIds[$0.loanId] != nil && accountIds[$0.accountId] != nil && transactionIds[$0.transactionId] != nil
        }
        skipped += snapshot.loanPayments.count - keptPayments.count
        let loanPayments = keptPayments.enumerated().map { index, payment in
            AndroidBackupDocument.LoanPaymentDTO(
                payment,
                id: Int64(index + 1),
                loanId: loanIds[payment.loanId]!,
                accountId: accountIds[payment.accountId]!,
                transactionId: transactionIds[payment.transactionId]!
            )
        }

        let keptRules = snapshot.recurringRules.sorted(by: creation).filter { accountIds[$0.accountId] != nil }
        skipped += snapshot.recurringRules.count - keptRules.count
        let ruleIds = IdTable(keptRules.map(\.id))
        let rules = keptRules.map { rule in
            AndroidBackupDocument.RecurringTransactionDTO(
                rule,
                id: ruleIds[rule.id]!,
                accountId: accountIds[rule.accountId]!,
                categoryId: rule.categoryId.flatMap { categoryIds[$0] }
            )
        }

        let keptOccurrences = snapshot.occurrences.sorted(by: creation).filter { ruleIds[$0.recurringTransactionId] != nil }
        skipped += snapshot.occurrences.count - keptOccurrences.count
        let occurrences = keptOccurrences.enumerated().map { index, occurrence in
            AndroidBackupDocument.OccurrenceDTO(
                occurrence,
                id: Int64(index + 1),
                recurringTransactionId: ruleIds[occurrence.recurringTransactionId]!,
                transactionId: occurrence.transactionId.flatMap { transactionIds[$0] }
            )
        }

        let planIds = IdTable(snapshot.plans.sorted(by: creation).map(\.id))
        let plans = snapshot.plans.sorted(by: creation).map { AndroidBackupDocument.FinancialPlanDTO($0, id: planIds[$0.id]!) }

        let keptItems = snapshot.planItems.sorted(by: creation).filter { planIds[$0.planId] != nil }
        skipped += snapshot.planItems.count - keptItems.count
        let planItems = keptItems.enumerated().map { index, item in
            AndroidBackupDocument.FinancialPlanItemDTO(
                item,
                id: Int64(index + 1),
                planId: planIds[item.planId]!,
                categoryId: item.categoryId.flatMap { categoryIds[$0] },
                transactionId: item.transactionId.flatMap { transactionIds[$0] }
            )
        }

        let keptTemplates = snapshot.templates.sorted(by: creation).filter {
            accountIds[$0.accountId] != nil && categoryIds[$0.categoryId] != nil
        }
        skipped += snapshot.templates.count - keptTemplates.count
        let templates = keptTemplates.enumerated().map { index, template in
            AndroidBackupDocument.TransactionTemplateDTO(
                template,
                id: Int64(index + 1),
                accountId: accountIds[template.accountId]!,
                categoryId: categoryIds[template.categoryId]!,
                sourceTransactionId: template.sourceTransactionId.flatMap { transactionIds[$0] }
            )
        }

        let theme = snapshot.themeMode.flatMap { themeModes.contains($0) ? $0 : nil } ?? "SYSTEM"
        let currency = snapshot.currencyCode.flatMap { $0.isEmpty ? nil : $0 } ?? defaultCurrencyCode

        summary.accounts = accounts.count
        summary.categories = categories.count
        summary.transactions = transactions.count
        summary.budgets = budgets.count
        summary.loans = loans.count
        summary.automations = rules.count
        summary.plans = plans.count
        summary.templates = templates.count
        summary.skipped = skipped

        let document = AndroidBackupDocument(
            schemaVersion: schemaVersion,
            exportedAtEpochMillis: exportedAt,
            preferences: .init(themeMode: theme, currencyCode: currency),
            accounts: accounts,
            categories: categories,
            transactions: transactions,
            budgets: budgets,
            savingsGoals: [],
            persons: persons,
            loans: loans,
            loanPayments: loanPayments,
            recurringTransactions: rules,
            recurringTransactionOccurrences: occurrences,
            financialPlans: plans,
            financialPlanItems: planItems,
            receipts: [],
            transactionTemplates: templates
        )
        return (document, summary)
    }

    /// JSON indenté (lisible, comme Android `prettyPrint`), clés triées : deux exports des mêmes
    /// données donnent le même fichier.
    public static func encode(_ document: AndroidBackupDocument) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        return try encoder.encode(document)
    }

    /// « arzikina-backup-2026-10-02.json » (date du jour, calendrier de la personne).
    public static func fileName(exportedAt: EpochMillis, calendar: Calendar) -> String {
        let day = CalendarDay(epochMillis: exportedAt, calendar: calendar)
        return String(format: "arzikina-backup-%04d-%02d-%02d.json", day.year, day.month, day.day)
    }

    // MARK: - Aides

    /// Ordre stable : date de création, puis identifiant.
    private static func creation<T: BackupOrderable>(_ lhs: T, _ rhs: T) -> Bool {
        lhs.backupCreatedAt == rhs.backupCreatedAt ? lhs.id < rhs.id : lhs.backupCreatedAt < rhs.backupCreatedAt
    }

    /// UUID → entier du fichier (1, 2, 3… dans l'ordre donné).
    private struct IdTable {
        private let ids: [EntityID: Int64]
        init(_ entityIds: [EntityID]) {
            var ids: [EntityID: Int64] = [:]
            for id in entityIds where ids[id] == nil { ids[id] = Int64(ids.count + 1) }
            self.ids = ids
        }
        subscript(_ id: EntityID) -> Int64? { ids[id] }
    }
}

/// Entité triable par date de création pour la numérotation du fichier.
protocol BackupOrderable {
    var id: EntityID { get }
    var backupCreatedAt: EpochMillis { get }
}

extension Account: BackupOrderable { var backupCreatedAt: EpochMillis { createdAt } }
extension Category: BackupOrderable { var backupCreatedAt: EpochMillis { createdAt } }
extension Transaction: BackupOrderable { var backupCreatedAt: EpochMillis { createdAt } }
extension Budget: BackupOrderable { var backupCreatedAt: EpochMillis { createdAt } }
extension Person: BackupOrderable { var backupCreatedAt: EpochMillis { createdAt } }
extension Loan: BackupOrderable { var backupCreatedAt: EpochMillis { createdAt } }
extension LoanPayment: BackupOrderable { var backupCreatedAt: EpochMillis { createdAt } }
extension RecurringTransaction: BackupOrderable { var backupCreatedAt: EpochMillis { createdAt } }
extension RecurringTransactionOccurrence: BackupOrderable { var backupCreatedAt: EpochMillis { createdAt } }
extension FinancialPlan: BackupOrderable { var backupCreatedAt: EpochMillis { createdAt } }
extension FinancialPlanItem: BackupOrderable { var backupCreatedAt: EpochMillis { createdAt } }
extension TransactionTemplate: BackupOrderable { var backupCreatedAt: EpochMillis { createdAt } }
