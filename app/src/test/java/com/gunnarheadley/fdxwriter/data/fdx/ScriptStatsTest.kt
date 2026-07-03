package com.gunnarheadley.fdxwriter.data.fdx

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptStatsTest {

    private fun para(type: ElementType, text: String) = ScreenplayParagraph(
        key = "k_${type}_${text.hashCode()}",
        typeName = type.fdxName,
        runs = if (text.isEmpty()) emptyList() else listOf(StyledRun(text)),
    )

    @Test
    fun countsScenesWordsAndDialogueBySpeaker() {
        val paragraphs = listOf(
            para(ElementType.SCENE_HEADING, "INT. OFFICE - DAY"),
            para(ElementType.ACTION, "A quiet room."),
            para(ElementType.CHARACTER, "MARCUS"),
            para(ElementType.DIALOGUE, "Hello there friend."),
            para(ElementType.CHARACTER, "ADAORA"),
            para(ElementType.DIALOGUE, "Hi."),
            para(ElementType.CHARACTER, "MARCUS"),
            para(ElementType.DIALOGUE, "Again."),
            para(ElementType.SCENE_HEADING, "EXT. STREET - NIGHT"),
        )
        val stats = ScriptStats.compute(paragraphs)

        assertEquals(2, stats.sceneCount)
        // Whitespace-split tokens, so the "-" in each scene heading counts too:
        // 4 + 3 + 1 + 3 + 1 + 1 + 1 + 1 + 4 = 19.
        assertEquals(19, stats.wordCount)
        // MARCUS speaks twice, ADAORA once; MARCUS listed first.
        assertEquals(listOf("MARCUS" to 2, "ADAORA" to 1), stats.dialogueByCharacter)
        assertEquals(true, stats.estimatedPages >= 1)
    }

    @Test
    fun stripsParentheticalExtensionFromSpeaker() {
        val paragraphs = listOf(
            para(ElementType.CHARACTER, "JOHN (V.O.)"),
            para(ElementType.DIALOGUE, "Narration."),
            para(ElementType.CHARACTER, "JOHN"),
            para(ElementType.DIALOGUE, "On screen now."),
        )
        val stats = ScriptStats.compute(paragraphs)
        assertEquals(listOf("JOHN" to 2), stats.dialogueByCharacter)
    }

    @Test
    fun emptyScriptIsOnePageNoScenes() {
        val stats = ScriptStats.compute(emptyList())
        assertEquals(0, stats.sceneCount)
        assertEquals(0, stats.wordCount)
        assertEquals(1, stats.estimatedPages)
        assertEquals(emptyList<Pair<String, Int>>(), stats.dialogueByCharacter)
    }
}
