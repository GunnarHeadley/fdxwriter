package com.example.fdxwriter.data.fdx

/**
 * Keeps script-note character offsets aligned with the text as it's edited. Notes store absolute
 * offsets into the concatenated `<Content>` text; when a paragraph's text — or the paragraph
 * structure — changes, offsets after the edit must shift so each note stays anchored to the same
 * words. A single prefix/suffix diff of the whole text covers typing, splits, merges, deletions,
 * and find/replace uniformly.
 */
object NoteAnchors {

    /**
     * Return [notes] with [NoteAnnotation.start]/[NoteAnnotation.end] shifted to match the change
     * from [oldParagraphs] to [newParagraphs]. Notes whose offsets move are flagged dirty so the
     * new range is written on save. Returns the same list instance when nothing moved.
     */
    fun adjust(
        oldParagraphs: List<ScreenplayParagraph>,
        newParagraphs: List<ScreenplayParagraph>,
        notes: List<NoteAnnotation>,
    ): List<NoteAnnotation> {
        if (notes.isEmpty()) return notes
        val oldText = concat(oldParagraphs)
        val newText = concat(newParagraphs)
        if (oldText == newText) return notes

        val minLen = minOf(oldText.length, newText.length)
        var prefix = 0
        while (prefix < minLen && oldText[prefix] == newText[prefix]) prefix++
        var suffix = 0
        while (
            suffix < minLen - prefix &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) {
            suffix++
        }
        val oldChangeEnd = oldText.length - suffix
        val delta = newText.length - oldText.length

        // Offsets before the edit stay put; those at/after the edit shift; those inside it clamp
        // to where the edit began.
        fun remap(o: Int): Int = when {
            o <= prefix -> o
            o >= oldChangeEnd -> o + delta
            else -> prefix
        }

        var changed = false
        val result = notes.map { note ->
            val start = remap(note.start).coerceIn(0, newText.length)
            val end = remap(note.end).coerceIn(start, newText.length)
            if (start != note.start || end != note.end) {
                changed = true
                note.copy(start = start, end = end, dirty = true)
            } else {
                note
            }
        }
        return if (changed) result else notes
    }

    /** Concatenate paragraph text with one separator per paragraph, matching [ScriptOffsetMapper]. */
    private fun concat(paragraphs: List<ScreenplayParagraph>): String = buildString {
        for (p in paragraphs) {
            append(p.plainText)
            append('\u0001')
        }
    }
}
