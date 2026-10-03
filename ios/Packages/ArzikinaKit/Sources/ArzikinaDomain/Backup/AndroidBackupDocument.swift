import Foundation

/// Fichier de sauvegarde Android, champ pour champ (`BackupPayload` et ses DTO, schéma 1).
///
/// Les noms de propriétés SONT les clés JSON : ne pas les renommer. Une valeur facultative absente
/// est omise du fichier ; Android lui applique alors sa valeur par défaut (`null`).
public struct AndroidBackupDocument: Codable, Equatable, Sendable {
    public var schemaVersion: Int
    public var exportedAtEpochMillis: Int64
    public var preferences: PreferencesDTO
    public var accounts: [AccountDTO]
    public var categories: [CategoryDTO]
    public var transactions: [TransactionDTO]
    public var budgets: [BudgetDTO]
    /// Anciens objectifs d'épargne (Android) : toujours vide depuis iOS.
    public var savingsGoals: [EmptyDTO]
    public var persons: [PersonDTO]
    public var loans: [LoanDTO]
    public var loanPayments: [LoanPaymentDTO]
    public var recurringTransactions: [RecurringTransactionDTO]
    public var recurringTransactionOccurrences: [OccurrenceDTO]
    public var financialPlans: [FinancialPlanDTO]
    public var financialPlanItems: [FinancialPlanItemDTO]
    /// Reçus PDF (Android) : toujours vide depuis iOS.
    public var receipts: [EmptyDTO]
    public var transactionTemplates: [TransactionTemplateDTO]

    /// Élément d'une liste toujours vide.
    public struct EmptyDTO: Codable, Equatable, Sendable {}

    public struct PreferencesDTO: Codable, Equatable, Sendable {
        public var themeMode: String
        public var currencyCode: String

        public init(themeMode: String, currencyCode: String) {
            self.themeMode = themeMode
            self.currencyCode = currencyCode
        }
    }

    public struct AccountDTO: Codable, Equatable, Sendable {
        public var id: Int64
        public var name: String
        public var icon: String
        public var colorArgb: Int64
        public var currencyCode: String
        public var initialBalanceMinor: Int64
        public var createdAt: Int64
        public var type: String
        public var cardLastFourDigits: String?
        public var cardExpiryMonth: Int?
        public var cardExpiryYear: Int?
        public var isExcludedFromStatistics: Bool
        public var mobileMoneyPackageName: String?
        public var displayOrder: Int64
        public var savingsTargetAmount: Int64?
        public var savingsDescription: String?

        init(_ account: Account, id: Int64) {
            self.id = id
            name = account.name
            icon = account.icon.rawValue
            colorArgb = account.colorArgb
            currencyCode = account.currencyCode
            initialBalanceMinor = account.initialBalance
            createdAt = account.createdAt
            type = account.type.rawValue
            cardLastFourDigits = account.cardLastFourDigits
            cardExpiryMonth = account.cardExpiryMonth
            cardExpiryYear = account.cardExpiryYear
            isExcludedFromStatistics = account.isExcludedFromStatistics
            mobileMoneyPackageName = account.mobileMoneyPackageName
            displayOrder = account.displayOrder
            savingsTargetAmount = account.savingsTargetAmount
            savingsDescription = account.savingsDescription
        }
    }

    public struct CategoryDTO: Codable, Equatable, Sendable {
        public var id: Int64
        public var name: String
        public var icon: String
        public var colorArgb: Int64
        public var type: String
        public var createdAt: Int64

        init(_ category: Category, id: Int64) {
            self.id = id
            name = category.name
            icon = category.icon.rawValue
            colorArgb = category.colorArgb
            type = category.type.rawValue
            createdAt = category.createdAt
        }
    }

    public struct TransactionDTO: Codable, Equatable, Sendable {
        public var id: Int64
        public var amount: Int64
        public var type: String
        public var accountId: Int64
        public var transferAccountId: Int64?
        public var categoryId: Int64?
        public var date: Int64
        public var description: String
        public var latitude: Double?
        public var longitude: Double?
        public var paymentMethod: String?
        public var createdAt: Int64
        public var feeTransactionId: Int64?
        public var feeType: String?

