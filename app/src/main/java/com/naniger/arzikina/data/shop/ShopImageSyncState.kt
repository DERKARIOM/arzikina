package com.naniger.arzikina.data.shop

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mémoire (par utilisateur local) du RATTRAPAGE UNIQUE de la synchronisation des photos de produits.
 *
 * Avant les photos synchronisées, les produits déjà envoyés au serveur n'avaient pas de photo côté serveur. Au
 * premier passage après la mise à jour, chaque produit synchronisé qui a une photo est remis en file d'envoi
 * (voir `ShopSyncHandler.enqueueUnsynced`), puis cet état est marqué. Tant qu'il ne l'est pas, un produit reçu
 * SANS photo ne fait pas perdre la photo locale ; ensuite le serveur fait foi (une photo retirée ailleurs l'est ici).
 *
 * Simple booléen en préférences : volontairement hors Room (aucune migration, aucune colonne en plus).
 */
@Singleton
class ShopImageSyncState @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isBackfilled(userId: Long): Boolean = prefs.getBoolean(key(userId), false)

    fun markBackfilled(userId: Long) {
        prefs.edit().putBoolean(key(userId), true).apply()
    }

    private fun key(userId: Long) = "backfilled_$userId"

    private companion object {
        const val PREFS_NAME = "shop_image_sync"
    }
}
