import ArzikinaDomain
import GRDB

/// Ligne de la table `persons`.
struct PersonRecord: SyncedRecord, Equatable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "persons"

    var id: String
    var name: String
    var phone: String?
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var version: Int64

    var meta: SyncMetadata {
        get { SyncMetadata(createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt, version: version) }
        set { createdAt = newValue.createdAt; updatedAt = newValue.updatedAt; deletedAt = newValue.deletedAt; version = newValue.version }
    }

    init(_ person: Person, meta: SyncMetadata) {
        id = person.id
        name = person.name
        phone = person.phone
        createdAt = meta.createdAt
        updatedAt = meta.updatedAt
        deletedAt = meta.deletedAt
        version = meta.version
    }

    var domain: Person {
        Person(id: id, name: name, phone: phone, createdAt: createdAt)
    }
}

/// Ligne de la table `loans`.
struct LoanRecord: SyncedRecord, Equatable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "loans"

    var id: String
    var personId: String
    var accountId: String
    var type: String
    var amount: Int64
    var amountRepaid: Int64
    var remainingAmount: Int64
    var startDate: Int64
    var dueDate: Int64
    var reason: String
    var reasonCustomText: String?
    var repaymentMode: String
    var description: String
    var status: String
    var transactionId: String
    var giftedAmount: Int64
    var giftTransactionId: String?
    var giftedAt: Int64?
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var version: Int64

    var meta: SyncMetadata {
        get { SyncMetadata(createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt, version: version) }
        set { createdAt = newValue.createdAt; updatedAt = newValue.updatedAt; deletedAt = newValue.deletedAt; version = newValue.version }
    }

    init(_ loan: Loan, meta: SyncMetadata) {
        id = loan.id
        personId = loan.personId
        accountId = loan.accountId
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
        transactionId = loan.transactionId
        giftedAmount = loan.giftedAmount
        giftTransactionId = loan.giftTransactionId
        giftedAt = loan.giftedAt
        createdAt = meta.createdAt
        updatedAt = meta.updatedAt
        deletedAt = meta.deletedAt
        version = meta.version
    }

    var domain: Loan {
        Loan(
            id: id,
            personId: personId,
            accountId: accountId,
            type: LoanType(rawValue: type) ?? .lent,
            amount: amount,
            amountRepaid: amountRepaid,
            remainingAmount: remainingAmount,
            startDate: startDate,
            dueDate: dueDate,
            reason: LoanReason(rawValue: reason) ?? .other,
            reasonCustomText: reasonCustomText,
            repaymentMode: RepaymentMode(rawValue: repaymentMode) ?? .single,
            description: description,
            status: LoanStatus(rawValue: status) ?? .ongoing,
            transactionId: transactionId,
            createdAt: createdAt,
            updatedAt: updatedAt,
            giftedAmount: giftedAmount,
            giftTransactionId: giftTransactionId,
            giftedAt: giftedAt
        )
    }
}

/// Ligne de la table `loan_payments`.
struct LoanPaymentRecord: SyncedRecord, Equatable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "loan_payments"

    var id: String
    var loanId: String
    var accountId: String
    var amount: Int64
    var date: Int64
    var note: String
    var transactionId: String
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var version: Int64

    var meta: SyncMetadata {
        get { SyncMetadata(createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt, version: version) }
        set { createdAt = newValue.createdAt; updatedAt = newValue.updatedAt; deletedAt = newValue.deletedAt; version = newValue.version }
    }

    init(_ payment: LoanPayment, meta: SyncMetadata) {
        id = payment.id
        loanId = payment.loanId
        accountId = payment.accountId
        amount = payment.amount
        date = payment.date
        note = payment.note
        transactionId = payment.transactionId
        createdAt = meta.createdAt
        updatedAt = meta.updatedAt
        deletedAt = meta.deletedAt
        version = meta.version
    }

    var domain: LoanPayment {
        LoanPayment(id: id, loanId: loanId, accountId: accountId, amount: amount, date: date, note: note, transactionId: transactionId, createdAt: createdAt)
    }
}