        init(_ transaction: Transaction, id: Int64, accountId: Int64, transferAccountId: Int64?, categoryId: Int64?, feeTransactionId: Int64?) {
            self.id = id
            amount = transaction.amount
            type = transaction.type.rawValue
            self.accountId = accountId
            self.transferAccountId = transferAccountId
            self.categoryId = categoryId
            date = transaction.date
            description = transaction.description
            latitude = transaction.latitude
            longitude = transaction.longitude
            paymentMethod = transaction.paymentMethod?.rawValue
            createdAt = transaction.createdAt
            self.feeTransactionId = feeTransactionId
            feeType = transaction.feeType?.rawValue
        }
    }

    public struct BudgetDTO: Codable, Equatable, Sendable {
        public var id: Int64
        public var categoryId: Int64
        public var period: String
        public var limitAmount: Int64
        public var currencyCode: String
        public var createdAt: Int64
        public var startDate: Int64?
        public var endDate: Int64?

        init(_ budget: Budget, id: Int64, categoryId: Int64) {
            self.id = id
            self.categoryId = categoryId
            period = budget.period.rawValue
            limitAmount = budget.limitAmount
            currencyCode = budget.currencyCode
            createdAt = budget.createdAt
            startDate = budget.startDate
            endDate = budget.endDate
        }
    }

    public struct PersonDTO: Codable, Equatable, Sendable {
        public var id: Int64
        public var name: String
        public var phone: String?
        public var createdAt: Int64

        init(_ person: Person, id: Int64) {
            self.id = id
            name = person.name
            phone = person.phone
            createdAt = person.createdAt
        }
    }

    public struct LoanDTO: Codable, Equatable, Sendable {
        public var id: Int64
        public var personId: Int64
        public var accountId: Int64
        public var type: String
        public var amount: Int64
        public var amountRepaid: Int64
        public var remainingAmount: Int64
        public var startDate: Int64
        public var dueDate: Int64
        public var reason: String
        public var reasonCustomText: String?
        public var repaymentMode: String
        public var description: String
        public var status: String
        public var createdAt: Int64
        public var updatedAt: Int64
        public var transactionId: Int64
        public var giftedAmount: Int64
        public var giftTransactionId: Int64?
        public var giftedAt: Int64?

        init(_ loan: Loan, id: Int64, personId: Int64, accountId: Int64, transactionId: Int64, giftTransactionId: Int64?) {
            self.id = id
            self.personId = personId
            self.accountId = accountId
            type = loan.type.rawValue
            amount = loan.amount
            amountRepaid = loan.amountRepaid
            remainingAmount = loan.remainingAmount
            startDate = loan.startDate
            dueDate = loan.dueDate
            reason = loan.reason.rawValue
            reasonCustomText = loan.reasonCustomText
            repaymentMode = loan.repaymentMode.rawValue
            description = loan.description
            status = loan.status.rawValue
            createdAt = loan.createdAt
            updatedAt = loan.updatedAt
            self.transactionId = transactionId
            giftedAmount = loan.giftedAmount
            self.giftTransactionId = giftTransactionId
            giftedAt = loan.giftedAt
        }
    }

    public struct LoanPaymentDTO: Codable, Equatable, Sendable {
        public var id: Int64
        public var loanId: Int64
        public var accountId: Int64
        public var amount: Int64
        public var date: Int64
        public var note: String
        public var transactionId: Int64
        public var createdAt: Int64

        init(_ payment: LoanPayment, id: Int64, loanId: Int64, accountId: Int64, transactionId: Int64) {
            self.id = id
            self.loanId = loanId
            self.accountId = accountId
            amount = payment.amount
            date = payment.date
            note = payment.note
            self.transactionId = transactionId
            createdAt = payment.createdAt
        }
    }

    public struct RecurringTransactionDTO: Codable, Equatable, Sendable {
        public var id: Int64
        public var type: String
        public var amount: Int64
        public var accountId: Int64
        public var categoryId: Int64?
        public var description: String
        public var paymentMethod: String?
        public var startDate: Int64
        public var endDate: Int64?
        public var frequency: String
        public var nextExecutionDate: Int64
        public var isActive: Bool
        public var createdAt: Int64
        public var updatedAt: Int64
        public var triggerHour: Int
        public var triggerMinute: Int

