import ArzikinaDomain
import Foundation

/// Emplacement des bases locales : un fichier par utilisateur dans
/// `Application Support/Arzikina/Databases/`.
public struct UserDatabaseLocator: Sendable {

    public let directory: URL

    public init(directory: URL) {
        self.directory = directory
    }

    /// Dossier par défaut de l'application.
    public static func standard() throws -> UserDatabaseLocator {
        let support = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        return UserDatabaseLocator(directory: support.appendingPathComponent("Arzikina/Databases", isDirectory: true))
    }

    /// Fichier de la base de [userId]. L'identifiant vient du serveur : seuls les caractères d'un
    /// UUID sont conservés, pour qu'il ne puisse jamais désigner un autre emplacement.
    public func databaseURL(for userId: String) -> URL {
        let safe = String(userId.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) || $0 == "-" }.prefix(64))
        return directory.appendingPathComponent("user-\(safe.isEmpty ? "unknown" : safe).sqlite")
    }

    /// Fichiers SQLite d'une base (principal + journal WAL + mémoire partagée).
    func files(for userId: String) -> [URL] {
        let main = databaseURL(for: userId)
        return [main, URL(fileURLWithPath: main.path + "-wal"), URL(fileURLWithPath: main.path + "-shm")]
    }
}

/// Espace de données local de l'utilisateur connecté : sa base et les dépôts qui l'utilisent.
///
/// Ouvert à la connexion, fermé à la déconnexion. Le fichier, lui, reste sur l'iPhone : en se
/// reconnectant avec le même compte, l'utilisateur retrouve ses données même sans réseau.
public final class UserDataSpace: Sendable {

    public let userId: String
    public let accounts: AccountRepository
    public let categories: CategoryRepository
    public let transactions: TransactionRepository
    public let dashboard: DashboardRepository
    public let accountOverview: AccountOverviewRepository
    public let ledger: TransactionLedgerRepository
    public let budgets: BudgetRepository

    let database: AppDatabase
    private let locator: UserDatabaseLocator?
    private let now: Clock

    init(userId: String, database: AppDatabase, locator: UserDatabaseLocator?, now: @escaping Clock) {
        self.userId = userId
        self.database = database
        self.locator = locator
        self.now = now
        self.accounts = LocalAccountRepository(database: database, now: now)
        self.categories = LocalCategoryRepository(database: database, now: now)
        self.transactions = LocalTransactionRepository(database: database, now: now)
        self.dashboard = LocalDashboardRepository(database: database)
        self.accountOverview = LocalAccountOverviewRepository(database: database)
        self.ledger = LocalTransactionLedgerRepository(database: database)
        self.budgets = LocalBudgetRepository(database: database, now: now)
    }

    /// Ouvre (ou crée) la base de [userId].
    public static func open(userId: String, locator: UserDatabaseLocator, now: @escaping Clock = Clocks.system) throws -> UserDataSpace {
        try FileManager.default.createDirectory(at: locator.directory, withIntermediateDirectories: true)
        protect(locator.directory)
        let database = try AppDatabase.open(at: locator.databaseURL(for: userId))
        return UserDataSpace(userId: userId, database: database, locator: locator, now: now)
    }

    /// Espace en mémoire, sans fichier (tests, aperçus).
    public static func inMemory(userId: String = "preview", now: @escaping Clock = Clocks.system) throws -> UserDataSpace {
        UserDataSpace(userId: userId, database: try AppDatabase.inMemory(), locator: nil, now: now)
    }

    /// Crée les comptes et catégories par défaut d'un compte qui VIENT d'être inscrit (jamais à
    /// la connexion à un compte existant, voir `DefaultData`). Écriture courte et synchrone, faite
    /// avant le démarrage de la synchronisation pour que le premier envoi les contienne.
    /// Retourne `false` si la base n'était pas vide (rien n'est écrit).
    @discardableResult
    public func seedDefaultDataForNewAccount() throws -> Bool {
        try DefaultDataSeeder(database: database, now: now, newId: EntityIDs.generate).seedIfEmpty()
    }

    public func observePendingChangesCount() -> AsyncStream<Int> {
        database.observePendingChangesCount()
    }

    public func pendingChangesCount() async throws -> Int {
        try await database.pendingChangesCount()
    }

    /// Taille occupée sur l'iPhone, en octets (0 pour un espace en mémoire).
    public func sizeOnDisk() -> Int64 {
        guard let locator else { return 0 }
        return locator.files(for: userId).reduce(Int64(0)) { total, url in
            let size = (try? FileManager.default.attributesOfItem(atPath: url.path)[.size] as? NSNumber)?.int64Value ?? 0
            return total + size
        }
    }

    /// Ferme la base (déconnexion). L'espace n'est plus utilisable ensuite.
    public func close() {
        try? database.close()
    }

    /// Ferme la base et SUPPRIME son fichier (« Vider les données locales »). Les données
    /// synchronisées seront retéléchargées ; les modifications pas encore envoyées sont perdues
    /// (l'interface prévient avant).
    public func closeAndErase() throws {
        try database.close()
        guard let locator else { return }
        for url in locator.files(for: userId) where FileManager.default.fileExists(atPath: url.path) {
            try FileManager.default.removeItem(at: url)
        }
    }

    /// Protection des données financières sur iOS : fichiers chiffrés tant que l'iPhone n'a pas
    /// été déverrouillé une première fois après démarrage (la future synchronisation en arrière-plan
    /// reste possible ensuite), et exclus de la sauvegarde iCloud (le serveur Arzikina est déjà la
    /// sauvegarde ; une restauration iCloud entrerait en conflit avec la synchronisation).
    private static func protect(_ directory: URL) {
        #if os(iOS)
        try? FileManager.default.setAttributes([.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication], ofItemAtPath: directory.path)
        var mutable = directory
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? mutable.setResourceValues(values)
        #endif
    }
}
