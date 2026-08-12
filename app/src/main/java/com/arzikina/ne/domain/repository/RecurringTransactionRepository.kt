package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.PaymentMethod
import com.arzikina.ne.domain.model.RecurringTransaction
import com.arzikina.ne.domain.model.RecurringTransactionOccurrence
import com.arzikina.ne.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * Contrat d'accès aux données des transactions récurrentes/planifiées (voir
 * [RecurringTransaction]/[RecurringTransactionOccurrence]). Voir [AccountRepository] pour le
 * raisonnement derrière cette séparation domaine/implémentation.
 *
 * Indépendant des [Loan] : un remboursement de prêt n'est PAS une transaction récurrente (choix
 * produit explicite, voir cahier des charges de cette fonctionnalité) — les deux systèmes
 * n'interagissent jamais.
 *
 * Chaque acceptation d'occurrence ([acceptOccurrence]/[acceptOccurrenceWithChanges]) crée, dans la
 * MÊME opération atomique, la [com.arzikina.ne.domain.model.Transaction] Arzikina correspondante —
 * voir la doc de [RecurringTransactionOccurrence.transactionId]. Un rejet ([rejectOccurrence]) n'en
 * crée volontairement AUCUNE.
 */
interface RecurringTransactionRepository {

    /** Flux réactif de toutes les règles récurrentes, triées par prochaine échéance. */
    fun observeRecurringTransactions(): Flow<List<RecurringTransaction>>

    suspend fun getRecurringTransaction(id: Long): RecurringTransaction?

    /** "À traiter" (écran "Transactions planifiées") ET badge du Dashboard : même flux, jamais
     * dupliqué (voir `RecurringTransactionOccurrenceDao.observePendingForUser`). */
    fun observePendingOccurrences(): Flow<List<RecurringTransactionOccurrence>>

    /** "Historique" (écran "Transactions planifiées") : occurrences déjà traitées. */
    fun observeProcessedOccurrences(): Flow<List<RecurringTransactionOccurrence>>

    /**
     * Si [RecurringTransaction.id] vaut 0 : crée la règle, [RecurringTransaction.nextExecutionDate]
     * est TOUJOURS initialisée à [RecurringTransaction.startDate] quelle que soit la valeur fournie
     * par l'appelant (aucune occurrence n'a encore pu être générée pour une règle qui n'existe pas
     * encore).
     *
     * Si [RecurringTransaction.id] est non nul : met à jour les champs modifiables. Ne rembobine
     * JAMAIS [RecurringTransaction.nextExecutionDate] déjà avancée par des occurrences déjà
     * générées — sauf si la règle n'a encore produit AUCUNE occurrence (auquel cas modifier
     * [RecurringTransaction.startDate] déplace logiquement la première échéance à venir). Ne
     * modifie JAMAIS l'historique des occurrences déjà générées ni les transactions déjà créées à
     * partir d'elles (voir [acceptOccurrence]/[acceptOccurrenceWithChanges], seuls responsables de
     * cet historique) : conformément au cahier des charges, modifier la règle n'affecte que ses
     * futures échéances, jamais le passé.
     *
     * Retourne l'id définitif de la règle.
     */
    suspend fun saveRecurringTransaction(recurringTransaction: RecurringTransaction): Long

    /**
     * Supprime la règle et tout son historique d'occurrences (cascade SQLite, voir
     * `data/local/entity/RecurringTransactionOccurrenceEntity`), ET les transactions Arzikina déjà
     * créées à partir des occurrences [com.arzikina.ne.domain.model.OccurrenceStatus.ACCEPTED]/
     * [com.arzikina.ne.domain.model.OccurrenceStatus.MODIFIED] — nettoyage atomique, voir la doc de
     * cette interface.
     */
    suspend fun deleteRecurringTransaction(id: Long)

    /**
     * "Enregistrer" (voir cahier des charges) : crée la transaction Arzikina à partir des valeurs
     * ACTUELLES de la règle (montant/compte/catégorie/description/moyen de paiement), à la date de
     * l'occurrence — sans aucune modification ponctuelle. Retourne l'id de la transaction créée.
     *
     * @throws IllegalStateException si l'occurrence n'existe pas ou n'est plus
     * [com.arzikina.ne.domain.model.OccurrenceStatus.PENDING] (déjà traitée).
     */
    suspend fun acceptOccurrence(occurrenceId: Long): Long

    /**
     * "Modifier" puis "Enregistrer" (voir cahier des charges) : crée la transaction Arzikina à
     * partir des valeurs FOURNIES ici (potentiellement différentes de la règle), à la date choisie —
     * sans jamais modifier la règle d'origine (voir [saveRecurringTransaction], seul point d'entrée
     * pour ça, sur action EXPLICITE et séparée de l'utilisateur). Retourne l'id de la transaction
     * créée.
     *
     * @throws IllegalStateException si l'occurrence n'existe pas ou n'est plus
     * [com.arzikina.ne.domain.model.OccurrenceStatus.PENDING] (déjà traitée).
     */
    suspend fun acceptOccurrenceWithChanges(
        occurrenceId: Long,
        type: TransactionType,
        amount: Long,
        accountId: Long,
        categoryId: Long?,
        date: Long,
        description: String,
        paymentMethod: PaymentMethod?
    ): Long

    /**
     * "Rejeter" (voir cahier des charges) : aucune transaction créée, l'occurrence passe à
     * [com.arzikina.ne.domain.model.OccurrenceStatus.REJECTED] et ne sera plus jamais proposée.
     */
    suspend fun rejectOccurrence(occurrenceId: Long)

    /**
     * Parcourt toutes les règles actives de l'utilisateur connecté et génère leurs occurrences
     * `PENDING` manquantes (échéance du jour ET échéances manquées, voir
     * `com.arzikina.ne.domain.model.generateMissingScheduledDates`) — appelée à l'ouverture de
     * l'app (voir `MainActivity`) et, plus tard, par le `Worker` périodique. Ne fait RIEN si aucun
     * utilisateur n'est connecté (contrairement aux autres méthodes de cette interface, qui lèvent
     * une erreur dans ce cas) : cette fonction est appelée sans device de garde particulier au
     * lancement de l'app, avant même de savoir si une session existe.
     */
    suspend fun generateMissingOccurrences()
}
