package com.naniger.arzikina.domain.model

/**
 * Un prêt accordé ou un emprunt contracté (voir [LoanType]) — associé à une [Person] ET à un
 * [Account] Arzikina (voir [accountId]).
 *
 * [accountId] n'est PAS optionnel : contrairement à ce qu'une lecture rapide du cahier des
 * charges pourrait suggérer ("doit pouvoir être associé à un compte"), le rendre facultatif
 * casserait la synchronisation automatique avec les transactions (section 8/15 du cahier des
 * charges), qui a besoin d'un compte concret pour générer la transaction à chaque création/
 * remboursement. Un prêt/emprunt a donc TOUJOURS un compte, comme une [Transaction] en a
 * toujours un.
 *
 * Devise : PAS de champ dédié ici (contrairement à ce que "la devise doit être configurable"
 * pourrait suggérer) — elle est entièrement héritée du compte associé ([accountId], voir
 * [Account.currencyCode]), exactement comme pour une [Transaction] classique. Permettre une
 * devise de prêt différente de celle du compte introduirait une incohérence qu'aucune partie de
 * l'application ne sait actuellement résoudre (aucune conversion de change n'existe nulle part
 * dans Arzikina) ; la "configurabilité" demandée est déjà satisfaite par le choix du compte
 * (chaque compte a sa propre devise, configurable, XOF par défaut — voir
 * [com.naniger.arzikina.util.Constants.DEFAULT_CURRENCY_CODE]).
 *
 * @param amount montant total prêté/emprunté, en unité mineure de la devise du compte associé
 * (même convention que [Transaction.amount]), toujours positif.
 * @param amountRepaid somme des [LoanPayment.amount] déjà enregistrés — DÉNORMALISÉ (recalculé à
 * chaque remboursement par `LoanRepository`, jamais modifié directement ailleurs), pour éviter de
 * ré-agréger l'historique des remboursements à chaque affichage.
 * @param remainingAmount `amount - amountRepaid` — DÉNORMALISÉ pour la même raison que
 * [amountRepaid] (voir aussi [LoanStatus], recalculé selon le même principe).
 * @param reason voir [LoanReason] ; [reasonCustomText] n'a de sens que si [reason] vaut
 * [LoanReason.OTHER] (`null` sinon).
 * @param status voir [LoanStatus] : TOUJOURS recalculé via [computeLoanStatus], jamais assigné à
 * la main par un appelant.
 * @param transactionId id de la [Transaction] Arzikina générée automatiquement pour le décaissement
 * initial de ce prêt/emprunt (mouvement d'argent à sa création — voir `data/local/database/DefaultCategories`,
 * catégories "Prêt accordé"/"Emprunt reçu"). Même raisonnement que [LoanPayment.transactionId] :
 * TOUJOURS renseigné, un [Loan] n'existe que si sa transaction a été créée avec succès dans la même
 * opération (voir `LoanRepository.saveLoan`). Distinct des transactions de remboursement (voir
 * [LoanPayment.transactionId]), qui ont chacune leur propre transaction.
 * @param giftedAmount part du solde restant transformée en cadeau (voir « Transformer en cadeau ») —
 * 0 tant que le prêt/emprunt n'a pas été transformé. Après transformation :
 * `amountRepaid + giftedAmount == amount`, [remainingAmount] vaut 0 et [status] vaut
 * [LoanStatus.GIFTED]. Le montant historique [amount] n'est JAMAIS modifié (traçabilité).
 * @param giftTransactionId id de la [Transaction] « Cadeaux » qui porte [giftedAmount] — `null` tant
 * que le prêt/emprunt n'a pas été transformé. Égal à [transactionId] quand rien n'avait été
 * remboursé (la transaction de décaissement est alors reclassée sur place, sans nouvelle ligne).
 * Comme [transactionId], pas de contrainte de clé étrangère SQL.
 * @param giftedAt instant de la transformation en cadeau (`null` sinon) — distinct de la date de la
 * transaction cadeau, qui conserve la date d'origine du prêt/emprunt ([startDate]).
 * @param id 0L tant que le prêt/emprunt n'a pas encore été enregistré en base.
 */
data class Loan(
    val id: Long = 0L,
    val personId: Long,
    val accountId: Long,
    val type: LoanType,
    val amount: Long,
    val amountRepaid: Long = 0L,
    val remainingAmount: Long,
    val startDate: Long,
    val dueDate: Long,
    val reason: LoanReason,
    val reasonCustomText: String? = null,
    val repaymentMode: RepaymentMode,
    val description: String = "",
    val status: LoanStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val transactionId: Long,
    val giftedAmount: Long = 0L,
    val giftTransactionId: Long? = null,
    val giftedAt: Long? = null
)

/**
 * Statut réel à l'instant [nowEpochMillis] (voir [computeLoanStatus]) — point d'entrée à privilégier
 * partout dans l'application : il transmet TOUJOURS [Loan.giftedAmount], qu'un appel direct à
 * [computeLoanStatus] pourrait oublier.
 */
fun Loan.liveStatus(nowEpochMillis: Long): LoanStatus =
    computeLoanStatus(amount, amountRepaid, startDate, dueDate, nowEpochMillis, giftedAmount)
