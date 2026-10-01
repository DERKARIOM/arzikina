package com.naniger.arzikina

import com.naniger.arzikina.data.repository.SyncPullCursor
import com.naniger.arzikina.domain.model.Account
import com.naniger.arzikina.domain.model.AccountIcon
import com.naniger.arzikina.domain.model.Budget
import com.naniger.arzikina.domain.model.BudgetPeriod
import com.naniger.arzikina.domain.model.CurrencyAmount
import com.naniger.arzikina.domain.model.FinancialPlanItem
import com.naniger.arzikina.domain.model.PlanItemStatus
import com.naniger.arzikina.domain.model.RecurringFrequency
import com.naniger.arzikina.domain.model.Transaction
import com.naniger.arzikina.domain.model.TransactionType
import com.naniger.arzikina.domain.model.computeLoanStatus
import com.naniger.arzikina.domain.model.computeNextExecutionDate
import com.naniger.arzikina.domain.model.generateMissingScheduledDates
import com.naniger.arzikina.presentation.accounts.computeCurrentBalances
import com.naniger.arzikina.presentation.transactions.computeRunningBalances
import com.naniger.arzikina.util.AuthValidator
import com.naniger.arzikina.util.BudgetPace
import com.naniger.arzikina.util.BudgetPeriodStatus
import com.naniger.arzikina.util.BudgetProgress
import com.naniger.arzikina.util.FinancialPlanProgress
import com.naniger.arzikina.util.Money
import com.naniger.arzikina.util.SavingsGoalProgress
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * Jeux de tests PARTAGÉS avec l'app iOS (`shared/test-fixtures/` à la racine du dépôt) : les mêmes
 * fichiers JSON sont exécutés ici contre le code Android et, côté iOS, par
 * `ios/Packages/ArzikinaKit/Tests/ArzikinaDomainTests/SharedFixtureTests.swift`. Si une règle
 * métier (montants, soldes, objectifs d'épargne, statut des prêts, budgets, récurrences,
 * planifications, validation de l'authentification) diverge entre les deux plateformes, au moins un des deux échoue.
 *
 * Les règles Android lisent le fuseau via `ZoneId.systemDefault()` : chaque jeu fixe donc le fuseau
 * par défaut de la JVM sur celui du fichier (restauré après chaque test), pour que le résultat ne
 * dépende jamais de la machine qui exécute les tests.
 *
 * Placé à la racine du package de test : l'outil de synchronisation de fichiers utilisé pendant le
 * développement ne descend pas au-delà de 7 niveaux de dossiers.
 */
class SharedFixturesTest {

    private val originalTimeZone: TimeZone = TimeZone.getDefault()

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone)
    }

    // --- Synchronisation ---------------------------------------------------------------------

    @Test
    fun pullCursor() {
        val fixture = fixture("pull-cursor.json")
        assertEquals(fixture.long("serverBatchLimit"), SyncPullCursor.SERVER_BATCH_LIMIT.toLong())
        fixture.objects("cases").forEach {
            val next = SyncPullCursor.next(
                current = it.long("current"),
                updatedAts = it.getValue("updatedAts").jsonArray.map { value -> value.jsonPrimitive.long },
                serverTime = it.long("serverTime"),
                isFull = it.getValue("full").jsonPrimitive.boolean
            )
            assertEquals(it.string("name"), it.long("expected"), next)
        }
    }

    // --- Montants ---------------------------------------------------------------------------

    @Test
    fun money() {
        val fixture = fixture("money.json")
        fixture.objects("parse").forEach {
            val input = it.string("input")
            assertEquals("parse « $input »", it.longOrNull("expected"), Money.parseToMinorUnits(input))
        }
        fixture.objects("formatForInput").forEach {
            assertEquals(it.string("expected"), Money.formatForInput(it.long("minor")))
        }
        fixture.objects("formatAmount").forEach {
            assertEquals(it.string("expected"), Money.formatAmount(it.long("minor")))
        }
        fixture.objects("formatWithCurrency").forEach {
            assertEquals(it.string("expected"), Money.format(CurrencyAmount(it.string("currency"), it.long("minor"))))
        }
    }

    // --- Objectif d'épargne -----------------------------------------------------------------

    @Test
    fun savingsGoal() {
        fixture("savings-goal.json").objects("cases").forEach {
            val name = it.string("name")
            val snapshot = SavingsGoalProgress.of(it.long("balance"), it.longOrNull("target"))
            val expected = it.objectOrNull("expected")
            if (expected == null) {
                assertNull(name, snapshot)
                return@forEach
            }
            assertNotNull(name, snapshot)
            assertEquals(name, expected.long("saved"), snapshot!!.saved)
            assertEquals(name, expected.long("remaining"), snapshot.remaining)
            assertEquals(name, expected.long("exceededBy"), snapshot.exceededBy)
            assertEquals(name, expected.int("percent"), snapshot.percent)
            assertEquals(name, expected.boolean("isReached"), snapshot.isReached)
        }
    }

    // --- Prêts --------------------------------------------------------------------------------

    @Test
    fun loanStatus() {
        val fixture = fixture("loan-status.json")
        val zone = useTimeZone(fixture.string("timeZone"))
        fixture.objects("cases").forEach {
            val status = computeLoanStatus(
                amount = it.long("amount"),
                amountRepaid = it.long("amountRepaid"),
                startDate = millis(it.string("startDate"), zone),
                dueDate = millis(it.string("dueDate"), zone),
                nowEpochMillis = millis(it.string("now"), zone)
            )
            assertEquals(it.string("name"), it.string("expected"), status.name)
        }
    }

    // --- Soldes -------------------------------------------------------------------------------

    @Test
    fun accountBalances() {
        val fixture = fixture("account-balances.json")
        val ids = IdRegistry()
        val accounts = fixture.objects("accounts").map {
            account(ids.of(it.string("id")), currencyCode = "XOF", initialBalance = it.long("initialBalance"))
        }
        val transactions = fixture.objects("transactions").map {
            transaction(
                type = TransactionType.valueOf(it.string("type")),
                amount = it.long("amount"),
                accountId = ids.of(it.string("accountId")),
                transferAccountId = it.stringOrNull("transferAccountId")?.let(ids::of)
            )
        }
        val balances = computeCurrentBalances(accounts, transactions)
        val expected = fixture.getValue("expected").jsonObject
        assertEquals("Un solde par compte connu, jamais pour un compte inconnu", expected.size, balances.size)
        expected.forEach { (id, value) -> assertEquals(id, value.jsonPrimitive.long, balances[ids.of(id)]) }
    }

    // --- Budgets ------------------------------------------------------------------------------

    @Test
    fun runningBalance() {
        val fixture = fixture("running-balance.json")
        val accountIds = IdRegistry()
        val transactionIds = IdRegistry()
        val accountJson = fixture.getValue("account").jsonObject
        val account = account(accountIds.of(accountJson.string("id")), currencyCode = "XOF", initialBalance = accountJson.long("initialBalance"))
        // Liste de la plus récente à la plus ancienne, comme TransactionDao.observeTransactions.
        val transactions = fixture.objects("transactions").map {
            transaction(
                type = TransactionType.valueOf(it.string("type")),
                amount = it.long("amount"),
                accountId = accountIds.of(it.string("accountId")),
                transferAccountId = it.stringOrNull("transferAccountId")?.let(accountIds::of)
            ).copy(id = transactionIds.of(it.string("id")))
        }
        val balances = computeRunningBalances(transactions, listOf(account))
        val expected = fixture.getValue("expected").jsonObject
        assertEquals("Aucun solde pour une transaction d'un autre compte", expected.size, balances.size)
        expected.forEach { (id, value) ->
            assertEquals(id, value.jsonPrimitive.long, balances[transactionIds.of(id) to account.id])
        }
    }

    @Test
    fun budgets() {
        val fixture = fixture("budget.json")
        val zone = useTimeZone(fixture.string("timeZone"))
        fixture.objects("cases").forEach { case ->
            val name = case.string("name")
            val ids = IdRegistry()
            val today = LocalDate.parse(case.string("today"))
            val budgetJson = case.getValue("budget").jsonObject
            val budget = Budget(
                categoryId = ids.of(budgetJson.string("categoryId")),
                period = BudgetPeriod.valueOf(budgetJson.string("period")),
                limitAmount = budgetJson.long("limitAmount"),
                currencyCode = budgetJson.string("currencyCode"),
                createdAt = 0L,
                startDate = budgetJson.stringOrNull("startDate")?.let { millis(it, zone) },
                endDate = budgetJson.stringOrNull("endDate")?.let { millis(it, zone) }
            )
            val accountsById = case.objects("accounts")
                .map { account(ids.of(it.string("id")), currencyCode = it.string("currencyCode")) }
                .associateBy { it.id }
            val transactions = case.objects("transactions").map {
                transaction(
                    type = TransactionType.valueOf(it.string("type")),
                    amount = it.long("amount"),
                    accountId = ids.of(it.string("accountId")),
                    categoryId = ids.of(it.string("categoryId")),
                    date = millis(it.string("date"), zone)
                )
            }

            val expected = case.getValue("expected").jsonObject
            val progress = BudgetProgress.compute(budget, transactions, accountsById, today)
            assertEquals(name, expected.long("spent"), progress.spentMinor)
            assertEquals(name, expected.double("progress"), progress.progress.toDouble(), 1e-4)
            assertEquals(name, expected.stringOrNull("periodStatus"), BudgetPeriodStatus.of(budget.startDate, budget.endDate, today)?.name)

            val pace = BudgetPace.of(budget, progress.spentMinor, today)
            val expectedPace = expected.getValue("pace").jsonObject
            assertEquals(name, expectedPace.string("periodStatus"), pace.periodStatus.name)
            assertEquals(name, LocalDate.parse(expectedPace.string("periodStart")), pace.periodStart)
            assertEquals(name, LocalDate.parse(expectedPace.string("periodEnd")), pace.periodEnd)
            assertEquals(name, expectedPace.long("totalDays"), pace.totalDays)
            assertEquals(name, expectedPace.long("elapsedDays"), pace.elapsedDays)
            assertEquals(name, expectedPace.long("daysRemaining"), pace.daysRemaining)
            assertEquals(name, expectedPace.string("paceState"), pace.paceState.name)
        }
    }

    // --- Automatisations ----------------------------------------------------------------------

    @Test
    fun recurrenceNextDate() {
        val fixture = fixture("recurrence.json")
        val defaultZone = fixture.string("timeZone")
        fixture.objects("next").forEach {
            val zone = useTimeZone(it.stringOrNull("timeZone") ?: defaultZone)
            val date = it.string("date")
            val frequency = RecurringFrequency.valueOf(it.string("frequency"))
            assertEquals(
                "$frequency après $date",
                it.stringOrNull("expected")?.let { expected -> millis(expected, zone) },
                computeNextExecutionDate(millis(date, zone), frequency)
            )
        }
    }

    @Test
    fun recurrenceMissingDates() {
        val fixture = fixture("recurrence.json")
        val zone = useTimeZone(fixture.string("timeZone"))
        fixture.objects("missing").forEach {
            val dates = generateMissingScheduledDates(
                nextExecutionDate = millis(it.string("nextExecutionDate"), zone),
                frequency = RecurringFrequency.valueOf(it.string("frequency")),
                endDate = it.stringOrNull("endDate")?.let { end -> millis(end, zone) },
                nowEpochMillis = millis(it.string("now"), zone),
                triggerHour = it.int("triggerHour"),
                triggerMinute = it.int("triggerMinute")
            )
            val expected = it.getValue("expected").jsonArray.map { date -> millis(date.jsonPrimitive.content, zone) }
            assertEquals(it.string("name"), expected, dates)
        }
    }

    // --- Authentification -------------------------------------------------------------------

    @Test
    fun authValidation() {
        val fixture = fixture("auth-validation.json")
        val predicates: List<Pair<String, (String) -> Boolean>> = listOf(
            "email" to AuthValidator::isValidEmail,
            "username" to AuthValidator::isValidUsername,
            "passwordLongEnough" to AuthValidator::isPasswordLongEnough,
            "securityAnswerLongEnough" to AuthValidator::isSecurityAnswerLongEnough
        )
        predicates.forEach { (group, predicate) ->
            fixture.objects(group).forEach {
                val input = it.string("input")
                assertEquals("$group « $input »", it.boolean("expected"), predicate(input))
            }
        }
        fixture.objects("normalizeSecurityAnswer").forEach {
            assertEquals(it.string("expected"), AuthValidator.normalizeSecurityAnswer(it.string("input")))
        }
    }

    // --- Planification ------------------------------------------------------------------------

    @Test
    fun financialPlan() {
        fixture("financial-plan.json").objects("cases").forEach {
            val name = it.string("name")
            val available = it.long("available")
            val items = it.objects("items").map { item ->
                FinancialPlanItem(
                    planId = 1L,
                    name = "item",
                    amount = item.long("amount"),
                    status = PlanItemStatus.valueOf(item.string("status")),
                    createdAt = 0L,
                    updatedAt = 0L
                )
            }
            val expected = it.getValue("expected").jsonObject
            val total = FinancialPlanProgress.calculateTotalPlanned(items)
            assertEquals(name, expected.long("totalPlanned"), total)
            assertEquals(name, expected.long("remaining"), FinancialPlanProgress.calculateRemainingAmount(available, total))
            assertEquals(name, expected.int("progress"), FinancialPlanProgress.calculateProgress(available, total))
            assertEquals(name, expected.boolean("isOverBudget"), FinancialPlanProgress.calculateOverBudget(available, total))
        }
    }

    // --- Outils -------------------------------------------------------------------------------

    /** Identifiants textuels des fixtures (UUID côté iOS/API) → identifiants Long locaux d'Android. */
    private class IdRegistry {
        private val ids = mutableMapOf<String, Long>()
        fun of(key: String): Long = ids.getOrPut(key) { ids.size + 1L }
    }

    private fun account(id: Long, currencyCode: String, initialBalance: Long = 0L) = Account(
        id = id,
        name = "compte $id",
        icon = AccountIcon.WALLET,
        colorArgb = 0L,
        currencyCode = currencyCode,
        initialBalance = initialBalance,
        createdAt = 0L
    )

    private fun transaction(
        type: TransactionType,
        amount: Long,
        accountId: Long,
        transferAccountId: Long? = null,
        categoryId: Long? = null,
        date: Long = 0L
    ) = Transaction(
        amount = amount,
        type = type,
        accountId = accountId,
        transferAccountId = transferAccountId,
        categoryId = categoryId,
        date = date,
        createdAt = 0L
    )

    private fun useTimeZone(id: String): ZoneId {
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        return ZoneId.of(id)
    }

    /** « 2026-09-30T08:00 » ou « 2026-09-30 » (début de journée) → instant dans [zone]. */
    private fun millis(text: String, zone: ZoneId): Long {
        val dateTime = if ('T' in text) LocalDateTime.parse(text) else LocalDate.parse(text).atStartOfDay()
        return dateTime.atZone(zone).toInstant().toEpochMilli()
    }

    private fun fixture(name: String): JsonObject =
        Json.parseToJsonElement(File(fixturesDirectory(), name).readText()).jsonObject

    /** `shared/test-fixtures/`, cherché en remontant depuis le dossier d'exécution des tests
     *  (`app/` sous Gradle, racine du dépôt sous certains IDE). */
    private fun fixturesDirectory(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "shared/test-fixtures")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("Dossier shared/test-fixtures introuvable depuis ${System.getProperty("user.dir")}")
    }

    private fun JsonObject.objects(key: String): List<JsonObject> = getValue(key).jsonArray.map { it.jsonObject }
    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content
    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
    private fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.long
    private fun JsonObject.longOrNull(key: String): Long? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.long
    private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int
    private fun JsonObject.double(key: String): Double = getValue(key).jsonPrimitive.double
    private fun JsonObject.boolean(key: String): Boolean = getValue(key).jsonPrimitive.boolean
    private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject
}
