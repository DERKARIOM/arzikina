package com.naniger.arzikina.domain.repository

import com.naniger.arzikina.domain.shop.Shop
import kotlinx.coroutines.flow.Flow

/**
 * Accès à la boutique de l'utilisateur courant. V1 : UNE boutique par utilisateur ; elle est créée
 * à la première ouverture de l'onglet Boutique ([getOrCreateShop]). Le passage à plusieurs
 * boutiques ajoutera un sélecteur ici, sans toucher aux données (toutes portent `shopId`).
 */
interface ShopRepository {

    /** Boutique active, `null` tant qu'elle n'a pas été créée ou sans utilisateur connecté. */
    fun observeShop(): Flow<Shop?>

    /** Retourne la boutique existante, ou la crée (nom par défaut, devise des préférences). Idempotent. */
    suspend fun getOrCreateShop(defaultName: String): Shop
}
