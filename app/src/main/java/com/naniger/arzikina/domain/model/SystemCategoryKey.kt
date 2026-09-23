package com.naniger.arzikina.domain.model

/**
 * Catégories créées par Arzikina (voir `DefaultCategories`), identifiées par leur NOM CANONIQUE
 * français et leur type — chantier i18n, étape 4.
 *
 * Pourquoi une clé DÉDUITE plutôt qu'une colonne en base : le nom canonique est déjà l'identifiant
 * utilisé partout (recherche des catégories Prêts/Frais par nom, synchronisation serveur,
 * sauvegardes). Il n'est donc JAMAIS modifié en base ; seule sa présentation change selon la
 * langue. Aucune migration, aucune donnée touchée, rien à changer côté serveur.
 *
 * Règle d'affichage (voir `Category.systemKey`) : une catégorie n'a une clé QUE si son nom est
 * encore exactement le nom canonique. Une catégorie renommée par l'utilisateur, ou créée par lui
 * (« Dépenses maman »), n'en a pas et s'affiche toujours telle quelle.
 *
 * Ajouter une catégorie par défaut = une entrée ici + une ligne dans `DefaultCategories` + un
 * libellé dans `strings.xml` (voir `SystemCategoryKey.labelRes` côté présentation).
 */
enum class SystemCategoryKey(val canonicalName: String, val type: TransactionType) {
    SALARY("Salaire", TransactionType.INCOME),
    OTHER_INCOME("Divers", TransactionType.INCOME),
    FOOD("Nourriture", TransactionType.EXPENSE),
    TRANSPORT("Transport", TransactionType.EXPENSE),
    HEALTH("Santé", TransactionType.EXPENSE),
    SHOPPING("Shopping", TransactionType.EXPENSE),
    GIFTS("Cadeaux", TransactionType.EXPENSE),
    INTERNET("Internet", TransactionType.EXPENSE),
    WATER("Eau", TransactionType.EXPENSE),
    ELECTRICITY("Électricité", TransactionType.EXPENSE),
    EDUCATION("Éducation", TransactionType.EXPENSE),
    HOME("Maison", TransactionType.EXPENSE),
    OTHER_EXPENSE("Divers", TransactionType.EXPENSE),
    LOAN_DISBURSEMENT_LENT(LoanCategoryNames.DISBURSEMENT_LENT, TransactionType.EXPENSE),
    LOAN_REPAYMENT_LENT(LoanCategoryNames.REPAYMENT_LENT, TransactionType.INCOME),
    LOAN_DISBURSEMENT_BORROWED(LoanCategoryNames.DISBURSEMENT_BORROWED, TransactionType.INCOME),
    LOAN_REPAYMENT_BORROWED(LoanCategoryNames.REPAYMENT_BORROWED, TransactionType.EXPENSE),
    FEES(FeeCategoryNames.FEES, TransactionType.EXPENSE);

    companion object {
        /** Clé d'une catégorie dont le nom est encore exactement le nom canonique, sinon `null`. */
        fun of(name: String, type: TransactionType): SystemCategoryKey? =
            entries.firstOrNull { it.type == type && it.canonicalName == name }

        /**
         * Nom à ENREGISTRER pour une saisie de formulaire : si [input] est le libellé d'une
         * catégorie par défaut de même [type], dans n'importe quelle langue supportée ([labelsOf]
         * fournit ces libellés), on enregistre son nom canonique. Ainsi, modifier « Salary » sans
         * toucher au nom ne casse jamais la traduction ni la recherche par nom des catégories
         * système. Toute autre saisie est enregistrée telle quelle.
         */
        fun canonicalNameFor(
            input: String,
            type: TransactionType,
            labelsOf: (SystemCategoryKey) -> Collection<String>
        ): String = entries
            .firstOrNull { key -> key.type == type && (input == key.canonicalName || input in labelsOf(key)) }
            ?.canonicalName
            ?: input
    }
}
