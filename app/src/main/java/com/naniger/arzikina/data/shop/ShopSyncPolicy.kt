package com.naniger.arzikina.data.shop

/**
 * Règles PURES de la réception des données Boutique (testées sans Android) — voir
 * `ShopSyncHandler` et `claude/shop/SYNC-BOUTIQUE-CONTRAT.md`.
 */
internal object ShopSyncPolicy {

    /**
     * Fenêtre pendant laquelle une ligne dont un parent obligatoire est encore inconnu est REPRISE au pull
     * suivant (le parent peut arriver plus tard : envoi partiel, ordre d'envoi). Passé ce délai la ligne est
     * considérée orpheline et abandonnée, pour ne jamais bloquer indéfiniment le curseur de son type.
     */
    const val DEFER_WINDOW_MILLIS: Long = 48L * 60 * 60 * 1000

    /**
     * Faut-il appliquer l'état serveur sur la ligne locale ?
     * - ligne inconnue : oui (création, l'appelant a déjà vérifié `allowCreate`) ;
     * - résultat d'un envoi (`allowCreate == false`) : toujours, c'est la confirmation du serveur ;
     * - pull : seulement si le serveur est PLUS RÉCENT que ce que l'appareil a déjà appliqué. Un
     *   retéléchargement (curseur repris après une référence manquante) ne réécrit donc pas une ligne
     *   modifiée localement et pas encore envoyée.
     */
    fun shouldApply(allowCreate: Boolean, localVersion: Int?, serverVersion: Int): Boolean =
        localVersion == null || !allowCreate || serverVersion > localVersion

    /** `true` si une ligne à référence manquante doit être reprise plus tard plutôt qu'abandonnée. */
    fun isDeferrable(serverUpdatedAt: Long, nowMillis: Long): Boolean =
        nowMillis - serverUpdatedAt <= DEFER_WINDOW_MILLIS
}
