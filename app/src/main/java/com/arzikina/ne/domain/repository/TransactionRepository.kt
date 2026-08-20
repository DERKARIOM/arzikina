package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.Transaction
import com.arzikina.ne.domain.model.TransactionFee
import kotlinx.coroutines.flow.Flow

/**
 * Contrat d'accès aux données des transactions. Voir [AccountRepository] pour
 * le raisonnement derrière cette séparation (indépendance vis-à-vis de Room).
 *
 * Volontairement minimal pour l'instant : les besoins de filtrage/recherche
 * (par compte, par catégorie, par période, texte libre) seront ajoutés ici au
 * fur et à mesure des écrans qui en ont réellement besoin, plutôt
 * qu'anticipés sans consommateur concret.
 */
interface TransactionRepository {

    fun observeTransactions(): Flow<List<Transaction>>

    suspend fun getTransaction(id: Long): Transaction?

    /** Voir [Transaction.receiptId] — utilisé par le bouton "Ajouter comme transaction" (cahier des
     * charges "Créer une transaction depuis un reçu") pour l'anti-doublon : `null` si ce reçu n'a
     * encore donné lieu à aucune transaction, auquel cas le bouton propose une création ; sinon il
     * propose d'ouvrir la transaction déjà créée plutôt que d'en générer une deuxième. */
    suspend fun findByReceiptId(receiptId: Long): Transaction?

    /** Voir [Transaction.receiptId] — ensemble des reçus déjà liés à une transaction (cahier des
     * charges "Créer une transaction depuis un reçu", statut visuel affiché sur "Gestion des
     * reçus") : UNE SEULE requête groupée plutôt qu'un [findByReceiptId] par reçu affiché dans la
     * liste (non-N+1, voir `ReceiptsViewModel`). */
    fun observeReceiptIdsWithTransaction(): Flow<Set<Long>>

    /** Crée la transaction si [Transaction.id] vaut 0, la met à jour sinon. Retourne l'id définitif
     * de la transaction (celui généré à la création, ou [Transaction.id] inchangé pour une mise à
     * jour) — voir [AccountRepository.saveAccount] pour le même principe, utile ici pour lier une
     * transaction générée automatiquement à un `Loan`/`LoanPayment`.
     *
     * [fee] : `null` par défaut (comportement inchangé pour tous les appelants existants). Quand
     * renseigné, l'implémentation matérialise/met à jour une transaction de frais liée (voir
     * [TransactionFee]) au lieu de porter les frais sur [transaction] elle-même — l'appelant n'a
     * jamais à connaître ni gérer [Transaction.feeTransactionId]. Passer `null` alors qu'une
     * transaction de frais existait déjà (édition) la SUPPRIME (l'utilisateur a retiré les frais).
     */
    suspend fun saveTransaction(transaction: Transaction, fee: TransactionFee? = null): Long

    /** Supprime aussi la transaction de frais liée si elle existe (voir [TransactionFee]) —
     * l'appelant n'a rien de spécial à faire, cette cascade applicative est entièrement gérée par
     * l'implémentation (pas de contrainte SQL sur [Transaction.feeTransactionId], voir sa doc). */
    suspend fun deleteTransaction(id: Long)
}
