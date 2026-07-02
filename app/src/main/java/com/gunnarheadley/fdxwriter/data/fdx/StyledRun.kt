package com.gunnarheadley.fdxwriter.data.fdx

/**
 * A run of text inside a `<Paragraph>` — one `<Text>` element. [style] holds the `Style`
 * attribute's `+`-separated tokens (e.g. `Bold`, `Italic`, `Underline`, `AllCaps`,
 * `HiddenText`); unknown tokens are preserved. [attributes] holds every other `<Text>`
 * attribute verbatim (AdornmentStyle, Background, Color, Font, RevisionID, Size).
 */
data class StyledRun(
    val text: String,
    val style: List<String> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
) {
    val bold: Boolean get() = BOLD in style
    val italic: Boolean get() = ITALIC in style
    val underline: Boolean get() = UNDERLINE in style

    /** Return a copy with [token] added or removed, preserving the order of other tokens. */
    fun withStyleToken(token: String, enabled: Boolean): StyledRun {
        val present = token in style
        if (present == enabled) return this
        val newStyle = if (enabled) style + token else style.filterNot { it == token }
        return copy(style = newStyle)
    }

    companion object {
        const val BOLD = "Bold"
        const val ITALIC = "Italic"
        const val UNDERLINE = "Underline"

        fun parseStyle(value: String?): List<String> =
            value?.split('+')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        fun styleAttribute(style: List<String>): String = style.joinToString("+")
    }
}
