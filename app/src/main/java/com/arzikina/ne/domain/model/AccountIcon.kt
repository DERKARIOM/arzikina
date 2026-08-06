package com.arzikina.ne.domain.model

/**
 * Icônes disponibles pour un compte.
 *
 * Le domaine ne dépend jamais d'Android ni de Compose : le mapping vers une
 * `ImageVector` concrète se fait uniquement dans la couche presentation.
 */
enum class AccountIcon {
    CASH,
    BANK,
    MOBILE_MONEY,
    SAVINGS,
    WALLET,
    OTHER
}
