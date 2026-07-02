package com.example.fdxwriter.data.fdx

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.InputStream
import java.io.StringWriter
import java.io.Writer

/**
 * A parsed FDX file: the full DOM (the source of truth for everything we don't edit) plus the
 * editable [model]. Unedited paragraphs/beats/notes are kept as cloned original DOM nodes so the
 * serializer can reproduce them verbatim; only items the user actually changes are rebuilt.
 */
class FdxDocument internal constructor(
    val dom: Document,
    val model: ScriptModel,
    internal val originalParagraphNodes: Map<String, Element>,
    internal val scenePropsByKey: Map<String, Element>,
    internal val originalBeatNodes: Map<String, Element>,
    internal val originalNoteNodes: Map<String, Element>,
) {
    /** A fresh offset mapper for the current paragraph list. */
    fun offsetMapper(): ScriptOffsetMapper = ScriptOffsetMapper(model.paragraphs)

    /** Return a document backed by the same DOM/preserved nodes but a new editable [model]. */
    fun withModel(newModel: ScriptModel): FdxDocument =
        FdxDocument(dom, newModel, originalParagraphNodes, scenePropsByKey, originalBeatNodes, originalNoteNodes)

    fun serialize(writer: Writer) = FdxSerializer.write(this, writer)

    fun serializeToString(): String = StringWriter().also { serialize(it) }.toString()

    companion object {
        fun parse(input: InputStream): FdxDocument = FdxParser.parse(input)

        fun parse(xml: String): FdxDocument = parse(xml.byteInputStream(Charsets.UTF_8))

        /** A brand-new, empty script: a single blank Scene Heading line. */
        fun blank(): FdxDocument = parse(BLANK_TEMPLATE)

        private const val BLANK_TEMPLATE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n" +
                "<FinalDraft DocumentType=\"Script\" Template=\"No\" Version=\"5\">\n" +
                "  <Content>\n" +
                "    <Paragraph Type=\"Scene Heading\"/>\n" +
                "  </Content>\n" +
                "</FinalDraft>\n"
    }
}
