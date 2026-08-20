package com.arzikina.ne.data.backup

import com.arzikina.ne.domain.model.SecurityQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [BackupMappers]'s `remapIds` functions sont volontairement des fonctions PURES (aucune
 * dépendance à Room) : voir leur doc de tête, "l'import n'insère JAMAIS les `id` du fichier tels
 * quels". Ces tests verrouillent le comportement dont dépend TOUTE la correction de
 * `BackupRepositoryImpl.importBackup` — une régression ici serait le bug le plus grave possible
 * pour cette fonctionnalité (corruption silencieuse de données à la restauration, voir la doc de
 * tête de `BackupRepositoryImpl`).
 *
 * Convention commune à tous les tests ci-dessous :
 * - une clé étrangère OBLIGATOIRE (ex. `Transaction.accountId`) doit lever une exception si son id
 *   d'origine est absent de la table de correspondance (fichier corrompu) — jamais insérer une
 *   ligne avec une référence invalide ni la faire disparaître silencieusement (voir
 *   `BackupRepositoryImpl`, qui laisse cette exception annuler tout l'import dans sa transaction
 *   Room atomique) ;
 * - une clé étrangère OPTIONNELLE (nullable) retombe sur `null` dans le même cas, sans jamais
 *   lever d'exception.
 */
class BackupIdRemappingTest {

    private fun transactionDto(
        id: Long = 1L,
        accountId: Long = 10L,
        transferAccountId: Long? = null,
        categoryId: Long? = 20L,
        feeTransactionId: Long? = null,
        receiptId: Long? = null
    ) = TransactionDto(
        id = id,
        amount = 1_000L,
        type = "EXPENSE",
        accountId = accountId,
        transferAccountId = transferAccountId,
        categoryId = categoryId,
        date = 0L,
        description = "",
        createdAt = 0L,
        feeTransactionId = feeTransactionId,
        receiptId = receiptId
    )

    @Test
    fun `transaction - accountId et categoryId reecrits avec les nouveaux ids`() {
        val dto = transactionDto(accountId = 10L, categoryId = 20L)
        val accountIdMap = mapOf(10L to 110L)
        val categoryIdMap = mapOf(20L to 220L)

        val remapped = dto.remapIds(newId = 100L, accountIdMap = accountIdMap, categoryIdMap = categoryIdMap)

        assertEquals(100L, remapped.id)
        assertEquals(110L, remapped.accountId)
        assertEquals(220L, remapped.categoryId)
    }

    @Test
    fun `transaction - transferAccountId reecrit pour un transfert`() {
        val dto = transactionDto(accountId = 10L, transferAccountId = 11L, categoryId = null)
        val accountIdMap = mapOf(10L to 110L, 11L to 111L)

        val remapped = dto.remapIds(newId = 100L, accountIdMap = accountIdMap, categoryIdMap = emptyMap())

        assertEquals(111L, remapped.transferAccountId)
    }

    @Test
    fun `transaction - transferAccountId absent de la map retombe sur null (FK optionnelle)`() {
        val dto = transactionDto(accountId = 10L, transferAccountId = 999L, categoryId = null)
        val accountIdMap = mapOf(10L to 110L)

        val remapped = dto.remapIds(newId = 100L, accountIdMap = accountIdMap, categoryIdMap = emptyMap())

        assertNull(remapped.transferAccountId)
    }

    @Test
    fun `transaction - accountId manquant de la map leve une exception (FK obligatoire)`() {
        val dto = transactionDto(accountId = 999L)

        assertThrows(NoSuchElementException::class.java) {
            dto.remapIds(newId = 100L, accountIdMap = emptyMap(), categoryIdMap = emptyMap())
        }
    }

    @Test
    fun `transaction - categoryId absent de la map retombe sur null (FK optionnelle)`() {
        val dto = transactionDto(accountId = 10L, categoryId = 999L)
        val accountIdMap = mapOf(10L to 110L)

        val remapped = dto.remapIds(newId = 100L, accountIdMap = accountIdMap, categoryIdMap = emptyMap())

        assertNull(remapped.categoryId)
    }

