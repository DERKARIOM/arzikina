package com.naniger.arzikina.data.repository

import com.naniger.arzikina.data.local.dao.ShopDao
import com.naniger.arzikina.domain.repository.SessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Périmètre de toute requête Boutique : l'utilisateur connecté ET sa boutique active. */
data class ShopScope(val userId: Long, val shopId: Long)

/**
 * Unique point qui répond à « quelle boutique de quel utilisateur ? » pour les repositories du
 * module (produits, stock, ventes…) : évite de dupliquer la résolution et garantit qu'aucune requête
 * ne sort de son périmètre. V1 : boutique active = première boutique de l'utilisateur ; le futur
 * sélecteur multi-boutiques ne change que cette classe.
 */
@Singleton
class ActiveShopProvider @Inject constructor(
    private val sessionManager: SessionManager,
    private val shopDao: ShopDao
) {

    /** `null` sans session ou tant que la boutique n'est pas créée ; ré-émet si l'un ou l'autre change. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeScope(): Flow<ShopScope?> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(null)
            } else {
                shopDao.observeFirstForUser(userId).map { shop -> shop?.let { ShopScope(userId, it.id) } }
            }
        }.distinctUntilChanged()

    suspend fun currentScopeOrNull(): ShopScope? {
        val userId = sessionManager.getCurrentUserIdOnce() ?: return null
        val shop = shopDao.getFirstForUser(userId) ?: return null
        return ShopScope(userId, shop.id)
    }
}
