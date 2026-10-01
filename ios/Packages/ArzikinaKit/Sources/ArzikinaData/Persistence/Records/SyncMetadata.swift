/// Colonnes de synchronisation communes à toutes les tables (voir `AppDatabaseSchema`).
struct SyncMetadata: Equatable, Sendable {
    var createdAt: Int64
    var updatedAt: Int64
    var deletedAt: Int64?
    var version: Int64

    /// Nouvelle entité créée localement : jamais envoyée au serveur (version 0).
    static func new(createdAt: Int64, now: Int64) -> SyncMetadata {
        SyncMetadata(createdAt: createdAt > 0 ? createdAt : now, updatedAt: now, deletedAt: nil, version: 0)
    }

    /// Modification locale d'une entité existante : seule la date de modification change.
    func touched(now: Int64) -> SyncMetadata {
        var copy = self
        copy.updatedAt = now
        return copy
    }

    /// Suppression douce locale.
    func deleted(now: Int64) -> SyncMetadata {
        var copy = self
        copy.updatedAt = now
        copy.deletedAt = now
        return copy
    }
}

/// Enregistrement d'une table synchronisée : accès uniforme aux métadonnées, utilisé par le dépôt
/// générique (`SyncedStore`).
protocol SyncedRecord: Codable, Sendable {
    var id: String { get }
    var meta: SyncMetadata { get set }
}
