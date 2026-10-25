package com.naniger.arzikina.data.local

/** Construction des motifs `LIKE` à partir d'une saisie utilisateur (partagée par toutes les recherches SQL). */
object SqlLike {

    /**
     * Motif « contient » : `\`, `%` et `_` saisis par l'utilisateur sont échappés (la requête doit
     * déclarer `ESCAPE '\'`), puis le tout est entouré de `%`. Une saisie vide donne `%%` (tout correspond).
     */
    fun containsPattern(query: String): String {
        val escaped = query.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }
}
