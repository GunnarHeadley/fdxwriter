package com.gunnarheadley.fdxwriter.data.fdx

import kotlin.math.ceil

/**
 * Summary statistics for a screenplay, derived from its paragraphs. Page count is an estimate:
 * each element wraps to its column width and carries Final Draft's inter-element spacing, packed
 * into ~53 lines per page (tuned to match Final Draft's own pagination).
 */
data class ScriptStats(
    val sceneCount: Int,
    val wordCount: Int,
    val estimatedPages: Int,
    /** Dialogue blocks per character, most first. */
    val dialogueByCharacter: List<Pair<String, Int>>,
) {
    companion object {
        // A US-Letter screenplay page holds ~54 printed lines; trimmed to 53 to approximate the
        // blank space Final Draft leaves at page breaks (it keeps character/dialogue blocks and
        // scene headings together instead of filling every page completely).
        private const val LINES_PER_PAGE = 53

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

                lines += paragraphLines(p)

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

        /** Approximate printed line count contributed by one paragraph. */
        private fun paragraphLines(p: ScreenplayParagraph): Int {
            val text = p.plainText.trim()
            val wrapped = if (text.isEmpty()) 1 else ceil(text.length.toDouble() / p.type.columnWidth).toInt()
            // Blank lines Final Draft inserts before an element (its SpaceBefore, in 12pt lines):
            // scene headings get two, action/character/transition one, dialogue/parenthetical none.
            val lead = when (p.type) {
                ElementType.SCENE_HEADING -> 2
                ElementType.ACTION, ElementType.CHARACTER, ElementType.TRANSITION -> 1
                else -> 0
            }
            return wrapped + lead
        }

        /** For each paragraph that begins a new printed page, its 1-based page number (>= 2). */
        fun pageStarts(paragraphs: List<ScreenplayParagraph>): Map<Int, Int> {
            val out = LinkedHashMap<Int, Int>()
            var lines = 0
            var page = 1
            for ((i, p) in paragraphs.withIndex()) {
                val startPage = lines / LINES_PER_PAGE + 1
                if (startPage > page) {
                    page = startPage
                    out[i] = startPage
                }
                lines += paragraphLines(p)
            }
            return out
        }
    }
}
