package com.naniger.arzikina.domain.model

/**
 * Catégorie de transaction (Nourriture, Transport, Salaire...).
 *
 * @param type détermine si cette catégorie est proposée pour les revenus ou
 * les dépenses (voir [TransactionType]).
 * @param id 0L tant que la catégorie n'a pas encore été enregistrée en base.
 */
data class Category(
    val id: Long = 0L,
    val name: String,
    val icon: CategoryIcon,
    val colorArgb: Long,
    val type: TransactionType,
    val createdAt: Long
) {
    /** Voir [SystemCategoryKey] : `null` pour une catégorie créée ou renommée par l'utilisateur. */
    val systemKey: SystemCategoryKey?
        get() = SystemCategoryKey.of(name, type)
}
