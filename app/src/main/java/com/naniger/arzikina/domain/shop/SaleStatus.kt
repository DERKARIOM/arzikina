package com.naniger.arzikina.domain.shop

/**
 * Statut d'une vente. Une vente validée n'est jamais supprimée : elle est ANNULÉE ([CANCELLED]),
 * avec des écritures correctrices (stock rétabli, transaction financière inverse) qui conservent la
 * trace de l'opération initiale.
 *
 * Stocké par NOM (texte) ; une valeur inconnue retombe sur [CONFIRMED] (voir [fromStored]) —
 * jamais renommer ni supprimer une entrée.
 */
enum class SaleStatus {
    CONFIRMED,
    CANCELLED;

    companion object {
        fun fromStored(value: String): SaleStatus = entries.firstOrNull { it.name == value } ?: CONFIRMED
    }
}
