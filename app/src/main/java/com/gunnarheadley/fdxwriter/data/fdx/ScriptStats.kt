package com.gunnarheadley.fdxwriter.data.fdx

import kotlin.math.ceil

/**
 * Summary statistics for a screenplay, derived from its paragraphs. Page count is an estimate:
 * screenplay pages run ~55 lines of Courier 12pt, and each element wraps to its own column width.
 */
data class ScriptStats(
    val sceneCount: Int,
    val wordCount: Int,
    val estimatedPages: Int,
    /** Dialogue blocks per character, most first. */
    val dialogueByCharacter: List<Pair<String, Int>>,
) {
    companion object {
        private const val LINES_PER_PAGE = 55

        fun compute(paragraphs: List<ScreenplayParagraph>): ScriptStats {
            var scenes = 0
            var words = 0
            var lines = 0
            val dialogue = LinkedHashMap<String, Int>()
            var speaker: String? = null

            for (p in paragraphs) {
                val text = p.plainText.trim()
                if (p.type == ElementType.SCENE_HEADING) scenes++
                if (text.isNotEmpty()) words += text.split(Regex("\\s+")).size

                // Rough printed-line estimate: wrap to the element's column, plus a leading blank
                // line for the elements that get vertical separation on the page.
                val wrapped = if (text.isEmpty()) 1 else ceil(text.length.toDouble() / p.type.columnWidth).toInt()
                val lead = when (p.type) {
                    ElementType.SCENE_HEADING, ElementType.ACTION,
                    ElementType.CHARACTER, ElementType.TRANSITION,
                    -> 1
                    else -> 0
                }
                lines += wrapped + lead

                when (p.type) {
                    ElementType.CHARACTER -> speaker = text.uppercase().substringBefore(" (").trim()
                    ElementType.DIALOGUE -> speaker?.takeIf { it.isNotEmpty() }?.let {
                        dialogue[it] = (dialogue[it] ?: 0) + 1
                    }
                    ElementType.SCENE_HEADING, ElementType.ACTION, ElementType.TRANSITION -> speaker = null
                    else -> {}
                }
            }

            val pages = maxOf(1, ceil(lines.toDouble() / LINES_PER_PAGE).toInt())
            val byChar = dialogue.entries.sortedByDescending { it.value }.map { it.key to it.value }
            return ScriptStats(scenes, words, pages, byChar)
        }
    }
}
