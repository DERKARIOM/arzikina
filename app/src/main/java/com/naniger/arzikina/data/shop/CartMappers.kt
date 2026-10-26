package com.naniger.arzikina.data.shop

import com.naniger.arzikina.data.local.entity.CartDraftEntity
import com.naniger.arzikina.data.local.entity.CartLineEntity
import com.naniger.arzikina.data.repository.ShopScope
import com.naniger.arzikina.domain.shop.CartSnapshot

/**
 * Relit un panier enregistré. Défensif : sans brouillon, avec une clé vide ou sans ligne valide
 * (quantité > 0), on repart d'un panier vide avec une NOUVELLE clé plutôt que de réutiliser un état
 * incohérent. Les lignes sont remises dans l'ordre d'ajout ([CartLineEntity.position]).
 */
internal fun toCartSnapshot(draft: CartDraftEntity?, lines: List<CartLineEntity>): CartSnapshot {
    val valid = lines.filter { it.quantityMilli > 0L }.sortedBy { it.position }
    if (draft == null || draft.draftKey.isBlank() || valid.isEmpty()) return CartSnapshot.fresh()
    return CartSnapshot(valid.associate { it.productId to it.quantityMilli }, draft.draftKey)
}

/** Lignes à enregistrer pour [snapshot] ; `position` = rang dans le panier. */
internal fun CartSnapshot.toLineEntities(scope: ShopScope): List<CartLineEntity> =
    lines.entries.mapIndexed { index, (productId, quantityMilli) ->
        CartLineEntity(scope.userId, scope.shopId, productId, quantityMilli, index)
    }

internal fun CartSnapshot.toDraftEntity(scope: ShopScope, now: Long): CartDraftEntity =
    CartDraftEntity(scope.userId, scope.shopId, draftKey, now)
