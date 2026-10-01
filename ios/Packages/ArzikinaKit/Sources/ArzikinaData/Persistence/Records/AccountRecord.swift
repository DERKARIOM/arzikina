import ArzikinaDomain
import GRDB

/// Ligne de la table `accounts`.
struct AccountRecord: SyncedRecord, Equatable, FetchableRecord, PersistableRecord {
    static let databaseTableName = "accounts"

    var id: String
    var name: String
    var icon: String
    var colorArgb: Int64
    var currencyCode: String
    var initialBalance: Int64
    var type: String
    var cardLastFourDigits: String?
    var cardExpiryMonth: Int?
    var cardExpiryYear: Int?
    var isExcludedFromStatistics: Bool
    var mobileMoneyPackageName: String?
    var displayOrder: Int64
    var savingsTargetAmount: Int64?
    var savingsDescription: String?
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var version: Int64

    var meta: SyncMetadata {
        get { SyncMetadata(createdAt: createdAt, updatedAt: updatedAt, deletedAt: deletedAt, version: version) }
        set { createdAt = newValue.createdAt; updatedAt = newValue.updatedAt; deletedAt = newValue.deletedAt; version = newValue.version }
    }

    init(_ account: Account, meta: SyncMetadata) {
        id = account.id
        name = account.name
        icon = account.icon.rawValue
        colorArgb = account.colorArgb
        currencyCode = account.currencyCode
        initialBalance = account.initialBalance
        type = account.type.rawValue
        cardLastFourDigits = account.cardLastFourDigits
        cardExpiryMonth = account.cardExpiryMonth
        cardExpiryYear = account.cardExpiryYear
        isExcludedFromStatistics = account.isExcludedFromStatistics
        mobileMoneyPackageName = account.mobileMoneyPackageName
        displayOrder = account.displayOrder
        savingsTargetAmount = account.savingsTargetAmount
        savingsDescription = account.savingsDescription
        createdAt = meta.createdAt
        updatedAt = meta.updatedAt
        deletedAt = meta.deletedAt
        version = meta.version
    }

    /// Valeur inconnue (version plus récente de l'app/du serveur) → valeur neutre plutôt qu'un
    /// échec de lecture : la ligne reste affichable.
    var domain: Account {
        Account(
            id: id,
            name: name,
            icon: AccountIcon(rawValue: icon) ?? .other,
            colorArgb: colorArgb,
            currencyCode: currencyCode,
            initialBalance: initialBalance,
            type: AccountType(rawValue: type) ?? .cash,
            cardLastFourDigits: cardLastFourDigits,
            cardExpiryMonth: cardExpiryMonth,
            cardExpiryYear: cardExpiryYear,
            isExcludedFromStatistics: isExcludedFromStatistics,
            mobileMoneyPackageName: mobileMoneyPackageName,
            displayOrder: displayOrder,
            savingsTargetAmount: savingsTargetAmount,
            savingsDescription: savingsDescription,
            createdAt: createdAt
        )
    }
}
