package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.shop.StockMovement
import com.naniger.arzikina.domain.shop.StockMovementRequest
import kotlinx.coroutines.flow.Flow

/**
 * Mouvements de stock saisis à la main. Chaque écriture est atomique : le mouvement ET le stock du
 * produit (cache de la somme des mouvements) sont écrits dans une même transaction.
 */
interface StockRepository {

    /** Historique d'un produit, du plus récent au plus ancien, limité aux [limit] derniers mouvements. */
    fun observeMovements(productId: Long, limit: Int): Flow<List<StockMovement>>

    /**
     * Enregistre un mouvement manuel et retourne le nouveau stock (milli-unités).
     *
     * @throws com.naniger.arzikina.domain.shop.StockException
     */
    suspend fun recordMovement(request: StockMovementRequest): Long

    /**
     * Correction d'inventaire : fixe le stock à [countedMilli] par un mouvement `ADJUSTMENT` dont l'écart
     * est calculé DANS la transaction (aucune course avec un autre mouvement). [reason] obligatoire.
     * Retourne le nouveau stock.
     *
     * @throws com.naniger.arzikina.domain.shop.StockException
     */
    suspend fun adjustStockTo(productId: Long, countedMilli: Long, reason: String): Long
}
