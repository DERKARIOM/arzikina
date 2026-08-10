package com.arzikina.ne.data.repository

import androidx.room.withTransaction
import com.arzikina.ne.data.local.dao.LoanDao
import com.arzikina.ne.data.local.dao.LoanPaymentDao
import com.arzikina.ne.data.local.dao.PersonDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Person
import com.arzikina.ne.domain.repository.PersonRepository
import com.arzikina.ne.domain.repository.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implémentation Room de [PersonRepository]. Isolation multi-utilisateurs : voir
 * `AccountRepositoryImpl` pour le raisonnement.
 *
 * [deletePerson] dépend de [LoanDao]/[LoanPaymentDao]/[TransactionDao] (pas seulement de
 * [PersonDao]) : voir la doc de [PersonRepository.deletePerson] — la cascade SQLite
 * `persons` → `loans` → `loan_payments` ne suffit pas, il faut aussi nettoyer les transactions
 * Arzikina liées, qu'aucune contrainte de clé étrangère ne peut atteindre.
 */
class PersonRepositoryImpl @Inject constructor(
    private val database: ArzikinaDatabase,
    private val personDao: PersonDao,
    private val loanDao: LoanDao,
    private val loanPaymentDao: LoanPaymentDao,
    private val transactionDao: TransactionDao,
    private val sessionManager: SessionManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : PersonRepository {

    override fun observePersons(): Flow<List<Person>> =
        sessionManager.observeCurrentUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                personDao.observeAllForUser(userId).map { entities -> entities.map { it.toDomain() } }
            }
        }

    override suspend fun getPerson(id: Long): Person? =
        withContext(ioDispatcher) { personDao.getById(id, requireCurrentUserId())?.toDomain() }

    override suspend fun savePerson(person: Person): Long = withContext(ioDispatcher) {
        val generatedId = personDao.upsert(person.toEntity(requireCurrentUserId()))
        // @Upsert ne retourne l'id généré QUE pour une insertion réelle (voir `AccountRepositoryImpl.saveAccount`).
        if (person.id != 0L) person.id else generatedId
    }

    override suspend fun deletePerson(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        database.withTransaction {
            loanDao.getAllForPerson(id, userId).forEach { loan ->
                loanPaymentDao.getAllForLoan(loan.id, userId).forEach { payment ->
                    transactionDao.deleteById(payment.transactionId, userId)
                }
                transactionDao.deleteById(loan.transactionId, userId)
            }
            // Supprime aussi, en cascade SQLite, tous les prêts/emprunts de cette personne et leurs
            // remboursements (voir `PersonDao.deleteById`).
            personDao.deleteById(id, userId)
        }
    }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
