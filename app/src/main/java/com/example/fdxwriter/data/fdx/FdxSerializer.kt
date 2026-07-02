package com.example.fdxwriter.data.fdx

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.Writer
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Writes an [FdxDocument] back to FDX. The whole DOM is preserved; only the four editable
 * sections (`<Content>`, `<ListItems>`, the Beat `<DisplayBoard>` items, and `<ScriptNotes>`)
 * are regenerated from the model. Unedited items are emitted as exact clones of their original
 * nodes, so untouched content survives round-trips unchanged.
 */
object FdxSerializer {

    fun write(doc: FdxDocument, writer: Writer) {
        val dom = doc.dom
        val root = dom.documentElement
        rebuildContent(doc, root)
        rebuildListItems(doc, root)
        rebuildBeatBoardItems(doc, root)
        rebuildScriptNotes(doc, root)
        transform(dom, writer)
    }

    // ---- Content / paragraphs -------------------------------------------------------------

    private fun rebuildContent(doc: FdxDocument, root: Element) {
        val content = root.firstChild("Content") ?: return
        val children = doc.model.paragraphs.map { para ->
            if (!para.dirty && doc.originalParagraphNodes[para.key] != null) {
                doc.originalParagraphNodes.getValue(para.key).cloneNode(true) as Element
            } else {
                buildParagraph(doc, para)
            }
        }
        replaceChildrenIndented(content, children, childIndent = "\n    ", closeIndent = "\n  ")
    }

    private fun buildParagraph(doc: FdxDocument, para: ScreenplayParagraph): Element {
        val dom = doc.dom
        val e = dom.createElement("Paragraph")
        para.number?.let { e.setAttribute("Number", it) }
        e.setAttribute("Type", para.typeName)
        for ((k, v) in para.extraAttributes) e.setAttribute(k, v)
        if (para.type == ElementType.SCENE_HEADING) {
            val original = doc.scenePropsByKey[para.key]
            if (original != null) e.appendChild(original.cloneNode(true))
            else e.appendChild(buildMinimalSceneProperties(dom))
        }
        for (run in para.runs) e.appendChild(buildText(dom, run))
        return e
    }

    private fun buildText(dom: Document, run: StyledRun): Element {
        val e = dom.createElement("Text")
        for ((k, v) in run.attributes) e.setAttribute(k, v)
        if (run.style.isNotEmpty()) e.setAttribute("Style", StyledRun.styleAttribute(run.style))
        e.appendChild(dom.createTextNode(run.text))
        return e
    }

    private fun buildMinimalSceneProperties(dom: Document): Element {
        val sp = dom.createElement("SceneProperties")
        sp.setAttribute("Length", "")
        sp.setAttribute("Page", "")
        sp.setAttribute("Title", "")
        sp.appendChild(dom.createElement("SceneArcBeats"))
        return sp
    }

    // ---- Beats ----------------------------------------------------------------------------

    private fun rebuildListItems(doc: FdxDocument, root: Element) {
        val listItems = root.firstChild("ListItems") ?: return
        val children = doc.model.beats.map { beat ->
            if (!beat.dirty && doc.originalBeatNodes[beat.id] != null) {
                doc.originalBeatNodes.getValue(beat.id).cloneNode(true) as Element
            } else {
                buildBeatListItem(doc, beat)
            }
        }
        replaceChildrenIndented(listItems, children, childIndent = "\n    ", closeIndent = "\n  ")
    }

    private fun buildBeatListItem(doc: FdxDocument, beat: Beat): Element {
        val dom = doc.dom
        val li = dom.createElement("ListItem")
        li.setAttribute("Color", beat.color)
        li.setAttribute("Id", beat.id)
        li.setAttribute("Title", beat.title)
        li.setAttribute("Type", "Beat")
        val content = dom.createElement("Content")
        for (line in beat.bodyLines) {
            val para = dom.createElement("Paragraph")
            for ((k, v) in BEAT_PARAGRAPH_SPEC) para.setAttribute(k, v)
            if (line.isNotEmpty()) {
                val text = dom.createElement("Text")
                for ((k, v) in BEAT_TEXT_SPEC) text.setAttribute(k, v)
                text.appendChild(dom.createTextNode(line))
                para.appendChild(text)
            }
            content.appendChild(para)
        }
        li.appendChild(content)
        return li
    }

    private fun rebuildBeatBoardItems(doc: FdxDocument, root: Element) {
        val board = root.firstChild("DisplayBoards")
            ?.childElements("DisplayBoard")
            ?.firstOrNull { it.getAttribute("Type") == "Beat" }
            ?: return
        val dom = doc.dom
        val items = doc.model.beats.map { beat ->
            dom.createElement("Item").apply {
                setAttribute("Height", beat.height.toString())
                setAttribute("Id", beat.id)
                setAttribute("Left", beat.left.toString())
                setAttribute("Top", beat.top.toString())
                setAttribute("Width", beat.width.toString())
            }
        }
        replaceChildrenIndented(board, items, childIndent = "\n      ", closeIndent = "\n    ")
    }