        init(_ rule: RecurringTransaction, id: Int64, accountId: Int64, categoryId: Int64?) {
            self.id = id
            type = rule.type.rawValue
            amount = rule.amount
            self.accountId = accountId
            self.categoryId = categoryId
            description = rule.description
            paymentMethod = rule.paymentMethod?.rawValue
            startDate = rule.startDate
            endDate = rule.endDate
            frequency = rule.frequency.rawValue
            nextExecutionDate = rule.nextExecutionDate
            isActive = rule.isActive
            createdAt = rule.createdAt
            updatedAt = rule.updatedAt
            triggerHour = rule.triggerHour
            triggerMinute = rule.triggerMinute
        }
    }

    public struct OccurrenceDTO: Codable, Equatable, Sendable {
        public var id: Int64
        public var recurringTransactionId: Int64
        public var scheduledDate: Int64
        public var status: String
        public var transactionId: Int64?
        public var processedAt: Int64?
        public var createdAt: Int64

        init(_ occurrence: RecurringTransactionOccurrence, id: Int64, recurringTransactionId: Int64, transactionId: Int64?) {
            self.id = id
            self.recurringTransactionId = recurringTransactionId
            scheduledDate = occurrence.scheduledDate
            status = occurrence.status.rawValue
            self.transactionId = transactionId
            processedAt = occurrence.processedAt
            createdAt = occurrence.createdAt
        }
    }

    public struct FinancialPlanDTO: Codable, Equatable, Sendable {
        public var id: Int64
        public var name: String
        public var description: String?
        public var availableAmount: Int64
        public var targetAmount: Int64?
        public var periodType: String
        public var startDate: Int64?
        public var endDate: Int64?
        public var icon: String
        public var colorArgb: Int64
        public var status: String
        public var createdAt: Int64
        public var updatedAt: Int64

        init(_ plan: FinancialPlan, id: Int64) {
            self.id = id
            name = plan.name
            description = plan.description
            availableAmount = plan.availableAmount
            targetAmount = plan.targetAmount
            periodType = plan.periodType.rawValue
            startDate = plan.startDate
            endDate = plan.endDate
            icon = plan.icon.rawValue
            colorArgb = plan.colorArgb
            status = plan.status.rawValue
            createdAt = plan.createdAt
            updatedAt = plan.updatedAt
        }
    }

    public struct FinancialPlanItemDTO: Codable, Equatable, Sendable {
        public var id: Int64
        public var planId: Int64
        public var name: String
        public var amount: Int64
        public var actualAmount: Int64?
        public var categoryId: Int64?
        public var description: String?
        public var plannedDate: Int64?
        public var priority: String
        public var status: String
        public var transactionId: Int64?
        public var createdAt: Int64
        public var updatedAt: Int64

        init(_ item: FinancialPlanItem, id: Int64, planId: Int64, categoryId: Int64?, transactionId: Int64?) {
            self.id = id
            self.planId = planId
            name = item.name
            amount = item.amount
            actualAmount = item.actualAmount
            self.categoryId = categoryId
            description = item.description
            plannedDate = item.plannedDate
            priority = item.priority.rawValue
            status = item.status.rawValue
            self.transactionId = transactionId
            createdAt = item.createdAt
            updatedAt = item.updatedAt
        }
    }

    public struct TransactionTemplateDTO: Codable, Equatable, Sendable {
        public var id: Int64
        public var name: String
        public var type: String
        public var amount: Int64
        public var categoryId: Int64
        public var accountId: Int64
        public var description: String
        public var isFavorite: Bool
        public var createdAt: Int64
        public var updatedAt: Int64
        public var defaultHour: Int?
        public var defaultMinute: Int?
        public var sourceTransactionId: Int64?

        init(_ template: TransactionTemplate, id: Int64, accountId: Int64, categoryId: Int64, sourceTransactionId: Int64?) {
            self.id = id
            name = template.name
            type = template.type.rawValue
            amount = template.amount
            self.categoryId = categoryId
            self.accountId = accountId
            description = template.description
            isFavorite = template.isFavorite
            createdAt = template.createdAt
            updatedAt = template.updatedAt
            defaultHour = template.defaultHour
            defaultMinute = template.defaultMinute
            self.sourceTransactionId = sourceTransactionId
        }
    }
}