    @Test
    fun `transaction - feeTransactionId omis (1ere passe) reste null meme si le dto en avait un`() {
        val dto = transactionDto(accountId = 10L, feeTransactionId = 2L)
        val accountIdMap = mapOf(10L to 110L)

        // Signature à 3 arguments (feeTransactionIdMap omis, défaut vide) : voir la doc de
        // TransactionDto.remapIds, "1ère passe" dans BackupRepositoryImpl.
        val remapped = dto.remapIds(newId = 100L, accountIdMap = accountIdMap, categoryIdMap = emptyMap())

        assertNull(remapped.feeTransactionId)
    }

    @Test
    fun `transaction - feeTransactionId reecrit en 2eme passe une fois la map complete connue`() {
        val dto = transactionDto(accountId = 10L, feeTransactionId = 2L)
        val accountIdMap = mapOf(10L to 110L)
        val transactionIdMap = mapOf(1L to 100L, 2L to 200L)

        val remapped = dto.remapIds(newId = 100L, accountIdMap = accountIdMap, categoryIdMap = emptyMap(), feeTransactionIdMap = transactionIdMap)

        assertEquals(200L, remapped.feeTransactionId)
    }

    @Test
    fun `transaction - receiptId reecrit avec le nouvel id quand receiptIdMap est fourni`() {
        val dto = transactionDto(accountId = 10L, receiptId = 5L)
        val accountIdMap = mapOf(10L to 110L)
        val receiptIdMap = mapOf(5L to 205L)

        val remapped = dto.remapIds(
            newId = 100L,
            accountIdMap = accountIdMap,
            categoryIdMap = emptyMap(),
            receiptIdMap = receiptIdMap
        )

        assertEquals(205L, remapped.receiptId)
    }

    @Test
    fun `transaction - receiptId absent de la map retombe sur null (FK optionnelle)`() {
        val dto = transactionDto(accountId = 10L, receiptId = 999L)
        val accountIdMap = mapOf(10L to 110L)

        val remapped = dto.remapIds(
            newId = 100L,
            accountIdMap = accountIdMap,
            categoryIdMap = emptyMap(),
            receiptIdMap = mapOf(5L to 205L)
        )

        assertNull(remapped.receiptId)
    }

    @Test
    fun `transaction - receiptId omis retombe sur null meme si le dto en avait un (regression 2eme passe)`() {
        // Verrouille exactement le piège documenté sur TransactionDto.remapIds : oublier de
        // repasser receiptIdMap (ex. à la 2ème passe qui réécrit feeTransactionId dans
        // BackupRepositoryImpl) effacerait silencieusement un receiptId déjà correctement résolu.
        val dto = transactionDto(accountId = 10L, receiptId = 5L, feeTransactionId = 2L)
        val accountIdMap = mapOf(10L to 110L)
        val transactionIdMap = mapOf(1L to 100L, 2L to 200L)

        val remapped = dto.remapIds(
            newId = 100L,
            accountIdMap = accountIdMap,
            categoryIdMap = emptyMap(),
            feeTransactionIdMap = transactionIdMap
            // receiptIdMap volontairement omis ici.
        )

        assertEquals(200L, remapped.feeTransactionId)
        assertNull(remapped.receiptId)
    }

    @Test
    fun `budget - categoryId obligatoire reecrit, exception si absent`() {
        val dto = BudgetDto(id = 1L, categoryId = 20L, period = "MONTHLY", limitAmount = 1_000L, currencyCode = "XOF", createdAt = 0L)

        val remapped = dto.remapIds(newId = 100L, categoryIdMap = mapOf(20L to 220L))
        assertEquals(220L, remapped.categoryId)

        assertThrows(NoSuchElementException::class.java) {
            dto.remapIds(newId = 100L, categoryIdMap = emptyMap())
        }
    }

