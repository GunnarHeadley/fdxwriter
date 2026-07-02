package com.gunnarheadley.fdxwriter.data.fdx

/**
 * Maps between absolute character offsets (as used by `<ScriptNote Range="start,end">` and
 * `<TextState Selection>`) and a (paragraph index, character-in-paragraph) position.
 *
 * Final Draft counts the concatenated text of every `<Content>` paragraph, with a single
 * separator character for the return between paragraphs:
 *
 *     offset(paragraph p, char c) = sum over k<p of (len(text_k) + 1) + c
 *
 * This is validated against the 11 real notes in Summer.fdx by the unit tests.
 */
class ScriptOffsetMapper(private val paragraphs: List<ScreenplayParagraph>) {

    private val starts = IntArray(paragraphs.size)
    private val lengths = IntArray(paragraphs.size)

    /** Total length of the offset space (sum of paragraph text lengths + one separator each). */
    val totalLength: Int

    init {
        var acc = 0
        for (i in paragraphs.indices) {
            val len = paragraphs[i].plainText.length
            starts[i] = acc
            lengths[i] = len
            acc += len + SEPARATOR
        }
        totalLength = acc
    }

    data class Position(val paragraphIndex: Int, val charInParagraph: Int)

    fun paragraphStart(index: Int): Int = starts.getOrElse(index) { totalLength }

    fun positionToOffset(paragraphIndex: Int, charInParagraph: Int): Int {
        if (paragraphs.isEmpty()) return 0
        val i = paragraphIndex.coerceIn(0, paragraphs.size - 1)
        val c = charInParagraph.coerceIn(0, lengths[i])
        return starts[i] + c
    }

    fun offsetToPosition(offset: Int): Position {
        if (paragraphs.isEmpty()) return Position(0, 0)
        val o = offset.coerceIn(0, totalLength)
        var lo = 0
        var hi = paragraphs.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val start = starts[mid]
            val end = start + lengths[mid]
            when {
                o < start -> hi = mid - 1
                o > end -> lo = mid + 1
                else -> return Position(mid, o - start)
            }
        }
        // o is past the last paragraph's text (e.g. == totalLength): clamp to its end.
        val last = paragraphs.size - 1
        return Position(last, lengths[last])
    }

    /** True when an offset range is fully inside the valid offset space. */
    fun isInBounds(start: Int, end: Int): Boolean =
        start in 0..totalLength && end in 0..totalLength && start <= end

    companion object {
        const val SEPARATOR = 1
    }
}
