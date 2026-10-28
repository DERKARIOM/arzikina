package com.naniger.arzikina.domain.model

/**
 * Mode d'utilisation choisi par l'utilisateur à l'inscription (« Comment souhaitez-vous utiliser Arsikina ? »).
 *
 * C'est une PRÉFÉRENCE INITIALE, jamais une restriction : elle détermine uniquement les comptes créés par défaut
 * (voir la règle métier : boutique → compte Boutique, finances personnelles → compte Espèces, les deux → les deux).
 * Un utilisateur peut toujours créer d'autres types de comptes plus tard.
 *
 * Les noms ([name]) sont stockés (Room), synchronisés et envoyés au serveur (`users.usage_mode`) : ne JAMAIS
 * renommer une entrée. Une valeur `null` (côté [User]) signifie « jamais demandé » : cas des utilisateurs inscrits
 * avant l'introduction de ce choix, auxquels rien n'est imposé.
 */
enum class UsageMode {
    /** Gestion de boutique : produits, stocks, ventes. */
    SHOP,

    /** Finances personnelles : revenus, dépenses, comptes, budgets. */
    PERSONAL,

    /** Boutique et finances personnelles. */
    BOTH;

    /** Un compte de type [AccountType.BOUTIQUE] est créé par défaut. */
    val createsShopAccount: Boolean get() = this == SHOP || this == BOTH

    /** Un compte de type [AccountType.CASH] (Espèces) est créé par défaut. */
    val createsCashAccount: Boolean get() = this == PERSONAL || this == BOTH

    companion object {
        /** Lecture tolérante d'une valeur stockée ou reçue du serveur ; `null` si absente ou inconnue (jamais d'exception). */
        fun fromCode(code: String?): UsageMode? = entries.firstOrNull { it.name == code }
    }
}
