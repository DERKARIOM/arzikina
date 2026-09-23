package com.naniger.arzikina.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Garde-fou du chantier i18n, étape 5 : `res/values-en/strings.xml` doit rester le reflet exact de
 * `res/values/strings.xml` (français, langue de référence).
 *
 * - toute clé traduisible française existe en anglais (sinon Android affiche silencieusement le
 *   français au milieu d'un écran anglais) ;
 * - aucune clé anglaise orpheline ni aucune chaîne `translatable="false"` dupliquée ;
 * - mêmes paramètres (`%1$s`, `%d`, `%%`…) des deux côtés : un paramètre manquant ou en trop
 *   provoque une exception ou un texte faux à l'exécution, que le compilateur ne détecte pas ;
 * - chaque `<plurals>` anglais définit `one` et `other`.
 *
 * Lit directement les fichiers (répertoire de travail des tests = module `app/`), comme
 * `DefaultNamesFrenchParityTest` : aucune dépendance Android/Robolectric.
 */
class EnglishTranslationParityTest {

    private class Resources(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>) {
        val keys: Set<String> get() = strings.keys + plurals.keys
    }

    private val french = load("src/main/res/values/strings.xml")
    private val english = load("src/main/res/values-en/strings.xml")

    @Test
    fun `chaque cle francaise traduisible existe en anglais`() {
        val missing = (french.keys - english.keys).sorted()
        assertTrue("Clés absentes de values-en : $missing", missing.isEmpty())
    }

    @Test
    fun `aucune cle anglaise orpheline ou non traduisible`() {
        val orphans = (english.keys - french.keys).sorted()
        assertTrue("Clés de values-en absentes de values (ou translatable=false) : $orphans", orphans.isEmpty())
    }

    @Test
    fun `les chaines ont les memes parametres dans les deux langues`() {
        french.strings.forEach { (key, frenchText) ->
            val englishText = english.strings[key] ?: return@forEach
            assertEquals("Paramètres différents pour $key", placeholders(frenchText), placeholders(englishText))
        }
    }

    @Test
    fun `les pluriels ont les memes parametres et les formes one et other`() {
        french.plurals.forEach { (key, frenchItems) ->
            val englishItems = english.plurals[key] ?: return@forEach
            assertTrue("$key : forme « one » ou « other » manquante", englishItems.keys.containsAll(setOf("one", "other")))
            assertEquals(
                "Paramètres différents pour $key",
                placeholders(frenchItems.getValue("other")),
                placeholders(englishItems.getValue("other"))
            )
        }
    }

    private companion object {
        /** `%%`, `%d`, `%1$s`, `%.1f`… Triés : l'ordre peut légitimement changer d'une langue à l'autre. */
        private val PLACEHOLDER = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfx%]""")

        fun placeholders(text: String): List<String> = PLACEHOLDER.findAll(text).map { it.value }.sorted().toList()

        fun load(path: String): Resources {
            val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path)).documentElement
            val elements = (0 until root.childNodes.length).map(root.childNodes::item).filterIsInstance<Element>()
                .filter { it.getAttribute("translatable") != "false" }

            val strings = elements.filter { it.tagName == "string" }
                .associate { it.getAttribute("name") to it.textContent }
            val plurals = elements.filter { it.tagName == "plurals" }.associate { plural ->
                val items = plural.getElementsByTagName("item")
                plural.getAttribute("name") to (0 until items.length).map { items.item(it) as Element }
                    .associate { it.getAttribute("quantity") to it.textContent }
            }
            return Resources(strings, plurals)
        }
    }
}
