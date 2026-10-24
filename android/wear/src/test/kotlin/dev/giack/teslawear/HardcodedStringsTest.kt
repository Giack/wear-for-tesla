package dev.giack.teslawear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The lint check for localisation: user-visible text lives in string resources, and every
 * translatable English string has an Italian twin. Android lint's HardcodedText only covers XML,
 * so the Kotlin side is checked here by the places text reaches the screen.
 */
class HardcodedStringsTest {
    private val module = File(".").absoluteFile.let { if (File(it, "src/main").exists()) it else File(it, "wear") }

    @Test fun kotlinSourcesShowNoLiteralText() {
        val offending = module.resolve("src").walkTopDown()
            .filter { it.extension == "kt" && !it.relativeTo(module.resolve("src")).path.startsWith("test") }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val code = line.substringBefore("//").trim()
                    if (code.startsWith("*") || code.startsWith("/*")) return@mapIndexedNotNull null
                    VISIBLE.firstOrNull { it.containsMatchIn(code) }?.let { "${file.relativeTo(module)}:${i + 1}: $code" }
                }
            }.toList()
        assertTrue("Hard-coded user-visible text, move it to strings.xml:\n" + offending.joinToString("\n"), offending.isEmpty())
    }

    @Test fun theCheckTellsTextFromCode() {
        fun flagged(code: String) = VISIBLE.any { it.containsMatchIn(code) }
        listOf(
            """Text("Impostazioni")""", """contentDescription = "Configura"""", """message = "Invio…"""",
            """Toast.makeText(this, "Segreti importati", 1)""", """UiText.Raw("Model Y")""", """.setLabel("VIN")""",
        ).forEach { assertTrue(it, flagged(it)) }
        listOf(
            """Text(stringResource(R.string.setup_title))""", """Text(s?.rangeKm?.let { "${'$'}it km" } ?: "")""",
            """Text("--", fontSize = 9.sp)""", """UiText.Raw("${'$'}{it.roundToInt()}°")""", """Diag.i("Car removed")""",
        ).forEach { assertTrue(it, !flagged(it)) }
    }

    @Test fun manifestLabelsAreResources() {
        val labels = Regex("""android:label="([^"]*)"""").findAll(module.resolve("src/main/AndroidManifest.xml").readText())
            .map { it.groupValues[1] }.toList()
        assertTrue(labels.isNotEmpty())
        assertTrue("Manifest labels must be @string: $labels", labels.all { it.startsWith("@string/") })
    }

    @Test fun everyEnglishStringHasAnItalianTranslation() {
        val english = translatable(module.resolve("src/main/res/values/strings.xml"))
        val italian = translatable(module.resolve("src/main/res/values-it/strings.xml"))
        assertEquals("Missing in Italian", emptySet<String>(), english - italian)
        assertEquals("Italian only (stale)", emptySet<String>(), italian - english)
    }

    @Test fun italianKeepsTheFormatArgumentsOfEnglish() {
        val english = formats(module.resolve("src/main/res/values/strings.xml"))
        val italian = formats(module.resolve("src/main/res/values-it/strings.xml"))
        val mismatched = italian.filter { (name, args) -> english[name] != args }.keys
        assertEquals(emptySet<String>(), mismatched)
    }

    private fun entries(file: File): List<org.w3c.dom.Element> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return (doc.getElementsByTagName("string").items() + doc.getElementsByTagName("plurals").items())
    }

    private fun org.w3c.dom.NodeList.items() = (0 until length).map { item(it) as org.w3c.dom.Element }

    private fun translatable(file: File): Set<String> =
        entries(file).filter { it.getAttribute("translatable") != "false" }.map { "${it.tagName}/${it.getAttribute("name")}" }.toSet()

    private fun formats(file: File): Map<String, List<String>> = entries(file).associate { e ->
        e.getAttribute("name") to FORMAT.findAll(e.textContent).map { it.value }.toSortedSet().toList()
    }

    private companion object {
        /** Words that read the same in every supported language. */
        private const val NEUTRAL = """(?!km\b)"""
        private const val WORDS = """"[^"]*?(?<![$\\{.])\b$NEUTRAL\p{L}{2,}"""

        /** Where a literal would reach the user: Compose text, descriptions, labels, toasts, untranslated UiText. */
        val VISIBLE = listOf(
            Regex("""\bText\(\s*$WORDS"""),
            Regex("""\b(contentDescription|label|message|title|description)\s*=\s*$WORDS"""),
            Regex("""\.set(Text|Label|ContentDescription)\(\s*$WORDS"""),
            Regex("""ComplicationText\.Builder\(\s*$WORDS"""),
            Regex("""makeText\([^,]+,\s*$WORDS"""),
            Regex("""UiText\.Raw\(\s*$WORDS"""),
        )
        val FORMAT = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z]""")
    }
}
