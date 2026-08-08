package com.arzikina.ne.domain.model

/**
 * Mouvement financier unique (dépense ou revenu).
 *
 * @param amount montant en unité mineure de la devise (voir [Account]), toujours
 * positif : c'est [type] qui porte l'information "revenu" ou "dépense".
 * @param accountId compte sur lequel le mouvement est imputé.
 * @param categoryId catégorie du mouvement ; son [Category.type] doit
 * correspondre à [type] (cohérence vérifiée côté formulaire).
 * @param date instant choisi par l'utilisateur (date + heure fusionnées en
 * epoch millis), distinct de [createdAt] qui est la date d'enregistrement.
 * @param description libre, vide par défaut.
 * @param receiptPhotoUri chemin local d'une photo de reçu, optionnel.
 * @param latitude, @param longitude localisation du mouvement : champs
 * préparés pour une future fonctionnalité de géolocalisation des dépenses,
 * non exploités par l'UI actuelle.
 * @param paymentMethod moyen de paiement, optionnel ("si applicable" — voir
 * [PaymentMethod]) : `null` tant que l'utilisateur ne l'a pas précisé, y
 * compris pour toutes les transactions enregistrées avant l'introduction de
 * ce champ.
 * @param id 0L tant que la transaction n'a pas encore été enregistrée en base.
 */
data class Transaction(
    val id: Long = 0L,
    val amount: Long,
    val type: TransactionType,
    val accountId: Long,
    val categoryId: Long,
    val date: Long,
    val description: String = "",
    val receiptPhotoUri: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val paymentMethod: PaymentMethod? = null,
    val createdAt: Long
)

/**
 * Montant signé : positif pour un revenu, négatif pour une dépense. Évite de
 * réécrire ce `when` dans chaque calcul de solde (solde courant d'un compte,
 * solde historique par transaction — voir
 * [com.arzikina.ne.presentation.transactions.computeRunningBalances]).
 */
fun Transaction.signedAmount(): Long = if (type == TransactionType.INCOME) amount else -amount
