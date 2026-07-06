package com.gunnarheadley.fdxwriter.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.gunnarheadley.fdxwriter.data.fdx.StyledRun

/**
 * Bridges FDX [StyledRun]s and Compose rich text. Bold/Italic/Underline map to [SpanStyle]s;
 * other run attributes aren't editable, so an edited paragraph keeps only B/I/U (unedited
 * paragraphs are preserved verbatim by the serializer).
 */
object RichText {

    private const val BOLD = StyledRun.BOLD
    private const val ITALIC = StyledRun.ITALIC
    private const val UNDERLINE = StyledRun.UNDERLINE

    /**
     * A stable, distinct color for a character name so the same character reads the same color
     * throughout the script. Hue is derived from the (trimmed, upper-cased) name; saturation and
     * lightness are fixed for legibility on the page.
     */
    fun characterColor(name: String): Color {
        val key = name.trim().uppercase()
        val hue = ((key.hashCode() % 360) + 360) % 360
        return Color.hsl(hue.toFloat(), 0.65f, 0.42f)
    }

    fun toTextFieldValue(runs: List<StyledRun>): TextFieldValue =
        TextFieldValue(annotatedString = runsToAnnotatedString(runs))

    fun runsToAnnotatedString(runs: List<StyledRun>): AnnotatedString = buildAnnotatedString {
        for (run in runs) {
            val start = length
            append(run.text)
            val tokens = visualTokens(run.style)
            if (tokens.isNotEmpty()) addStyle(spanFor(tokens), start, length)
        }
    }

    fun annotatedToRuns(anno: AnnotatedString): List<StyledRun> {
        val text = anno.text
        if (text.isEmpty()) return emptyList()
        val tokens = charTokens(anno)
        val result = ArrayList<StyledRun>()
        var i = 0
        while (i < text.length) {
            val t = tokens[i]
            var j = i + 1
            while (j < text.length && tokens[j] == t) j++
            result.add(StyledRun(text.substring(i, j), t.toList()))
            i = j
        }
        return result
    }

    /** Toggle [token] over the current selection. No-op when the selection is collapsed. */
    fun toggleToken(value: TextFieldValue, token: String): TextFieldValue {
        val sel = value.selection
        if (sel.collapsed) return value
        val anno = value.annotatedString
        val tokens = charTokens(anno).map { it.toMutableSet() }
        val start = sel.min.coerceIn(0, tokens.size)
        val end = sel.max.coerceIn(0, tokens.size)
        if (start >= end) return value
        val allHave = (start until end).all { token in tokens[it] }
        for (k in start until end) {
            if (allHave) tokens[k].remove(token) else tokens[k].add(token)
        }
        return value.copy(annotatedString = build(anno.text, tokens))
    }

    /** The B/I/U tokens applying immediately before [position] (empty for empty text). */
    fun tokensAt(value: TextFieldValue, position: Int): Set<String> {
        val anno = value.annotatedString
        if (anno.text.isEmpty()) return emptySet()
        val tokens = charTokens(anno)
        val i = (position - 1).coerceIn(0, tokens.size - 1)
        return tokens[i].toSet()
    }

    /**
     * Rebuild [next]'s B/I/U styling from [old] so edits behave like a word processor: unchanged
     * text keeps its formatting (even when a soft keyboard resends a composing word as plain text),
     * deleting leaves the remaining formatting intact, and newly typed text takes [pending] when the
     * user armed a style, otherwise inherits the character before it.
     */
    fun reconcileStyle(old: TextFieldValue, next: TextFieldValue, pending: Set<String>?): TextFieldValue {
        val oldAnno = old.annotatedString
        val oldText = oldAnno.text
        val nextText = next.text
        val oldLen = oldText.length
        val nextLen = nextText.length
        if (nextLen == 0) return next
        val oldTokens = charTokens(oldAnno)
        // Longest common prefix and (non-overlapping) suffix of the old and new text.
        val maxPrefix = minOf(oldLen, nextLen)
        var p = 0
        while (p < maxPrefix && oldText[p] == nextText[p]) p++
        var s = 0
        while (s < maxPrefix - p && oldText[oldLen - 1 - s] == nextText[nextLen - 1 - s]) s++
        val newStyle: Set<String> = pending ?: if (p > 0) oldTokens[p - 1] else emptySet()
        val per = ArrayList<Set<String>>(nextLen)
        for (i in 0 until nextLen) {
            per += when {
                i < p -> oldTokens[i]                            // unchanged prefix keeps its style
                i >= nextLen - s -> oldTokens[oldLen - (nextLen - i)] // unchanged suffix keeps its style
                else -> newStyle                                 // inserted/changed run
            }
        }
        return next.copy(annotatedString = build(nextText, per))
    }

