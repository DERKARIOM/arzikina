package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.naniger.arzikina.data.local.entity.SaleEntity
import kotlinx.coroutines.flow.Flow

/** Ligne d'historique (projection SQL, sans les lignes de produits). */
data class SaleSummaryRow(
    val id: Long,
    val reference: String,
    val soldAt: Long,
    val totalMinor: Long,
    val currencyCode: String,
    val status: String,
    val customerName: String?,
    val issuedReceiptId: Long?,
    val accountId: Long?,
    val method: String?
)

/** Accès à la table `sales`. Filtrage systématique par `userId` (voir `AccountDao`). */
@Dao
interface SaleDao {

    @Insert
    suspend fun insert(sale: SaleEntity): Long

    @Query("SELECT * FROM sales WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    fun observeById(id: Long, userId: Long): Flow<SaleEntity?>

    @Query("SELECT * FROM sales WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): SaleEntity?

    /** Clé d'idempotence : le `syncId` de la vente est la clé du panier (voir `SaleRequest.draftKey`). */
    @Query("SELECT * FROM sales WHERE userId = :userId AND syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(userId: Long, syncId: String): SaleEntity?

    /**
     * Historique d'une boutique. Une seule ligne par vente : l'encaissement joint est le premier (en V1 il
     * n'y en a qu'un). `:status` `NULL` = tous ; `:sort` 0 = date décroissante, 1 = montant décroissant.
     * Index `(shopId, soldAt)` ; recherche par référence ou client avec échappement LIKE (voir `SqlLike`).
     */
    @Query(
        "SELECT s.id AS id, s.reference AS reference, s.soldAt AS soldAt, s.totalMinor AS totalMinor, " +
            "s.currencyCode AS currencyCode, s.status AS status, s.customerName AS customerName, " +
            "s.issuedReceiptId AS issuedReceiptId, p.accountId AS accountId, p.method AS method " +
            "FROM sales s LEFT JOIN sale_payments p ON p.id = " +
            "(SELECT MIN(p2.id) FROM sale_payments p2 WHERE p2.saleId = s.id AND p2.deletedAt IS NULL) " +
            "WHERE s.userId = :userId AND s.shopId = :shopId AND s.deletedAt IS NULL " +
            "AND (:status IS NULL OR s.status = :status) " +
            "AND s.soldAt >= :fromMillis AND s.soldAt < :toMillis " +
            "AND (s.reference LIKE :pattern ESCAPE '\\' OR IFNULL(s.customerName, '') LIKE :pattern ESCAPE '\\') " +
            "ORDER BY CASE WHEN :sort = 0 THEN s.soldAt END DESC, " +
            "CASE WHEN :sort = 1 THEN s.totalMinor END DESC, s.id DESC " +
            "LIMIT :limit"
    )
    fun observeSummaries(
        userId: Long,
        shopId: Long,
        pattern: String,
        status: String?,
        fromMillis: Long,
        toMillis: Long,
        sort: Int,
        limit: Int
    ): Flow<List<SaleSummaryRow>>

    /**
     * Passe la vente à `CANCELLED`, UNIQUEMENT si elle est encore validée. Retourne le nombre de lignes
     * modifiées : 0 = déjà annulée (garde d'idempotence et de concurrence).
     */
    @Query(
        "UPDATE sales SET status = 'CANCELLED', cancelledAt = :cancelledAt, cancelReason = :reason, " +
            "updatedAt = :cancelledAt WHERE id = :id AND userId = :userId AND status = 'CONFIRMED'"
    )
    suspend fun markCancelled(id: Long, userId: Long, cancelledAt: Long, reason: String?): Int

    /**
     * Rattache un reçu si la vente n'en a pas ou si son reçu actuel est [expected] (`-1` = aucun).
     * Retourne le nombre de lignes modifiées : 0 = un autre reçu est déjà rattaché.
     */
    @Query(
        "UPDATE sales SET issuedReceiptId = :receiptId, updatedAt = :now WHERE id = :id AND userId = :userId " +
            "AND (issuedReceiptId IS NULL OR issuedReceiptId = :expected)"
    )
    suspend fun linkReceipt(id: Long, userId: Long, receiptId: Long, expected: Long, now: Long): Int

    /**
     * Indicateurs d'une période `[from, to)` sur la date de VENTE. Le CA, le nombre de ventes et l'encaissé
     * ne comptent que les ventes `CONFIRMED` ; les annulées sont comptées à part (aucun double comptage).
     */
    @Query(
        "SELECT COUNT(CASE WHEN s.status = 'CONFIRMED' THEN 1 END) AS salesCount, " +
            "COALESCE(SUM(CASE WHEN s.status = 'CONFIRMED' THEN s.totalMinor END), 0) AS revenueMinor, " +
            "COALESCE(SUM(CASE WHEN s.status = 'CONFIRMED' THEN (SELECT COALESCE(SUM(p.amountMinor), 0) " +
            "FROM sale_payments p WHERE p.saleId = s.id AND p.deletedAt IS NULL) END), 0) AS collectedMinor, " +
            "COUNT(CASE WHEN s.status = 'CANCELLED' THEN 1 END) AS cancelledCount, " +
            "COALESCE(SUM(CASE WHEN s.status = 'CANCELLED' THEN s.totalMinor END), 0) AS cancelledMinor " +
            "FROM sales s WHERE s.userId = :userId AND s.shopId = :shopId AND s.deletedAt IS NULL " +
            "AND s.soldAt >= :fromMillis AND s.soldAt < :toMillis"
    )
    fun observeSummary(userId: Long, shopId: Long, fromMillis: Long, toMillis: Long): Flow<SalesSummaryRow>

    /** CA par jour calendaire local (`soldAt + offset`), ventes validées uniquement. */
    @Query(
        "SELECT (s.soldAt + :offsetMillis) / 86400000 AS dayKey, SUM(s.totalMinor) AS revenueMinor, " +
            "COUNT(*) AS salesCount FROM sales s WHERE s.userId = :userId AND s.shopId = :shopId " +
            "AND s.deletedAt IS NULL AND s.status = 'CONFIRMED' AND s.soldAt >= :fromMillis " +
            "AND s.soldAt < :toMillis GROUP BY dayKey ORDER BY dayKey"
    )
    fun observeDaily(userId: Long, shopId: Long, fromMillis: Long, toMillis: Long, offsetMillis: Long): Flow<List<DailyRevenueRow>>

    /** Purge TOUTES les lignes de [userId] (restauration de sauvegarde, suppression du compte). */
    @Query("DELETE FROM sales WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}

/** Agrégat brut des ventes d'une période (voir [SaleDao.observeSummary]). */
data class SalesSummaryRow(
    val salesCount: Int,
    val revenueMinor: Long,
    val collectedMinor: Long,
    val cancelledCount: Int,
    val cancelledMinor: Long
)

/** CA d'un jour calendaire (voir [SaleDao.observeDaily]). */
data class DailyRevenueRow(val dayKey: Long, val revenueMinor: Long, val salesCount: Int)
