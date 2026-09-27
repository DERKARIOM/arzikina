package com.naniger.arzikina.domain.model

/**
 * Levée par `TransactionTemplateRepository.saveTemplate` quand on tente de créer un modèle à partir
 * d'une transaction qui en a DÉJÀ un actif (voir [TransactionTemplate.sourceTransactionId]) — évite
 * « Transaction → Modèle A » puis « Transaction → Modèle B » sans action explicite (double clic,
 * formulaire ouvert deux fois, modèle arrivé entre-temps par synchronisation…).
 *
 * @param existingTemplateId modèle déjà lié, pour proposer « Voir le modèle » à la place.
 */
class TemplateAlreadyLinkedException(val existingTemplateId: Long) :
    IllegalStateException("Un modèle a déjà été créé à partir de cette transaction.")
