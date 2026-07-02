package com.gunnarheadley.fdxwriter.data.fdx

/**
 * One `<Paragraph>` of the screenplay body. [key] is a stable identity assigned at parse time
 * so the serializer can re-attach preserved sub-structures (e.g. `<SceneProperties>`) and clone
 * the original node verbatim when the paragraph was not edited ([dirty] == false).
 */
data class ScreenplayParagraph(
    val key: String,
    val typeName: String,
    val runs: List<StyledRun>,
    val number: String? = null,
    val extraAttributes: Map<String, String> = emptyMap(),
    val hasSceneProperties: Boolean = false,
    val dirty: Boolean = false,
) {
    val type: ElementType get() = ElementType.fromFdx(typeName) ?: ElementType.GENERAL

    val plainText: String
        get() = if (runs.isEmpty()) "" else buildString { for (r in runs) append(r.text) }

    fun markDirty(): ScreenplayParagraph = if (dirty) this else copy(dirty = true)
}

/**
 * A beat card. Text lives in `<ListItems>/<ListItem>` and position in
 * `<DisplayBoards>/<DisplayBoard Type="Beat">/<Item>`; the two are joined by [id].
 */
data class Beat(
    val id: String,
    val title: String,
    val color: String,
    val bodyLines: List<String>,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val dirty: Boolean = false,
)

/**
 * A Final Draft script note (`<ScriptNote>`). [start]/[end] are absolute character offsets into
 * the concatenated `<Content>` text (see [ScriptOffsetMapper]). [body] is the note's rich text,
 * one inner-list of runs per paragraph.
 */
data class NoteAnnotation(
    val id: String,
    val start: Int,
    val end: Int,
    val type: String = "",
    val color: String = FdxColor.WHITE,
    val writerName: String = "",
    val writerId: String = "",
    val dateTime: String = "",
    val dateModified: String = "",
    val name: String = "",
    val body: List<List<StyledRun>> = emptyList(),
    val extraAttributes: Map<String, String> = emptyMap(),
    val dirty: Boolean = false,
) {
    val bodyText: String
        get() = body.joinToString("\n") { para -> para.joinToString("") { it.text } }
}

/** The editable, immutable representation of a script: body paragraphs, beats, and notes. */
data class ScriptModel(
    val paragraphs: List<ScreenplayParagraph>,
    val beats: List<Beat>,
    val notes: List<NoteAnnotation>,
)
