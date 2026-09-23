package com.naniger.arzikina.domain.model

/**
 * Comptes créés par défaut pour un nouvel utilisateur (voir `DefaultAccounts`), identifiés par
 * leur nom canonique. Même principe que [SystemCategoryKey] (voir sa documentation) : le nom
 * stocké ne change jamais, seul son affichage suit la langue, et un compte renommé par
 * l'utilisateur s'affiche toujours tel quel.
 */
enum class DefaultAccountKey(val canonicalName: String) {
    CASH("Espèces"),
    BANK("Banque"),
    MOBILE_MONEY("Mobile Money"),
    SAVINGS("Épargne"),
    WALLET("Wallet");

    companion object {
        fun of(name: String): DefaultAccountKey? = entries.firstOrNull { it.canonicalName == name }

        /** Voir [SystemCategoryKey.canonicalNameFor]. */
        fun canonicalNameFor(input: String, labelsOf: (DefaultAccountKey) -> Collection<String>): String =
            entries.firstOrNull { key -> input == key.canonicalName || input in labelsOf(key) }
                ?.canonicalName
                ?: input
    }
}
