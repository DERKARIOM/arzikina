package com.arzikina.ne.domain.model

/**
 * Icônes disponibles pour un compte.
 *
 * Le domaine ne dépend jamais d'Android ni de Compose : le mapping vers une
 * `ImageVector` concrète se fait uniquement dans la couche presentation.
 *
 * Purement visuel depuis l'introduction de [AccountType] : avant, cette
 * icône jouait AUSSI le rôle de "type" de compte par convention (voir
 * l'historique de [Account]) ; ce n'est plus le cas, [AccountType] porte
 * maintenant cette information. [CREDIT_CARD] reste ajoutée ici (plutôt que
 * dans une énumération séparée) pour continuer à réutiliser tel quel le
 * sélecteur d'icônes générique (`IconPickerAdapter<AccountIcon>`).
 */
enum class AccountIcon {
    CASH,
    BANK,
    MOBILE_MONEY,
    SAVINGS,
    WALLET,
    CREDIT_CARD,
    OTHER
}
