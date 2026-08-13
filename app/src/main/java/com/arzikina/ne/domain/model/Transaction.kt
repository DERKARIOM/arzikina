package com.arzikina.ne.domain.model

/**
 * Mouvement financier unique : dépense, revenu, ou transfert entre deux
 * comptes (voir [TransactionType]).
 *
 * @param amount montant en unité mineure de la devise (voir [Account]), toujours
 * positif : c'est [type] qui porte l'information "revenu", "dépense" ou "transfert".
 * @param accountId compte sur lequel le mouvement est imputé — pour un
 * [TransactionType.TRANSFER], le compte SOURCE (l'argent en sort).
 * @param transferAccountId compte DESTINATION d'un transfert (l'argent y
 * entre) ; `null` sauf pour [TransactionType.TRANSFER]. Distinct de
 * [accountId] par construction (un transfert vers le même compte n'a pas de
 * sens, vérifié côté formulaire).
 * @param categoryId catégorie du mouvement ; son [Category.type] doit
 * correspondre à [type] (cohérence vérifiée côté formulaire). `null`
 * uniquement pour [TransactionType.TRANSFER], qui n'a pas de catégorie (voir
 * [TransactionType]) — toujours renseignée pour un revenu ou une dépense.
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
 * @param feeTransactionId présent UNIQUEMENT sur une transaction "principale" qui a des frais
 * supplémentaires (voir cahier des charges "Gestion des frais") : pointe vers une AUTRE
 * [Transaction] auto-générée (type [TransactionType.EXPENSE], compte = celui choisi pour les
 * frais, catégorie système "Frais et commissions", montant = le montant des frais) plutôt que de
 * porter le montant/la description des frais directement sur cette transaction — voir
 * `TransactionRepositoryImpl` pour l'écriture conjointe des deux lignes. `null` pour une
 * transaction normale ET pour une transaction de frais elle-même (pas de chaînage).
 * @param feeType présent UNIQUEMENT sur la transaction DE FRAIS pointée par [feeTransactionId]
 * d'une autre transaction (voir [FeeType]) ; `null` partout ailleurs, y compris sur la transaction
 * principale qui la référence.
 */
data class Transaction(
    val id: Long = 0L,
    val amount: Long,
    val type: TransactionType,
    val accountId: Long,
    val transferAccountId: Long? = null,
    val categoryId: Long?,
    val date: Long,
    val description: String = "",
    val receiptPhotoUri: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val paymentMethod: PaymentMethod? = null,
    val createdAt: Long,
    val feeTransactionId: Long? = null,
    val feeType: FeeType? = null
)

/**
 * Montant signé côté [Transaction.accountId] : positif pour un revenu,
 * négatif pour une dépense OU pour la jambe "source" d'un transfert (l'argent
 * QUITTE ce compte). Ne couvre PAS le crédit du compte destination d'un
 * transfert ([Transaction.transferAccountId]) — voir
 * [com.arzikina.ne.presentation.accounts.computeCurrentBalances], seul
 * endroit qui doit connaître les DEUX comptes d'une même transaction. Évite
 * de réécrire ce `when` dans chaque calcul de solde.
 */
fun Transaction.signedAmount(): Long = if (type == TransactionType.INCOME) amount else -amount
