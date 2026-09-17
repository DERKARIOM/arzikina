package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.TransactionTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Contrat d'accès aux données des modèles de transaction (cahier des charges "Marketplace
 * personnelle" — voir [TransactionTemplate]). Voir [AccountRepository] pour le raisonnement
 * derrière cette séparation domaine/implémentation.
 *
 * Bibliothèque strictement PERSONNELLE : aucune méthode de cette interface ne partage un modèle
 * entre utilisateurs ni ne dépend d'un serveur — l'action "Acheter" (création d'une transaction à
 * partir d'un modèle) reste entièrement du ressort de `TransactionFormFragment`/
 * `TransactionRepository`, jamais de cette interface (voir la doc de [TransactionTemplate]).
 */
interface TransactionTemplateRepository {

    /** Flux réactif de tous les modèles, favoris en tête (voir cahier des charges section 7),
     * puis par nom. */
    fun observeTemplates(): Flow<List<TransactionTemplate>>

    suspend fun getTemplate(id: Long): TransactionTemplate?

    /**
     * Si [TransactionTemplate.id] vaut 0 : crée le modèle. Sinon, met à jour les champs
     * modifiables — ne modifie JAMAIS une transaction déjà créée à partir de ce modèle (voir la
     * doc de [TransactionTemplate], aucun lien n'est conservé entre les deux).
     *
     * Retourne l'id définitif du modèle.
     */
    suspend fun saveTemplate(template: TransactionTemplate): Long

    /** Cahier des charges section 6, "Dupliquer" — copie tous les champs SAUF [TransactionTemplate.name]
     * (suffixé, voir l'implémentation) et [TransactionTemplate.isFavorite] (toujours `false` pour la
     * copie, jamais héritée). Retourne l'id du nouveau modèle. */
    suspend fun duplicateTemplate(id: Long): Long

    /** Cahier des charges section 7, action ⭐ isolée du reste du formulaire d'édition — évite
     * d'exiger tous les autres champs pour ce simple bascule. */
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    suspend fun deleteTemplate(id: Long)
}
