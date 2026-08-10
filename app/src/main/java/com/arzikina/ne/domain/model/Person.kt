package com.arzikina.ne.domain.model

/**
 * Une personne à qui l'utilisateur a prêté de l'argent, ou auprès de qui il en a emprunté (voir
 * [Loan.personId]). Volontairement minimal : ni [colorArgb][Account.colorArgb] ni initiale ne
 * sont stockées ici — l'avatar (couleur + initiale) se calcule à l'affichage à partir de [name]
 * (voir `presentation/utilities/loans`, fonction pure et déterministe, même principe que
 * [computeLoanStatus]) plutôt que d'ajouter une colonne dérivable de [name] (voir instructions
 * projet, "évite les colonnes inutiles").
 *
 * @param phone optionnel : une personne peut être ajoutée sans numéro de téléphone.
 * @param id 0L tant que la personne n'a pas encore été enregistrée en base.
 */
data class Person(
    val id: Long = 0L,
    val name: String,
    val phone: String? = null,
    val createdAt: Long
)
