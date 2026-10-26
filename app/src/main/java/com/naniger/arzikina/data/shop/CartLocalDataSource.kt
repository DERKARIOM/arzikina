package com.naniger.arzikina.data.shop

import androidx.room.withTransaction
import com.naniger.arzikina.data.local.dao.CartDao
import com.naniger.arzikina.data.local.dao.ShopDao
import com.naniger.arzikina.data.local.database.ArzikinaDatabase
import com.naniger.arzikina.data.repository.ShopScope
import com.naniger.arzikina.domain.shop.CartSnapshot
import javax.inject.Inject

/**
 * Lecture / écriture du panier de vente dans Room. Toujours en bloc, dans une transaction : lignes et clé
 * du brouillon ne peuvent jamais être enregistrées l'une sans l'autre (voir [CartSnapshot]).
 */
class CartLocalDataSource @Inject constructor(
    private val database: ArzikinaDatabase,
    private val cartDao: CartDao,
    private val shopDao: ShopDao
) {

    suspend fun load(scope: ShopScope): CartSnapshot = database.withTransaction {
        toCartSnapshot(cartDao.getDraft(scope.userId, scope.shopId), cartDao.getLines(scope.userId, scope.shopId))
    }

    /**
     * Remplace le panier de [scope]. Un panier vide supprime tout. Si la boutique n'existe plus (compte
     * supprimé, sauvegarde restaurée), rien n'est écrit : on ne laisse pas de lignes orphelines.
     */
    suspend fun save(scope: ShopScope, snapshot: CartSnapshot, now: Long = System.currentTimeMillis()) {
        database.withTransaction {
            if (shopDao.getById(scope.shopId, scope.userId) == null) return@withTransaction
            cartDao.deleteLines(scope.userId, scope.shopId)
            if (snapshot.lines.isEmpty()) {
                cartDao.deleteDraft(scope.userId, scope.shopId)
            } else {
                cartDao.insertDraft(snapshot.toDraftEntity(scope, now))
                cartDao.insertLines(snapshot.toLineEntities(scope))
            }
        }
    }

    /** Supprime tous les paniers de [userId] (suppression du compte, restauration d'une sauvegarde). */
    suspend fun purge(userId: Long) {
        database.withTransaction {
            cartDao.deleteAllLinesForUser(userId)
            cartDao.deleteAllDraftsForUser(userId)
        }
    }
}
