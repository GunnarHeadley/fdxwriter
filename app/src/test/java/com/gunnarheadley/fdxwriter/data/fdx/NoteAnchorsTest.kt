package com.gunnarheadley.fdxwriter.data.fdx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NoteAnchorsTest {

    private fun para(text: String) = ScreenplayParagraph(
        key = "p_$text",
        typeName = "Action",
        runs = if (text.isEmpty()) emptyList() else listOf(StyledRun(text)),
    )

    private fun note(start: Int, end: Int) = NoteAnnotation(id = "n1", start = start, end = end)

    @Test
    fun emptyNotesReturnsSameInstance() {
        val paras = listOf(para("abc"))
        val notes = emptyList<NoteAnnotation>()
        assertSame(notes, NoteAnchors.adjust(paras, listOf(para("abcd")), notes))
    }

    @Test
    fun styleOnlyChangeLeavesNotesUntouched() {
        // Same text (only run styling would differ) -> offsets unchanged, same list instance.
        val old = listOf(para("abc"))
        val new = listOf(para("abc"))
        val notes = listOf(note(1, 2))
        assertSame(notes, NoteAnchors.adjust(old, new, notes))
    }

    @Test
    fun insertBeforeNoteShiftsRight() {
        // "abcdef" -> "abXXcdef": 2 chars inserted at index 2; note at [4,5) -> [6,7).
        val out = NoteAnchors.adjust(listOf(para("abcdef")), listOf(para("abXXcdef")), listOf(note(4, 5)))
        assertEquals(6, out[0].start)
        assertEquals(7, out[0].end)
        assertEquals(true, out[0].dirty)
    }

    @Test
    fun insertAfterNoteLeavesItUnchanged() {
        val out = NoteAnchors.adjust(listOf(para("abcdef")), listOf(para("abcdefXX")), listOf(note(1, 3)))
        assertEquals(1, out[0].start)
        assertEquals(3, out[0].end)
        assertEquals(false, out[0].dirty)
    }

    @Test
    fun deleteBeforeNoteShiftsLeft() {
        // "abcdef" -> "adef": "bc" removed at index 1; note at [4,5) -> [2,3).
        val out = NoteAnchors.adjust(listOf(para("abcdef")), listOf(para("adef")), listOf(note(4, 5)))
        assertEquals(2, out[0].start)
        assertEquals(3, out[0].end)
    }

    @Test
    fun editInsideNoteRangeClampsToEditStart() {
        // "abcdef" -> "aZf": "bcde" replaced by "Z"; note at [2,4) collapses to the edit start.
        val out = NoteAnchors.adjust(listOf(para("abcdef")), listOf(para("aZf")), listOf(note(2, 4)))
        assertEquals(1, out[0].start)
        assertEquals(1, out[0].end)
    }

    @Test
    fun insertingAParagraphShiftsLaterNotes() {
        // Add "action" between "scene" and "dialogue": note anchored in "dialogue" shifts by
        // len("action") + one separator = 7.
        val old = listOf(para("scene"), para("dialogue"))
        val new = listOf(para("scene"), para("action"), para("dialogue"))
        val out = NoteAnchors.adjust(old, new, listOf(note(8, 10)))
        assertEquals(15, out[0].start)
        assertEquals(17, out[0].end)
    }

    @Test
    fun editAfterNoteInEarlierParagraphKeepsNoteStable() {
        // Note lives in the first paragraph; typing happens in the second -> note must not move.
        val old = listOf(para("scene"), para("dialogue"))
        val new = listOf(para("scene"), para("dialogue plus"))
        val out = NoteAnchors.adjust(old, new, listOf(note(1, 3)))
        assertEquals(1, out[0].start)
        assertEquals(3, out[0].end)
        assertEquals(false, out[0].dirty)
    }
}
