package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.shop.Sale
import com.naniger.arzikina.domain.shop.SaleFilter
import com.naniger.arzikina.domain.shop.SaleSummary
import com.naniger.arzikina.domain.shop.SaleRequest
import kotlinx.coroutines.flow.Flow

/**
 * Ventes de la boutique. Aucune règle financière dupliquée : l'encaissement passe par une
 * transaction de revenu ordinaire sur le compte choisi, le solde reste « solde initial +
 * transactions ».
 */
interface SaleRepository {

    /**
     * Valide la vente de façon ATOMIQUE : vente, lignes, encaissement, sorties de stock et
     * transaction de revenu sont écrits ensemble ou pas du tout. Idempotente sur
     * [SaleRequest.draftKey]. Lève une `SaleException` si la vente est refusée.
     */
    suspend fun confirmSale(request: SaleRequest): Sale

    fun observeSale(id: Long): Flow<Sale?>

    suspend fun getSale(id: Long): Sale?

    /** Historique filtré (recherche par référence ou client, période, statut, tri), limité aux plus récentes. */
    fun observeSales(filter: SaleFilter): Flow<List<SaleSummary>>

    /**
     * Annule une vente validée, SANS rien supprimer : remise en stock (`SALE_CANCELLATION`), transaction
     * inverse sur le compte crédité, statut `CANCELLED` avec date et [reason]. Idempotente : annuler une
     * vente déjà annulée la renvoie telle quelle. Lève `SaleException.NotFound` si elle n'existe pas.
     */
    suspend fun cancelSale(id: Long, reason: String?): Sale

    /**
     * Rattache [receiptId] à la vente si elle n'a pas de reçu ou si son reçu actuel est [expectedCurrent].
     * @return `false` si un autre reçu valide était déjà rattaché (rien n'est modifié).
     */
    suspend fun linkReceipt(saleId: Long, receiptId: Long, expectedCurrent: Long?): Boolean
}
