import ArzikinaDomain
import GRDB

/// Écrit les comptes et catégories par défaut (`DefaultData`) d'un compte qui vient d'être
/// inscrit, en UNE transaction SQL, chacun inscrit dans la file d'envoi (création) : ils partent
/// au serveur à la première synchronisation, puis arrivent sur les autres appareils.
struct DefaultDataSeeder {

    let database: AppDatabase
    let now: Clock
    let newId: @Sendable () -> EntityID

    /// `false` (rien écrit) si la base contient déjà un compte ou une catégorie, même supprimés :
    /// filet de sécurité contre les doublons si l'appel était fait sur une base déjà utilisée.
    func seedIfEmpty() throws -> Bool {
        let accounts = SyncedStore<AccountRecord>(database: database, entityType: .accounts, now: now)
        let categories = SyncedStore<CategoryRecord>(database: database, entityType: .categories, now: now)
        let timestamp = now()
        return try database.writer.write { db in
            let existing = try AccountRecord.fetchCount(db) + CategoryRecord.fetchCount(db)
            guard existing == 0 else { return false }
            for account in DefaultData.accounts(now: timestamp, newId: newId) {
                try accounts.save(db, id: account.id, createdAt: account.createdAt, timestamp: timestamp) { AccountRecord(account, meta: $0) }
            }
            for category in DefaultData.categories(now: timestamp, newId: newId) {
                try categories.save(db, id: category.id, createdAt: category.createdAt, timestamp: timestamp) { CategoryRecord(category, meta: $0) }
            }
            return true
        }
    }
}
