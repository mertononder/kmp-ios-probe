package dev.probe.kmp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Things that are known to differ between the JVM and Kotlin/Native runtimes; run on both. */
class ProbeTest {
    @Test fun numberTextIsTheSameOnEveryRuntime() {
        assertEquals("1.5E-5", 1.5E-5.toString())
        assertEquals("41.0082", 41.0082.toString())
        assertEquals("1.0E21", 1.0E21.toString())
        assertEquals("0.1", 0.1f.toString())
    }

    @Test fun regexFeaturesUsedByTextParsing() {
        assertTrue(Regex("(?<=\\d)\\p{L}+").containsMatchIn("12km"))
        assertEquals(listOf("çay", "şeker"), Regex("\\p{L}+").findAll("çay, şeker").map { it.value }.toList())
    }

    @Test fun invariantCasing() {
        assertEquals("istanbul", "ISTANBUL".lowercase())
        assertEquals("İ".lowercase().length, 2)
    }
}
