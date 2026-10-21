package com.naniger.arzikina.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.naniger.arzikina.data.local.entity.IssuedReceiptEntity
import kotlinx.coroutines.flow.Flow

/**
 * Ligne de l'historique : colonnes strictement nécessaires à la liste, sans les champs JSON ni les
 * textes longs — Room ne lit que ces colonnes.
 */
data class IssuedReceiptSummaryRow(
    val id: Long,
    val number: String,
    val customerName: String,
    val issuedAt: Long,
    val amountMinor: Long,
    val currencyCode: String,
    val pdfFileName: String?
)

/**
 * Voir `AccountDao` pour le filtrage systématique par `userId`. Suppression DOUCE : les lectures
 * d'affichage filtrent `deletedAt IS NULL` ; seules les requêtes de numérotation et de
 * synchronisation incluent les lignes supprimées (un numéro n'est jamais réutilisé).
 *
 * La recherche est faite en SQL (et non en mémoire comme pour les justificatifs importés) : un
 * commerçant peut émettre des milliers de reçus.
 */
@Dao
interface IssuedReceiptDao {

    /**
     * [pattern] : motif `LIKE` DÉJÀ échappé (`\`, `%`, `_` précédés de `\`) et entouré de `%`, voir
     * `IssuedReceiptRepositoryImpl.likePattern`. [fromMillis]/[toMillis] : bornes inclusives,
     * `Long.MIN_VALUE`/`Long.MAX_VALUE` = pas de borne. Index `(userId, issuedAt)` pour le tri.
     */
    @Query(
        "SELECT id, number, customerName, issuedAt, amountMinor, currencyCode, pdfFileName " +
            "FROM issued_receipts WHERE userId = :userId AND deletedAt IS NULL " +
            "AND issuedAt BETWEEN :fromMillis AND :toMillis " +
            "AND (number LIKE :pattern ESCAPE '\\' OR customerName LIKE :pattern ESCAPE '\\' " +
            "OR description LIKE :pattern ESCAPE '\\') " +
            "ORDER BY issuedAt DESC, id DESC"
    )
    fun observeSummaries(userId: Long, pattern: String, fromMillis: Long, toMillis: Long): Flow<List<IssuedReceiptSummaryRow>>

    @Query("SELECT * FROM issued_receipts WHERE id = :id AND userId = :userId AND deletedAt IS NULL")
    suspend fun getById(id: Long, userId: Long): IssuedReceiptEntity?

    /** Numéros déjà attribués commençant par [prefix] — y compris supprimés (jamais de réutilisation). [prefix] sans caractère `LIKE` spécial (voir `ReceiptNumber.yearPrefix`). */
    @Query("SELECT number FROM issued_receipts WHERE userId = :userId AND number LIKE :prefix || '%'")
    suspend fun getNumbersWithPrefix(userId: Long, prefix: String): List<String>

    @Query("SELECT COUNT(*) FROM issued_receipts WHERE userId = :userId AND number = :number")
    suspend fun countByNumber(userId: Long, number: String): Int

    /** Réservé à `SyncEngineImpl` (étape « Synchronisation ») — voir `CategoryDao.getBySyncId`. */
    @Query("SELECT * FROM issued_receipts WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): IssuedReceiptEntity?

    /** Ligne portant [number], supprimée ou non (l'index unique `(userId, number)` les couvre toutes) — voir `IssuedReceiptSyncHandler`. */
    @Query("SELECT * FROM issued_receipts WHERE userId = :userId AND number = :number LIMIT 1")
    suspend fun getByNumber(userId: Long, number: String): IssuedReceiptEntity?

    @Query("SELECT * FROM issued_receipts WHERE userId = :userId AND deletedAt IS NULL AND syncId IS NULL")
    suspend fun getUnsyncedForUser(userId: Long): List<IssuedReceiptEntity>

    /** Retourne l'id de la ligne insérée (voir `AccountDao.upsert`). */
    @Upsert
    suspend fun upsert(receipt: IssuedReceiptEntity): Long

    @Update
    suspend fun update(receipt: IssuedReceiptEntity)

    /** Voir `AccountDao.insertAll` (restauration de sauvegarde : insertions neuves). */
    @Insert
    suspend fun insertAll(receipts: List<IssuedReceiptEntity>): List<Long>

    /** Cache de PDF local : ne touche NI `updatedAt` NI `version` (rien à synchroniser). */
    @Query("UPDATE issued_receipts SET pdfFileName = :pdfFileName WHERE id = :id AND userId = :userId")
    suspend fun setPdfFileName(id: Long, userId: Long, pdfFileName: String?)

    @Query("UPDATE issued_receipts SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND userId = :userId")
    suspend fun softDeleteById(id: Long, userId: Long, deletedAt: Long)

    /** Noms des PDF encore référencés, pour nettoyer les fichiers orphelins (voir l'étape « Générateur PDF »). */
    @Query("SELECT pdfFileName FROM issued_receipts WHERE userId = :userId AND deletedAt IS NULL AND pdfFileName IS NOT NULL")
    suspend fun getReferencedPdfFileNames(userId: Long): List<String>

    /** JSON d'émetteur des reçus qui MENTIONNENT une signature (filtre SQL large, le tri exact se fait en code). */
    @Query("SELECT issuerJson FROM issued_receipts WHERE userId = :userId AND issuerJson LIKE '%signatureFileName%'")
    suspend fun getIssuerJsonsMentioningSignature(userId: Long): List<String>

    /** Purge TOUTES les lignes de [userId] (restauration de sauvegarde, suppression du compte). */
    @Query("DELETE FROM issued_receipts WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: Long)
}
