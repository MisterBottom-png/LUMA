package com.orbit.app.ui.localization

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationResourceParityTest {
    @Test
    fun `home capture placeholder has the approved localized value in each supported language`() {
        val resDirectory = projectRoot().resolve("app/src/main/res")

        assertEquals(
            "What’s on your mind?",
            stringValue(resDirectory.resolve("values"), "core_home_capture_placeholder"),
        )
        assertEquals(
            "Mis sul mõttes on?",
            stringValue(resDirectory.resolve("values-et"), "core_home_capture_placeholder"),
        )
        assertEquals(
            "Что у тебя на уме?",
            stringValue(resDirectory.resolve("values-ru"), "core_home_capture_placeholder"),
        )
    }

    @Test
    fun `every translatable English string and plural has Estonian and Russian variants`() {
        val resDirectory = projectRoot().resolve("app/src/main/res")
        val englishNames = translatableResourceNames(resDirectory.resolve("values"))

        listOf("values-et", "values-ru").forEach { localeDirectory ->
            val missing = englishNames - translatableResourceNames(resDirectory.resolve(localeDirectory))
            assertTrue("$localeDirectory is missing: ${missing.sorted()}", missing.isEmpty())
        }
    }

    private fun projectRoot(): File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .firstOrNull { it.resolve("app/src/main/res/values").isDirectory }
        ?: error("Unable to locate app resources from ${System.getProperty("user.dir")}")

    private fun translatableResourceNames(directory: File): Set<String> = directory
        .listFiles { file -> file.isFile && file.name.startsWith("strings") && file.extension == "xml" }
        .orEmpty()
        .flatMap { file ->
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            document.documentElement.childNodes.let { nodes ->
                (0 until nodes.length).mapNotNull { index ->
                    val element = nodes.item(index) as? org.w3c.dom.Element ?: return@mapNotNull null
                    element.getAttribute("name")
                        .takeIf { element.tagName in setOf("string", "plurals") && element.getAttribute("translatable") != "false" }
                }
            }
        }
        .toSet()

    private fun stringValue(directory: File, name: String): String {
        val document = directory
            .listFiles { file -> file.isFile && file.name.startsWith("strings") && file.extension == "xml" }
            .orEmpty()
            .asSequence()
            .map { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
            .firstOrNull { document ->
                document.documentElement.childNodes.let { nodes ->
                    (0 until nodes.length).any { index ->
                        val element = nodes.item(index) as? org.w3c.dom.Element
                        element?.tagName == "string" && element.getAttribute("name") == name
                    }
                }
            }
            ?: error("Missing string resource: $name")
        val nodes = document.documentElement.childNodes
        return (0 until nodes.length)
            .asSequence()
            .mapNotNull { nodes.item(it) as? org.w3c.dom.Element }
            .first { it.tagName == "string" && it.getAttribute("name") == name }
            .textContent
    }
}
