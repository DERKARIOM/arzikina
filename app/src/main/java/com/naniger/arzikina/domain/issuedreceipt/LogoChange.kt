package com.naniger.arzikina.domain.issuedreceipt

/** Ce que l'utilisateur veut faire du logo lors de l'enregistrement du profil émetteur. */
sealed interface LogoChange {

    /** Conserver le logo actuel (ou l'absence de logo). */
    data object Keep : LogoChange

    /** Supprimer le logo. */
    data object Remove : LogoChange

    /** Remplacer par l'image désignée par [sourceUri] (URI `content://` choisie dans la galerie ou les fichiers). */
    data class Replace(val sourceUri: String) : LogoChange
}
