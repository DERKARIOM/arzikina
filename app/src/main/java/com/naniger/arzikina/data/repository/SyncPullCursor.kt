package com.naniger.arzikina.data.repository

/**
 * Curseur `updated_after` du lot suivant de `server/api/sync/pull.php` — règle PARTAGÉE avec iOS
 * (`SyncPullCursor.swift`) et le Web (`src/lib/pull-cursor.ts`), décrite et vérifiée par
 * `shared/test-fixtures/pull-cursor.json` (voir `SharedFixturesTest.pullCursor`).
 *
 * `pull.php` renvoie au plus [SERVER_BATCH_LIMIT] lignes triées par `updated_at`, et un `serverTime`
 * capturé AVANT sa lecture. Reprendre à `serverTime` après un lot PLEIN (ancienne règle) sautait
 * pour toujours les lignes restantes, toutes antérieures à `serverTime` : un compte de plus de
 * 500 transactions synchronisé pour la première fois sur un appareil n'en recevait que 500.
 *
 * Nouvelle règle :
 * - lot plein → `updatedAt` de la DERNIÈRE ligne reçue moins 1 ms : les lignes de cette même
 *   milliseconde pas encore reçues sont incluses ; celles déjà reçues reviennent une seconde fois,
 *   sans effet (l'application d'une ligne serveur est idempotente) ;
 * - lot incomplet → `serverTime` : tout ce qui existait avant la requête a été reçu.
 * Le curseur ne recule jamais, et progresse toujours après un lot plein (pas de boucle infinie).
 */
object SyncPullCursor {

    /** `$batchLimit` de `pull.php`. À modifier EN MÊME TEMPS que le serveur (et iOS, Web). */
    const val SERVER_BATCH_LIMIT = 500

    fun isFullBatch(count: Int): Boolean = count >= SERVER_BATCH_LIMIT

    fun next(current: Long, updatedAts: List<Long>, serverTime: Long, isFull: Boolean): Long {
        val last = updatedAts.maxOrNull()
        if (!isFull || last == null) return maxOf(current, serverTime)
        // 500 lignes ou plus à la même milliseconde : reculer d'1 ms ne ferait plus progresser le
        // curseur ; on avance quand même.
        return if (last - 1 > current) last - 1 else maxOf(current + 1, last)
    }
}