    fun isTokenActive(value: TextFieldValue, token: String): Boolean {
        val anno = value.annotatedString
        if (anno.text.isEmpty()) return false
        val tokens = charTokens(anno)
        val sel = value.selection
        return if (sel.collapsed) {
            val i = (sel.start - 1).coerceIn(0, tokens.size - 1)
            token in tokens[i]
        } else {
            val start = sel.min.coerceIn(0, tokens.size)
            val end = sel.max.coerceIn(0, tokens.size)
            start < end && (start until end).all { token in tokens[it] }
        }
    }

    /** Display caps types (Scene Heading, Character, …) in uppercase without changing stored text. */
    val UppercaseTransformation = VisualTransformation { text ->
        val upper = buildAnnotatedString {
            append(text.text.map { it.uppercaseChar() }.joinToString(""))
            for (span in text.spanStyles) addStyle(span.item, span.start, span.end)
        }
        TransformedText(upper, OffsetMapping.Identity)
    }

    /** A background highlight over [start, end) in the field's raw-text coordinates. */
    data class HighlightSpan(val start: Int, val end: Int, val color: Color)

    /**
     * A [VisualTransformation] that optionally uppercases display text (for caps element types)
     * and paints [highlights] as background spans, showing where notes are anchored. Text length
     * is preserved, so offsets map 1:1.
     */
    fun highlightTransformation(uppercase: Boolean, highlights: List<HighlightSpan>): VisualTransformation =
        VisualTransformation { text ->
            val display = buildAnnotatedString {
                val shown = if (uppercase) text.text.map { it.uppercaseChar() }.joinToString("") else text.text
                append(shown)
                for (span in text.spanStyles) addStyle(span.item, span.start, span.end)
                for (h in highlights) {
                    val s = h.start.coerceIn(0, length)
                    val e = h.end.coerceIn(0, length)
                    if (s < e) addStyle(SpanStyle(background = h.color), s, e)
                }
            }
            TransformedText(display, OffsetMapping.Identity)
        }

    private fun charTokens(anno: AnnotatedString): Array<MutableSet<String>> {
        val n = anno.length
        val acc = Array(n) { mutableSetOf<String>() }
        for (range in anno.spanStyles) {
            val toks = tokensFromSpan(range.item)
            if (toks.isEmpty()) continue
            val s = range.start.coerceIn(0, n)
            val e = range.end.coerceIn(0, n)
            for (i in s until e) acc[i].addAll(toks)
        }
        return acc
    }

    private fun build(text: String, tokens: List<Set<String>>): AnnotatedString = buildAnnotatedString {
        append(text)
        var i = 0
        while (i < text.length) {
            val t = tokens[i]
            var j = i + 1
            while (j < text.length && tokens[j] == t) j++
            if (t.isNotEmpty()) addStyle(spanFor(t), i, j)
            i = j
        }
    }

    private fun tokensFromSpan(span: SpanStyle): Set<String> {
        val out = mutableSetOf<String>()
        val weight = span.fontWeight
        if (weight != null && weight.weight >= FontWeight.Bold.weight) out.add(BOLD)
        if (span.fontStyle == FontStyle.Italic) out.add(ITALIC)
        if (span.textDecoration?.contains(TextDecoration.Underline) == true) out.add(UNDERLINE)
        return out
    }

    private fun visualTokens(style: List<String>): Set<String> =
        style.filterTo(mutableSetOf()) { it == BOLD || it == ITALIC || it == UNDERLINE }

    private fun spanFor(tokens: Set<String>): SpanStyle = SpanStyle(
        fontWeight = if (BOLD in tokens) FontWeight.Bold else null,
        fontStyle = if (ITALIC in tokens) FontStyle.Italic else null,
        textDecoration = if (UNDERLINE in tokens) TextDecoration.Underline else null,
    )
}