    private fun loanDto(personId: Long = 30L, accountId: Long = 10L, transactionId: Long = 1L) = LoanDto(
        id = 1L,
        personId = personId,
        accountId = accountId,
        type = "LENT",
        amount = 1_000L,
        amountRepaid = 0L,
        remainingAmount = 1_000L,
        startDate = 0L,
        dueDate = 0L,
        reason = "OTHER",
        repaymentMode = "SINGLE",
        description = "",
        status = "ONGOING",
        createdAt = 0L,
        updatedAt = 0L,
        transactionId = transactionId
    )

    @Test
    fun `loan - personId, accountId et transactionId tous reecrits`() {
        val dto = loanDto(personId = 30L, accountId = 10L, transactionId = 1L)
        val remapped = dto.remapIds(
            newId = 300L,
            personIdMap = mapOf(30L to 130L),
            accountIdMap = mapOf(10L to 110L),
            transactionIdMap = mapOf(1L to 100L)
        )

        assertEquals(300L, remapped.id)
        assertEquals(130L, remapped.personId)
        assertEquals(110L, remapped.accountId)
        assertEquals(100L, remapped.transactionId)
    }

    @Test
    fun `loan - transactionId manquant (decaissement absent du fichier) leve une exception`() {
        val dto = loanDto(personId = 30L, accountId = 10L, transactionId = 999L)

        assertThrows(NoSuchElementException::class.java) {
            dto.remapIds(
                newId = 300L,
                personIdMap = mapOf(30L to 130L),
                accountIdMap = mapOf(10L to 110L),
                transactionIdMap = emptyMap()
            )
        }
    }

    @Test
    fun `loan - personId manquant (personne absente du fichier) leve une exception`() {
        val dto = loanDto(personId = 999L, accountId = 10L, transactionId = 1L)

        assertThrows(NoSuchElementException::class.java) {
            dto.remapIds(
                newId = 300L,
                personIdMap = emptyMap(),
                accountIdMap = mapOf(10L to 110L),
                transactionIdMap = mapOf(1L to 100L)
            )
        }
    }

    @Test
    fun `loanPayment - loanId, accountId et transactionId tous reecrits`() {
        val dto = LoanPaymentDto(id = 1L, loanId = 40L, accountId = 10L, amount = 500L, date = 0L, note = "", transactionId = 2L, createdAt = 0L)

        val remapped = dto.remapIds(
            newId = 400L,
            loanIdMap = mapOf(40L to 340L),
            accountIdMap = mapOf(10L to 110L),
            transactionIdMap = mapOf(2L to 200L)
        )

        assertEquals(400L, remapped.id)
        assertEquals(340L, remapped.loanId)
        assertEquals(110L, remapped.accountId)
        assertEquals(200L, remapped.transactionId)
    }

    @Test
    fun `loanPayment - loanId manquant (pret absent du fichier) leve une exception`() {
        val dto = LoanPaymentDto(id = 1L, loanId = 999L, accountId = 10L, amount = 500L, date = 0L, note = "", transactionId = 2L, createdAt = 0L)

        assertThrows(NoSuchElementException::class.java) {
            dto.remapIds(
                newId = 400L,
                loanIdMap = emptyMap(),
                accountIdMap = mapOf(10L to 110L),
                transactionIdMap = mapOf(2L to 200L)
            )
        }
    }

