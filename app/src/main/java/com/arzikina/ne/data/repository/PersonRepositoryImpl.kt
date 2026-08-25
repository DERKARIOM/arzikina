package com.arzikina.ne.data.repository

import androidx.room.withTransaction
import com.arzikina.ne.data.local.dao.LoanDao
import com.arzikina.ne.data.local.dao.LoanPaymentDao
import com.arzikina.ne.data.local.dao.PersonDao
import com.arzikina.ne.data.local.dao.TransactionDao
import com.arzikina.ne.data.local.database.ArzikinaDatabase
import com.arzikina.ne.data.local.entity.PersonEntity
import com.arzikina.ne.data.mapper.toDomain
import com.arzikina.ne.data.mapper.toEntity
import com.arzikina.ne.data.remote.dto.PersonSyncPayload
import com.arzikina.ne.di.IoDispatcher
import com.arzikina.ne.domain.model.Person
import com.arzikina.ne.domain.model.SyncOperation
import com.arzikina.ne.domain.repository.PersonRepository
import com.arzikina.ne.domain.repository.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.UUID
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
    private val syncQueueEnqueuer: SyncQueueEnqueuer,
    private val json: Json,
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

    /**
     * [PersonEntity.syncId]/[PersonEntity.version] PRÉSERVÉS d'une modification à l'autre (jamais
     * réinitialisés par `toEntity`, qui les ignore volontairement) — même raisonnement que
     * `CategoryRepositoryImpl.saveCategory` (voir sa KDoc pour le détail complet). [Person] (voir sa
     * définition) ne porte pas d'`updatedAt` : recalculé ici à chaque enregistrement, comme
     * `createdAt` l'était déjà avant cette étape.
     */
    override suspend fun savePerson(person: Person): Long = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = if (person.id != 0L) personDao.getById(person.id, userId) else null
        val now = System.currentTimeMillis()

        val entity = person.toEntity(userId).copy(
            syncId = existing?.syncId ?: UUID.randomUUID().toString(),
            updatedAt = now,
            deletedAt = existing?.deletedAt,
            version = existing?.version ?: 1
        )
        // @Upsert ne retourne l'id généré QUE pour une insertion réelle (voir `AccountRepositoryImpl.saveAccount`).
        val generatedId = personDao.upsert(entity)
        val savedId = if (person.id != 0L) person.id else generatedId
        enqueuePersonSync(
            entity.copy(id = savedId),
            operation = if (existing == null) SyncOperation.CREATE else SyncOperation.UPDATE
        )
        savedId
    }

    /**
     * Suppression DOUCE de la personne (voir `PersonDao.softDeleteById`) — les prêts/emprunts
     * restent supprimés PHYSIQUEMENT, EXPLICITEMENT (comportement inchangé par rapport à avant
     * cette étape) : un `UPDATE` ne déclenche jamais le cascade SQLite `ForeignKey.CASCADE` que le
     * `DELETE` d'avant fournissait pour `loans.personId` — voir `loanDao.deleteById` ajouté
     * ci-dessous, qui cascade lui-même sur `loan_payments` via `loanId` (relation NON touchée par
     * cette étape). Les prêts/emprunts eux-mêmes ne sont PAS synchronisés à cette étape.
     *
     * `syncId ?: UUID.randomUUID()...` : même filet de sécurité que
     * `CategoryRepositoryImpl.deleteCategory` pour une personne jamais modifiée depuis l'ajout de
     * la synchronisation.
     */
    override suspend fun deletePerson(id: Long) = withContext(ioDispatcher) {
        val userId = requireCurrentUserId()
        val existing = personDao.getById(id, userId) ?: return@withContext
        val now = System.currentTimeMillis()

        database.withTransaction {
            loanDao.getAllForPerson(id, userId).forEach { loan ->
                loanPaymentDao.getAllForLoan(loan.id, userId).forEach { payment ->
                    transactionDao.deleteById(payment.transactionId, userId)
                }
                transactionDao.deleteById(loan.transactionId, userId)
                // Suppression PHYSIQUE explicite (cascade SQLite `ForeignKey.CASCADE` de
                // `loans.personId` inopérante sur le softDeleteById ci-dessous) — cascade toujours
                // elle-même sur `loan_payments` via `loanId`, relation intacte.
                loanDao.deleteById(loan.id, userId)
            }
            personDao.softDeleteById(id, userId, now)
        }
        enqueuePersonSync(
            existing.copy(syncId = existing.syncId ?: UUID.randomUUID().toString(), deletedAt = now, updatedAt = now),
            operation = SyncOperation.DELETE
        )
    }

    private suspend fun enqueuePersonSync(entity: PersonEntity, operation: SyncOperation) {
        val payload = PersonSyncPayload(
            id = requireNotNull(entity.syncId) { "syncId doit être généré avant l'enfilage." },
            baseVersion = if (operation == SyncOperation.CREATE) null else entity.version,
            name = entity.name,
            phone = entity.phone,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
        syncQueueEnqueuer.enqueue(
            entityType = "persons",
            entitySyncId = payload.id,
            operation = operation,
            payloadJson = json.encodeToString(PersonSyncPayload.serializer(), payload)
        )
    }

    private suspend fun requireCurrentUserId(): Long =
        sessionManager.getCurrentUserIdOnce() ?: error("Aucun utilisateur connecté.")
}
