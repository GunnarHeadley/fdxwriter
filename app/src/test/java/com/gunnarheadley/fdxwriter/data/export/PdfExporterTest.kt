package com.gunnarheadley.fdxwriter.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfExporterTest {

    @Test
    fun textShorterThanColumnStaysOneLine() {
        assertEquals(listOf("hello world"), PdfExporter.wrap("hello world", 35))
    }

    @Test
    fun wrapsGreedilyAtWordBoundaries() {
        val lines = PdfExporter.wrap("aaaa bbbb cccc dddd", 9)
        assertEquals(listOf("aaaa bbbb", "cccc dddd"), lines)
        assertTrue(lines.all { it.length <= 9 })
    }

    @Test
    fun hardBreaksAWordLongerThanTheColumn() {
        val lines = PdfExporter.wrap("supercalifragilistic", 5)
        assertTrue(lines.all { it.length <= 5 })
        // No characters are lost when a long word is split.
        assertEquals("supercalifragilistic", lines.joinToString(""))
    }
}
