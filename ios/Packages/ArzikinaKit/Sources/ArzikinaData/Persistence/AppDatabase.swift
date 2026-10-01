import Foundation
import GRDB

/// Base SQLite locale d'UN utilisateur (GRDB) : source de vérité hors ligne de l'app, comme Room
/// sur Android.
///
/// Une base PAR utilisateur (voir `UserDatabaseLocator`) : aucune table n'a besoin de colonne
/// `userId`, et deux comptes utilisés sur le même iPhone ne peuvent jamais se mélanger.
public final class AppDatabase: Sendable {

    /// Accès en lecture/écriture. `DatabasePool` (mode WAL : lectures concurrentes pendant une
    /// écriture) pour un fichier, `DatabaseQueue` en mémoire pour les tests.
    let writer: any DatabaseWriter

    init(writer: any DatabaseWriter) throws {
        self.writer = writer
        try AppDatabaseSchema.migrator.migrate(writer)
    }

    /// Ouvre (ou crée) la base au chemin [url] et applique les migrations en attente.
    public static func open(at url: URL) throws -> AppDatabase {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        var configuration = Configuration()
        configuration.label = "Arzikina"
        let pool = try DatabasePool(path: url.path, configuration: configuration)
        return try AppDatabase(writer: pool)
    }

    /// Base vide en mémoire (tests, aperçus).
    public static func inMemory() throws -> AppDatabase {
        try AppDatabase(writer: DatabaseQueue())
    }

    /// Ferme la base (avant suppression du fichier). Plus aucun accès possible ensuite.
    public func close() throws {
        if let pool = writer as? DatabasePool {
            try pool.close()
        } else if let queue = writer as? DatabaseQueue {
            try queue.close()
        }
    }

    /// Nombre de modifications locales pas encore envoyées au serveur, mis à jour en continu.
    public func observePendingChangesCount() -> AsyncStream<Int> {
        observe { db in try SyncQueueRecord.fetchCount(db) }
    }

    public func pendingChangesCount() async throws -> Int {
        try await writer.read { db in try SyncQueueRecord.fetchCount(db) }
    }

    /// Observe une requête et émet son résultat à chaque modification des tables lues.
    func observe<Value: Sendable>(_ fetch: @escaping @Sendable (Database) throws -> Value) -> AsyncStream<Value> {
        let observation = ValueObservation.tracking(fetch)
        let writer = self.writer
        return AsyncStream { continuation in
            let task = Task {
                do {
                    for try await value in observation.values(in: writer) {
                        continuation.yield(value)
                    }
                } catch {
                    // Base fermée (déconnexion, vidage du cache) : fin normale du flux.
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}
