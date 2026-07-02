package com.example.fdxwriter.data.fdx

import org.junit.Assert.assertEquals
import org.junit.Test

class TextEditsTest {

    // ---- findRanges -----------------------------------------------------------------------

    @Test
    fun findsAllOccurrencesCaseInsensitively() {
        assertEquals(listOf(0 until 3, 8 until 11), TextEdits.findRanges("catxxxxxCAT", "cat"))
    }

    @Test
    fun emptyQueryFindsNothing() {
        assertEquals(emptyList<IntRange>(), TextEdits.findRanges("hello", ""))
    }

    @Test
    fun noMatchFindsNothing() {
        assertEquals(emptyList<IntRange>(), TextEdits.findRanges("hello", "z"))
    }

    @Test
    fun matchesAreNonOverlapping() {
        assertEquals(listOf(0 until 2, 2 until 4), TextEdits.findRanges("aaaa", "aa"))
    }

    // ---- spliceRuns -----------------------------------------------------------------------

    private fun List<StyledRun>.text() = joinToString("") { it.text }

    @Test
    fun replacesWithinASingleRun() {
        val out = TextEdits.spliceRuns(listOf(StyledRun("hello world")), 6, 11, "there")
        assertEquals("hello there", out.text())
    }

    @Test
    fun replacementInheritsStyleAtStart() {
        // "abc" bold + "def" plain; replace chars [1,5) ("bcde") with "X".
        val runs = listOf(StyledRun("abc", listOf(StyledRun.BOLD)), StyledRun("def"))
        val out = TextEdits.spliceRuns(runs, 1, 5, "X")
        assertEquals("aXf", out.text())
        val perChar = out.flatMap { r -> r.text.map { r.style } }
        assertEquals(listOf(StyledRun.BOLD), perChar[0]) // 'a'
        assertEquals(listOf(StyledRun.BOLD), perChar[1]) // 'X' inherits start style
        assertEquals(emptyList<String>(), perChar[2])    // 'f'
    }

    @Test
    fun adjacentSameStyleRunsAreCoalesced() {
        val runs = listOf(StyledRun("ab", listOf(StyledRun.BOLD)), StyledRun("cd", listOf(StyledRun.BOLD)))
        val out = TextEdits.spliceRuns(runs, 1, 3, "X") // "bc" -> "X"
        assertEquals("aXd", out.text())
        assertEquals("should coalesce into one run", 1, out.size)
    }

    @Test
    fun deletingAllTextYieldsNoRuns() {
        assertEquals(emptyList<StyledRun>(), TextEdits.spliceRuns(listOf(StyledRun("abc")), 0, 3, ""))
    }

    @Test
    fun insertionAtEndInheritsPrecedingStyle() {
        val out = TextEdits.spliceRuns(listOf(StyledRun("ab", listOf(StyledRun.ITALIC))), 2, 2, "cd")
        assertEquals("abcd", out.text())
        assertEquals(1, out.size)
        assertEquals(listOf(StyledRun.ITALIC), out[0].style)
    }
}
