package com.arzikina.ne.domain.repository

import com.arzikina.ne.domain.model.Person
import kotlinx.coroutines.flow.Flow

/**
 * Contrat d'accès aux données des personnes (voir [Person]) avec qui l'utilisateur
 * prête/emprunte. Voir [AccountRepository] pour le raisonnement derrière cette séparation.
 */
interface PersonRepository {

    /** Flux réactif de toutes les personnes, triées par nom. */
    fun observePersons(): Flow<List<Person>>

    suspend fun getPerson(id: Long): Person?

    /** Crée la personne si [Person.id] vaut 0, la met à jour sinon. Retourne l'id définitif. */
    suspend fun savePerson(person: Person): Long

    /**
     * Supprime la personne ET, atomiquement, tout son historique de prêts/emprunts (voir
     * [LoanRepository]) — y compris les transactions Arzikina générées automatiquement pour
     * chacun (décaissements et remboursements), qu'une simple suppression en cascade SQLite ne
     * peut pas atteindre (voir `data/local/entity/LoanPaymentEntity`, pas de `FOREIGN KEY` vers
     * `transactions`).
     */
    suspend fun deletePerson(id: Long)
}
