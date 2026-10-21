package com.naniger.arzikina.domain.model

/**
 * Icônes disponibles pour une catégorie. Comme pour [AccountIcon], le
 * mapping vers une icône Material concrète reste dans la couche presentation.
 *
 * CONTRAT DE COMPATIBILITÉ : le NOM de chaque valeur est stocké en texte (voir `Converters`),
 * synchronisé avec le serveur et écrit dans les sauvegardes. Il ne faut donc JAMAIS renommer ni
 * supprimer une valeur (ajouter est sans risque : aucune migration Room). Un ancien appareil qui
 * reçoit un nom inconnu retombe sur [OTHER] (voir `SyncEngineImpl`, `BackupMappers`). L'ordre des
 * valeurs n'est pas stocké : il sert seulement à l'ordre d'affichage dans le sélecteur.
 */
enum class CategoryIcon {
    // --- Icônes d'origine (ordre conservé : la première sert de repli dans les sauvegardes) ---
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
    LOAN,
    /** Ajouté pour la catégorie système "Frais et commissions" (voir `FeeCategoryNames`,
     * `DefaultCategories`) — même raisonnement que [LOAN], aucune migration Room nécessaire. */
    FEE,

    // --- Nourriture et boissons ---
    RESTAURANT,
    COFFEE,
    GROCERIES,

    // --- Transport ---
    FUEL,
    MOTORBIKE,
    BUS,
    TAXI,
    CAR,
    FLIGHT,

    // --- Logement et entretien ---
    RENT,
    REPAIRS,
    CLEANING,
    LAUNDRY,

    // --- Communication ---
    PHONE,
    TV,

    // --- Santé et bien-être ---
    PHARMACY,
    FITNESS,
    BEAUTY,

    // --- Famille, fêtes et solidarité ---
    CHILDREN,
    PETS,
    FAMILY,
    WEDDING,
    CELEBRATION,
    BIRTHDAY,
    CHARITY,

    // --- Travail, commerce et argent ---
    BUSINESS,
    SHOP,
    AGRICULTURE,
    SAVINGS,
    INVESTMENT,
    BANK,
    BILLS,
    INSURANCE,
    LEGAL,

    // --- Loisirs et vie quotidienne ---
    GAMES,
    MUSIC,
    SPORT,
    BOOKS,
    CLOTHING,
    TRAVEL,
    NATURE,

    // --- Religion ---
    MOSQUE,
    CHURCH
}
