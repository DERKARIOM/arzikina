package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.model.IssuedReceipt
import com.naniger.arzikina.domain.model.IssuedReceiptFilter
import com.naniger.arzikina.domain.model.IssuedReceiptSummary
import kotlinx.coroutines.flow.Flow

/**
 * Contrat d'accès aux reçus émis (utilitaire « Gestion de reçus », voir [IssuedReceipt]). Voir
 * [AccountRepository] pour le raisonnement derrière la séparation domaine/implémentation ; toutes les
 * opérations sont limitées à l'utilisateur connecté (aucun accès aux reçus d'un autre).
 */
interface IssuedReceiptRepository {

    /** Historique du plus récent au plus ancien, filtré par [filter]. Liste allégée (voir [IssuedReceiptSummary]). */
    fun observeReceipts(filter: IssuedReceiptFilter = IssuedReceiptFilter()): Flow<List<IssuedReceiptSummary>>

    suspend fun getReceipt(id: Long): IssuedReceipt?

    /** Numéro qui serait attribué à un reçu émis à la date [issuedAt] (aperçu dans le formulaire ; l'attribution réelle se refait à l'enregistrement). */
    suspend fun suggestNextNumber(issuedAt: Long): String

    /**
     * Crée ([IssuedReceipt.id] = 0) ou met à jour un reçu et retourne son id.
     *
     * Création : numéro vide = généré ; numéro fourni = validé et vérifié unique. Mise à jour : le
     * numéro et la date de création sont CONSERVÉS quoi que contienne [receipt], et le PDF déjà
     * généré devient périmé ([IssuedReceipt.pdfFileName] repasse à `null`). Enregistrer deux fois le
     * même reçu ne crée jamais de doublon.
     *
     * @throws com.naniger.arzikina.domain.issuedreceipt.IssuedReceiptException
     */
    suspend fun saveReceipt(receipt: IssuedReceipt): Long

    /** Mémorise le nom du PDF généré (n'altère ni la version ni la date de modification : c'est un cache local). */
    suspend fun markPdfGenerated(id: Long, pdfFileName: String)

    /** Suppression DOUCE : le numéro n'est jamais réutilisé. */
    suspend fun deleteReceipt(id: Long)
}