    // ---- Notes ----------------------------------------------------------------------------

    private fun rebuildScriptNotes(doc: FdxDocument, root: Element) {
        val scriptNotes = root.firstChild("ScriptNotes") ?: return
        val children = doc.model.notes.map { note ->
            if (!note.dirty && doc.originalNoteNodes[note.id] != null) {
                doc.originalNoteNodes.getValue(note.id).cloneNode(true) as Element
            } else {
                buildNote(doc, note)
            }
        }
        replaceChildrenIndented(scriptNotes, children, childIndent = "\n    ", closeIndent = "\n  ")
    }

    private fun buildNote(doc: FdxDocument, note: NoteAnnotation): Element {
        val dom = doc.dom
        val e = dom.createElement("ScriptNote")
        e.setAttribute("Color", note.color)
        if (note.dateModified.isNotEmpty()) e.setAttribute("DateModified", note.dateModified)
        if (note.dateTime.isNotEmpty()) e.setAttribute("DateTime", note.dateTime)
        e.setAttribute("Id", note.id)
        e.setAttribute("Name", note.name)
        e.setAttribute("Range", "${note.start},${note.end}")
        e.setAttribute("Type", note.type)
        if (note.writerId.isNotEmpty()) e.setAttribute("WriterID", note.writerId)
        if (note.writerName.isNotEmpty()) e.setAttribute("WriterName", note.writerName)
        for ((k, v) in note.extraAttributes) e.setAttribute(k, v)

        val paragraphs = note.body.ifEmpty { listOf(emptyList()) }
        for (runs in paragraphs) {
            val para = dom.createElement("Paragraph")
            for ((k, v) in NOTE_PARAGRAPH_SPEC) para.setAttribute(k, v)
            if (runs.isEmpty()) {
                // Preserve an empty note line as a single default-styled run for Final Draft.
            } else {
                for (run in runs) {
                    val text = dom.createElement("Text")
                    val attrs = if (run.attributes.isEmpty()) NOTE_TEXT_SPEC else run.attributes
                    for ((k, v) in attrs) text.setAttribute(k, v)
                    if (run.style.isNotEmpty()) text.setAttribute("Style", StyledRun.styleAttribute(run.style))
                    text.appendChild(dom.createTextNode(run.text))
                    para.appendChild(text)
                }
            }
            e.appendChild(para)
        }
        return e
    }

    // ---- Shared ---------------------------------------------------------------------------

    private fun replaceChildrenIndented(
        parent: Element,
        children: List<Element>,
        childIndent: String,
        closeIndent: String,
    ) {
        val dom = parent.ownerDocument
        parent.clearChildren()
        if (children.isEmpty()) return
        for (child in children) {
            parent.appendChild(dom.createTextNode(childIndent))
            parent.appendChild(child)
        }
        parent.appendChild(dom.createTextNode(closeIndent))
    }

    private fun transform(dom: Document, writer: Writer) {
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n")
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.METHOD, "xml")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.INDENT, "no")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        }
        transformer.transform(DOMSource(dom), StreamResult(writer))
    }

    private val BEAT_PARAGRAPH_SPEC = linkedMapOf(
        "Alignment" to "Left", "FirstIndent" to "0.00", "Leading" to "Regular",
        "LeftIndent" to "0.00", "RightIndent" to "3.06", "SpaceBefore" to "0",
        "Spacing" to "1", "StartsNewPage" to "No",
    )
    private val BEAT_TEXT_SPEC = linkedMapOf(
        "AdornmentStyle" to "0", "Background" to "#FFFFFFFFFFFF", "Color" to "#000000000000",
        "Font" to "Arial", "RevisionID" to "0", "Size" to "12", "Style" to "",
    )
    private val NOTE_PARAGRAPH_SPEC = linkedMapOf(
        "Alignment" to "Left", "FirstIndent" to "0.00", "Leading" to "Regular",
        "LeftIndent" to "0.00", "RightIndent" to "0.00", "SpaceBefore" to "0",
        "Spacing" to "1", "StartsNewPage" to "No",
    )
    private val NOTE_TEXT_SPEC = linkedMapOf(
        "AdornmentStyle" to "0", "Background" to "#FFFFFFFFFFFF", "Color" to "#000000000000",
        "Font" to "Arial", "RevisionID" to "0", "Size" to "12", "Style" to "",
    )
}
