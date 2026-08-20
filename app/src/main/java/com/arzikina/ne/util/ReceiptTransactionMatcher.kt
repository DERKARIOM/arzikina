package com.arzikina.ne.util

import com.arzikina.ne.domain.model.Account
import com.arzikina.ne.domain.model.Category

/**
 * Correspondance compte/catégorie pour préremplir le formulaire "Ajouter une transaction" depuis un
 * reçu (cahier des charges "Créer une transaction depuis un reçu", sections 8-9). Complète
 * [ReceiptTransactionInfoParser] (qui n'a accès qu'au texte du reçu, jamais aux comptes/catégories de
 * l'utilisateur, déjà en base).
 *
 * IMPORTANT — voir la doc de tête de [ReceiptTransactionInfoParser] : ces deux fonctions ne
 * choisissent JAMAIS arbitrairement (ex. "le premier compte de la liste", "une catégorie par
 * défaut") — `null` si aucune correspondance fiable n'est trouvée, à charge pour l'utilisateur de
 * compléter lui-même dans le formulaire (déjà capable de gérer un compte/une catégorie non
 * pré-rempli).
 *
 * Fonctions PURES (aucun accès Room/disque) — testables directement, voir
 * `ReceiptTransactionMatcherTest`. Les comptes/catégories à comparer sont fournis par l'appelant
 * (voir `ReceiptDetailViewModel`, Étape 6 à venir), déjà filtrés pour l'utilisateur courant (et, pour
 * les catégories, déjà filtrés par [com.arzikina.ne.domain.model.TransactionType] détecté via
 * `CategoryRepository.observeCategoriesByType` — pas le rôle de cet objet de refaire ce filtrage).
 */
object ReceiptTransactionMatcher {

    /**
     * Compte dont [Account.mobileMoneyPackageName] correspond EXACTEMENT au package source du reçu
     * ([sourceApp], voir [com.arzikina.ne.domain.model.Receipt.sourceApp]) — seule correspondance
     * jugée assez fiable pour une suggestion automatique (l'app qui a partagé le reçu EST l'app
     * Mobile Money du compte concerné). Comparaison de packages Android : sensible à la casse,
     * jamais normalisée (ce sont des identifiants techniques, pas du texte affiché).
     */
    fun matchAccountBySourceApp(accounts: List<Account>, sourceApp: String?): Account? {
        if (sourceApp.isNullOrBlank()) return null
        return accounts.firstOrNull { it.mobileMoneyPackageName == sourceApp }
    }

    /**
     * Catégorie dont le nom apparaît dans [keyword] (ou inversement, pour les noms de catégorie
     * COMPOSÉS d'un seul mot-clé plus général, ex. "Assurance" dans "Assurance vie") — comparaison
     * insensible aux accents/casse (voir [ReceiptAmountParser.normalizeForMatching]). Les noms trop
     * courts (< [MIN_CATEGORY_NAME_LENGTH_FOR_MATCH]) sont ignorés pour éviter un faux positif sur un
     * nom de catégorie très générique (ex. "IT") qui apparaîtrait par hasard dans presque tout texte.
     *
     * @param keyword texte source de la comparaison (ex. [ReceiptTransactionInfo.description], ou le
     * texte brut du reçu pour maximiser les chances d'une correspondance) — `null`/vide : aucune
     * correspondance possible, retourne `null` immédiatement.
     */
    fun matchCategoryByKeyword(categories: List<Category>, keyword: String?): Category? {
        val normalizedKeyword = keyword
            ?.let { ReceiptAmountParser.normalizeForMatching(it) }
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return categories.firstOrNull { category ->
            val normalizedName = ReceiptAmountParser.normalizeForMatching(category.name)
            normalizedName.length >= MIN_CATEGORY_NAME_LENGTH_FOR_MATCH &&
                (normalizedKeyword.contains(normalizedName) || normalizedName.contains(normalizedKeyword))
        }
    }

    private const val MIN_CATEGORY_NAME_LENGTH_FOR_MATCH = 3
}
