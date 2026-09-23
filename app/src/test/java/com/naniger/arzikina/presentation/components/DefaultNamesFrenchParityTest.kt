package com.naniger.arzikina.presentation.components

import com.naniger.arzikina.R
import com.naniger.arzikina.domain.model.DefaultAccountKey
import com.naniger.arzikina.domain.model.SystemCategoryKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Garde-fou du chantier i18n, étape 4 : en français (`res/values/strings.xml`), le libellé de
 * chaque catégorie/compte par défaut doit être STRICTEMENT identique à son nom canonique en base.
 * Sinon, en français, « Epargne » (sans accent) s'afficherait à la place d'« Épargne », et une
 * modification enregistrée depuis le formulaire ne retrouverait plus le nom canonique.
 *
 * Lit directement le fichier de ressources (répertoire de travail des tests = module `app/`) :
 * aucune dépendance Android/Robolectric nécessaire.
 */
class DefaultNamesFrenchParityTest {

    private val frenchStrings: Map<String, String> by lazy {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        (0 until nodes.length).associate { index ->
            val node = nodes.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent.replace("\\'", "'")
        }
    }

    private fun resourceName(id: Int): String =
        R.string::class.java.fields.first { it.getInt(null) == id }.name

    private fun frenchLabel(id: Int): String {
        val name = resourceName(id)
        assertNotNull("Libellé $name absent de values/strings.xml", frenchStrings[name])
        return frenchStrings.getValue(name)
    }

    @Test
    fun `categories par defaut, libelle francais identique au nom canonique`() {
        SystemCategoryKey.entries.forEach { key ->
            assertEquals(key.name, key.canonicalName, frenchLabel(key.labelRes))
        }
    }

    @Test
    fun `comptes par defaut, libelle francais identique au nom canonique`() {
        DefaultAccountKey.entries.forEach { key ->
            assertEquals(key.name, key.canonicalName, frenchLabel(key.labelRes))
        }
    }
}
