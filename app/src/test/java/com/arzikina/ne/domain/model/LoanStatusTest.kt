package com.arzikina.ne.domain.model

import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [computeLoanStatus] est une fonction pure (voir sa doc) : idéale pour des tests unitaires
 * exhaustifs sans aucun mock. Couvre en particulier les deux bugs corrigés à l'Étape "Tests et
 * correction des bugs" :
 * - le statut n'était recalculé qu'à l'écriture, jamais à la lecture (voir les tests ci-dessous,
 *   qui vérifient directement la fonction utilisée pour ce recalcul) ;
 * - la frontière OVERDUE se déclenchait le jour même de l'échéance au lieu du lendemain (voir
 *   [computeLoanStatus overdueSeulementLendemainEcheance] et les tests de frontière associés).
 */
class LoanStatusTest {

    private val zone = ZoneId.systemDefault()

    /** Minuit local d'un jour donné, même convention que `LoanFormFragment.showDatePicker` pour
     * [Loan.startDate]/[Loan.dueDate]. */
    private fun localMidnight(daysFromEpoch: Long): Long =
        ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone)
            .plusDays(daysFromEpoch)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `remboursement total reste REPAID meme apres l'echeance`() {
        val start = localMidnight(0)
        val due = localMidnight(10)
        val now = due + TimeUnit.DAYS.toMillis(30) // très en retard, mais soldé

        val status = computeLoanStatus(
            amount = 10_000L,
            amountRepaid = 10_000L,
            startDate = start,
            dueDate = due,
            nowEpochMillis = now
        )

        assertEquals(LoanStatus.REPAID, status)
    }

    @Test
    fun `remboursement superieur au montant est aussi REPAID (jamais de trop-percu affiche)`() {
        val start = localMidnight(0)
        val due = localMidnight(10)

        val status = computeLoanStatus(
            amount = 10_000L,
            amountRepaid = 10_500L,
            startDate = start,
            dueDate = due,
            nowEpochMillis = due
        )

        assertEquals(LoanStatus.REPAID, status)
    }

    @Test
    fun `pret pas encore commence est UPCOMING`() {
        val start = localMidnight(5)
        val due = localMidnight(15)
        val now = localMidnight(2) // avant le début

        val status = computeLoanStatus(
            amount = 10_000L,
            amountRepaid = 0L,
            startDate = start,
            dueDate = due,
            nowEpochMillis = now
        )

        assertEquals(LoanStatus.UPCOMING, status)
    }

    @Test
    fun `le jour meme du debut n'est plus UPCOMING (le jour est deja considere commence)`() {
        val start = localMidnight(5)
        val due = localMidnight(15)
        val now = start // exactement minuit le jour de début

        val status = computeLoanStatus(
            amount = 10_000L,
            amountRepaid = 0L,
            startDate = start,
            dueDate = due,
            nowEpochMillis = now
        )

        assertEquals(LoanStatus.ONGOING, status)
    }

    @Test
    fun `pret en cours entre le debut et l'echeance est ONGOING`() {
        val start = localMidnight(0)
        val due = localMidnight(30)
        val now = localMidnight(15)

        val status = computeLoanStatus(
            amount = 10_000L,
            amountRepaid = 2_000L,
            startDate = start,
            dueDate = due,
            nowEpochMillis = now
        )

        assertEquals(LoanStatus.ONGOING, status)
    }

    @Test
    fun `le jour meme de l'echeance reste ONGOING (pas encore en retard)`() {
        // Régression du bug corrigé : un prêt échéant "aujourd'hui" ne doit PAS s'afficher en
        // retard dès le début de cette même journée.
        val start = localMidnight(0)
        val due = localMidnight(10)
        val now = due + TimeUnit.HOURS.toMillis(23) // 23h le jour même de l'échéance

        val status = computeLoanStatus(
            amount = 10_000L,
            amountRepaid = 0L,
            startDate = start,
            dueDate = due,
            nowEpochMillis = now
        )

        assertEquals(LoanStatus.ONGOING, status)
    }

    @Test
    fun `overdueSeulementLendemainEcheance - le lendemain de l'echeance devient OVERDUE`() {
        val start = localMidnight(0)
        val due = localMidnight(10)
        val now = localMidnight(11) // minuit le lendemain

        val status = computeLoanStatus(
            amount = 10_000L,
            amountRepaid = 0L,
            startDate = start,
            dueDate = due,
            nowEpochMillis = now
        )

        assertEquals(LoanStatus.OVERDUE, status)
    }

    @Test
    fun `tres en retard reste OVERDUE tant que non solde`() {
        val start = localMidnight(0)
        val due = localMidnight(10)
        val now = due + TimeUnit.DAYS.toMillis(90)

        val status = computeLoanStatus(
            amount = 10_000L,
            amountRepaid = 4_000L,
            startDate = start,
            dueDate = due,
            nowEpochMillis = now
        )

        assertEquals(LoanStatus.OVERDUE, status)
    }
}
