package com.naniger.arzikina.domain.model

/**
 * Règle métier des comptes créés par défaut selon le [UsageMode] choisi à l'inscription.
 *
 * | Mode       | Compte Boutique | Compte Espèces |
 * |------------|-----------------|----------------|
 * | `SHOP`     | créé            | NON créé       |
 * | `PERSONAL` | NON créé        | créé           |
 * | `BOTH`     | créé            | créé           |
 *
 * Objet PUR (aucune dépendance Android/Room) : la règle est testable seule. La couche data s'en sert pour
 * décider QUOI créer ([missingKeys]) ; la construction des entités reste dans `DefaultAccounts`.
 *
 * Idempotence : la décision se fait par TYPE de compte déjà présent, pas par nom. Une nouvelle tentative
 * (double appui, reprise après coupure) ne recrée donc rien, et un compte renommé par l'utilisateur n'est
 * jamais dupliqué.
 */
object DefaultAccountPlan {

    /** Comptes à créer pour [mode], dans l'ordre d'affichage (Boutique avant Espèces). */
    fun keysFor(mode: UsageMode): List<DefaultAccountKey> = buildList {
        if (mode.createsShopAccount) add(DefaultAccountKey.SHOP)
        if (mode.createsCashAccount) add(DefaultAccountKey.CASH)
    }

    /**
     * Le compte Boutique créé par défaut est-il exclu des statistiques personnelles ?
     *
     * - `BOTH` : OUI. L'argent du commerce ne doit pas se mélanger au solde, aux revenus et aux dépenses
     *   personnels (voir `PersonalStatistics`). L'utilisateur peut lever l'exclusion dans le compte.
     * - `SHOP` : NON. La boutique est alors sa seule activité financière : l'exclure viderait le tableau de bord
     *   et les statistiques.
     * - `PERSONAL` : sans objet (aucun compte Boutique créé).
     *
     * Ne concerne QUE les comptes créés à l'inscription : aucun compte existant n'est modifié.
     */
    fun excludesShopFromStatistics(mode: UsageMode): Boolean = mode == UsageMode.BOTH

    /** Type fonctionnel du compte créé pour [key] (seules les clés de [keysFor] sont concernées). */
    fun accountTypeOf(key: DefaultAccountKey): AccountType = when (key) {
        DefaultAccountKey.SHOP -> AccountType.BOUTIQUE
        DefaultAccountKey.CASH -> AccountType.CASH
        DefaultAccountKey.BANK -> AccountType.BANK
        DefaultAccountKey.MOBILE_MONEY -> AccountType.MOBILE_MONEY
        DefaultAccountKey.SAVINGS -> AccountType.SAVINGS
        DefaultAccountKey.WALLET -> AccountType.CASH
    }

    /**
     * Comptes encore à créer pour [mode], sachant que l'utilisateur possède déjà des comptes actifs des types
     * [existingTypes]. Liste vide = rien à faire (opération déjà effectuée).
     */
    fun missingKeys(mode: UsageMode, existingTypes: Set<AccountType>): List<DefaultAccountKey> =
        keysFor(mode).filter { accountTypeOf(it) !in existingTypes }
}
