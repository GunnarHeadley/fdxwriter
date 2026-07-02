package com.example.fdxwriter.data.fdx

/**
 * Pure helpers for in-script find/replace: locating query matches and splicing rich-text runs.
 * Kept free of Android dependencies so they can be unit-tested directly.
 */
object TextEdits {

    /**
     * Every case-insensitive occurrence of [query] in [text], as non-overlapping ranges from left
     * to right. Returns an empty list when [query] is empty.
     */
    fun findRanges(text: String, query: String): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        var from = 0
        while (from <= text.length) {
            val idx = text.indexOf(query, from, ignoreCase = true)
            if (idx < 0) break
            out.add(idx until idx + query.length)
            from = idx + query.length
        }
        return out
    }

    /**
     * Replace the flattened character range [start, end) of [runs] with [replacement], which
     * inherits the style of the run at [start] (or the preceding run for an end-insertion). Other
     * characters keep their styles and adjacent same-style characters are re-coalesced into runs.
     * Non-B/I/U attributes are not preserved — the same behavior as any manual edit.
     */
    fun spliceRuns(runs: List<StyledRun>, start: Int, end: Int, replacement: String): List<StyledRun> {
        val chars = ArrayList<Pair<Char, List<String>>>()
        for (run in runs) for (c in run.text) chars.add(c to run.style)
        val s = start.coerceIn(0, chars.size)
        val e = end.coerceIn(s, chars.size)
        val replStyle = when {
            s < chars.size -> chars[s].second
            s > 0 -> chars[s - 1].second
            else -> emptyList()
        }
        val out = ArrayList<Pair<Char, List<String>>>()
        for (k in 0 until s) out.add(chars[k])
        for (c in replacement) out.add(c to replStyle)
        for (k in e until chars.size) out.add(chars[k])
        if (out.isEmpty()) return emptyList()
        val result = ArrayList<StyledRun>()
        var i = 0
        while (i < out.size) {
            val style = out[i].second
            var j = i + 1
            while (j < out.size && out[j].second == style) j++
            result.add(StyledRun(out.subList(i, j).joinToString("") { it.first.toString() }, style))
            i = j
        }
        return result
    }
}
