package com.arzikina.ne.domain.model

/**
 * Icônes disponibles pour une catégorie. Comme pour [AccountIcon], le
 * mapping vers une icône Material concrète reste dans la couche presentation.
 */
enum class CategoryIcon {
    FOOD,
    TRANSPORT,
    HEALTH,
    SALARY,
    SHOPPING,
    GIFTS,
    INTERNET,
    WATER,
    ELECTRICITY,
    EDUCATION,
    HOME,
    OTHER,
    /** Ajouté pour les catégories par défaut Prêts/Emprunts (voir `DefaultCategories`) — simple
     * valeur d'enum stockée en texte (voir `Converters`), aucune migration Room nécessaire. */
    LOAN
}
