/// Curseur `updated_after` du lot suivant de `pull.php` — règle PARTAGÉE avec Android et le Web,
/// décrite et vérifiée par `shared/test-fixtures/pull-cursor.json`.
///
/// `pull.php` renvoie au plus [serverBatchLimit] lignes triées par `updated_at` et un `serverTime`
/// capturé AVANT sa lecture. Reprendre à `serverTime` après un lot PLEIN sauterait toutes les
/// lignes restantes (antérieures à `serverTime`) : après un lot plein, la lecture reprend donc au
/// `updatedAt` de la dernière ligne moins 1 ms (les lignes de cette milliseconde déjà reçues
/// reviennent une seconde fois, sans effet : l'enregistrement est idempotent).
enum SyncPullCursor {

    /// `$batchLimit` de `server/api/sync/pull.php`. À modifier EN MÊME TEMPS que le serveur.
    static let serverBatchLimit = 500

    static func isFullBatch(_ count: Int) -> Bool {
        count >= serverBatchLimit
    }

    static func next(current: Int64, updatedAts: [Int64], serverTime: Int64, isFull: Bool) -> Int64 {
        guard isFull, let last = updatedAts.max() else { return max(current, serverTime) }
        // 500 lignes ou plus à la même milliseconde : reculer d'1 ms ne ferait plus progresser le
        // curseur (boucle infinie) ; on avance quand même.
        return last - 1 > current ? last - 1 : max(current + 1, last)
    }
}