    private fun recurringTransactionDto(accountId: Long = 10L, categoryId: Long? = 20L) = RecurringTransactionDto(
        id = 1L,
        type = "EXPENSE",
        amount = 1_000L,
        accountId = accountId,
        categoryId = categoryId,
        description = "",
        startDate = 0L,
        frequency = "MONTHLY",
        nextExecutionDate = 0L,
        isActive = true,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun `recurringTransaction - accountId obligatoire, categoryId optionnel`() {
        val dto = recurringTransactionDto(accountId = 10L, categoryId = 20L)
        val remapped = dto.remapIds(newId = 500L, accountIdMap = mapOf(10L to 110L), categoryIdMap = mapOf(20L to 220L))

        assertEquals(500L, remapped.id)
        assertEquals(110L, remapped.accountId)
        assertEquals(220L, remapped.categoryId)
    }

    @Test
    fun `recurringTransaction - categoryId absent de la map retombe sur null (FK optionnelle)`() {
        val dto = recurringTransactionDto(accountId = 10L, categoryId = 999L)

        val remapped = dto.remapIds(newId = 500L, accountIdMap = mapOf(10L to 110L), categoryIdMap = emptyMap())

        assertNull(remapped.categoryId)
    }

    @Test
    fun `recurringTransaction - accountId manquant leve une exception`() {
        val dto = recurringTransactionDto(accountId = 999L)

        assertThrows(NoSuchElementException::class.java) {
            dto.remapIds(newId = 500L, accountIdMap = emptyMap(), categoryIdMap = emptyMap())
        }
    }

    @Test
    fun `occurrence - recurringTransactionId obligatoire, transactionId optionnel (PENDING)`() {
        val dto = RecurringTransactionOccurrenceDto(
            id = 1L,
            recurringTransactionId = 50L,
            scheduledDate = 0L,
            status = "PENDING",
            transactionId = null,
            createdAt = 0L
        )

        val remapped = dto.remapIds(
            newId = 600L,
            recurringTransactionIdMap = mapOf(50L to 150L),
            transactionIdMap = emptyMap()
        )

        assertEquals(600L, remapped.id)
        assertEquals(150L, remapped.recurringTransactionId)
        assertNull(remapped.transactionId)
    }

    @Test
    fun `occurrence - transactionId reecrit quand l'occurrence a ete acceptee`() {
        val dto = RecurringTransactionOccurrenceDto(
            id = 1L,
            recurringTransactionId = 50L,
            scheduledDate = 0L,
            status = "ACCEPTED",
            transactionId = 5L,
            createdAt = 0L
        )

        val remapped = dto.remapIds(
            newId = 600L,
            recurringTransactionIdMap = mapOf(50L to 150L),
            transactionIdMap = mapOf(5L to 205L)
        )

        assertEquals(205L, remapped.transactionId)
    }

    @Test
    fun `occurrence - transactionId present mais absent de transactionIdMap retombe sur null`() {
        val dto = RecurringTransactionOccurrenceDto(
            id = 1L,
            recurringTransactionId = 50L,
            scheduledDate = 0L,
            status = "ACCEPTED",
            transactionId = 999L,
            createdAt = 0L
        )

        val remapped = dto.remapIds(
            newId = 600L,
            recurringTransactionIdMap = mapOf(50L to 150L),
            transactionIdMap = emptyMap()
        )

        assertNull(remapped.transactionId)
    }

    @Test
    fun `occurrence - recurringTransactionId manquant leve une exception`() {
        val dto = RecurringTransactionOccurrenceDto(
            id = 1L,
            recurringTransactionId = 999L,
            scheduledDate = 0L,
            status = "PENDING",
            createdAt = 0L
        )

        assertThrows(NoSuchElementException::class.java) {
            dto.remapIds(newId = 600L, recurringTransactionIdMap = emptyMap(), transactionIdMap = emptyMap())
        }
    }

    @Test
    fun `securityQuestionOrDefault - valeur connue parsee correctement`() {
        val dto = userDto(securityQuestion = SecurityQuestion.BIRTH_CITY.name)
        assertEquals(SecurityQuestion.BIRTH_CITY, dto.securityQuestionOrDefault())
    }

    @Test
    fun `securityQuestionOrDefault - valeur corrompue retombe sur la premiere question`() {
        val dto = userDto(securityQuestion = "VALEUR_INCONNUE_CORROMPUE")
        assertEquals(SecurityQuestion.entries.first(), dto.securityQuestionOrDefault())
    }

    private fun userDto(securityQuestion: String) = UserDto(
        fullName = "Test",
        username = "test",
        email = "test@example.com",
        passwordHash = "pbkdf2\$15000\$salt\$hash",
        securityQuestion = securityQuestion,
        securityAnswerHash = "pbkdf2\$15000\$salt\$hash",
        createdAt = 0L
    )
}
