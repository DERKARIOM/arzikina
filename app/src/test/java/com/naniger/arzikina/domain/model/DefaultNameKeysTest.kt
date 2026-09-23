package com.naniger.arzikina.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Clés des noms créés par Arzikina (chantier i18n, étape 4) : voir [SystemCategoryKey] et
 * [DefaultAccountKey]. Kotlin pur, sans Android.
 */
class DefaultNameKeysTest {

    private fun category(name: String, type: TransactionType) =
        Category(name = name, icon = CategoryIcon.OTHER, colorArgb = 0L, type = type, createdAt = 0L)

    private val englishLabels = mapOf(
        SystemCategoryKey.SALARY to "Salary",
        SystemCategoryKey.OTHER_INCOME to "Other",
        SystemCategoryKey.OTHER_EXPENSE to "Other",
        SystemCategoryKey.LOAN_DISBURSEMENT_LENT to "Loan given"
    )
    private fun labels(key: SystemCategoryKey) = listOfNotNull(key.canonicalName, englishLabels[key])

    @Test
    fun `une categorie par defaut a une cle, meme nom et meme type`() {
        assertEquals(SystemCategoryKey.SALARY, category("Salaire", TransactionType.INCOME).systemKey)
        assertEquals(SystemCategoryKey.FEES, category(FeeCategoryNames.FEES, TransactionType.EXPENSE).systemKey)
    }

    @Test
    fun `Divers est distingue par son type`() {
        assertEquals(SystemCategoryKey.OTHER_INCOME, category("Divers", TransactionType.INCOME).systemKey)
        assertEquals(SystemCategoryKey.OTHER_EXPENSE, category("Divers", TransactionType.EXPENSE).systemKey)
    }

    @Test
    fun `une categorie renommee ou personnelle n a pas de cle et ne sera jamais traduite`() {
        assertNull(category("Salaire de juin", TransactionType.INCOME).systemKey)
        assertNull(category("Dépenses maman", TransactionType.EXPENSE).systemKey)
        assertNull(category("Salaire", TransactionType.EXPENSE).systemKey) // bon nom, mauvais type
    }

    @Test
    fun `les noms canoniques ne changent pas, ils identifient les donnees existantes`() {
        assertEquals("Salaire", SystemCategoryKey.SALARY.canonicalName)
        assertEquals("Prêt accordé", SystemCategoryKey.LOAN_DISBURSEMENT_LENT.canonicalName)
        assertEquals("Espèces", DefaultAccountKey.CASH.canonicalName)
    }

    @Test
    fun `enregistrer le libelle traduit sans le modifier conserve le nom canonique`() {
        assertEquals("Salaire", SystemCategoryKey.canonicalNameFor("Salary", TransactionType.INCOME, ::labels))
        assertEquals("Salaire", SystemCategoryKey.canonicalNameFor("Salaire", TransactionType.INCOME, ::labels))
        assertEquals("Divers", SystemCategoryKey.canonicalNameFor("Other", TransactionType.EXPENSE, ::labels))
    }

    @Test
    fun `une vraie modification du nom est enregistree telle quelle`() {
        assertEquals("Salary June", SystemCategoryKey.canonicalNameFor("Salary June", TransactionType.INCOME, ::labels))
        // Même libellé mais type différent : aucune correspondance, rien n'est réécrit.
        assertEquals("Salary", SystemCategoryKey.canonicalNameFor("Salary", TransactionType.EXPENSE, ::labels))
    }

    @Test
    fun `comptes par defaut, meme regle`() {
        val accountLabels = mapOf(DefaultAccountKey.CASH to "Cash", DefaultAccountKey.SAVINGS to "Savings")
        val labelsOf = { key: DefaultAccountKey -> listOfNotNull(key.canonicalName, accountLabels[key]) }
        assertEquals(DefaultAccountKey.CASH, DefaultAccountKey.of("Espèces"))
        assertNull(DefaultAccountKey.of("Orange Money perso"))
        assertEquals("Espèces", DefaultAccountKey.canonicalNameFor("Cash", labelsOf))
        assertEquals("Mon cash", DefaultAccountKey.canonicalNameFor("Mon cash", labelsOf))
    }
}
