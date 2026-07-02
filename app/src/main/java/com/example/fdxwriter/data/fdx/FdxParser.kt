package com.example.fdxwriter.data.fdx

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/** Reads an FDX (Final Draft XML) file into an [FdxDocument]. */
object FdxParser {

    fun parse(input: InputStream): FdxDocument {
        val doc = newSecureBuilder().parse(ByteArrayInputStream(sanitize(input.readBytes())))
        val root = doc.documentElement
            ?: error("Not an FDX file: missing root element")
        require(root.tagName == "FinalDraft") { "Not an FDX file: root is <${root.tagName}>" }

        val originalParagraphNodes = LinkedHashMap<String, Element>()
        val scenePropsByKey = HashMap<String, Element>()
        val paragraphs = parseParagraphs(root, originalParagraphNodes, scenePropsByKey)

        val originalBeatNodes = LinkedHashMap<String, Element>()
        val beats = parseBeats(root, originalBeatNodes)

        val originalNoteNodes = LinkedHashMap<String, Element>()
        val notes = parseNotes(root, originalNoteNodes)

        return FdxDocument(
            dom = doc,
            model = ScriptModel(paragraphs, beats, notes),
            originalParagraphNodes = originalParagraphNodes,
            scenePropsByKey = scenePropsByKey,
            originalBeatNodes = originalBeatNodes,
            originalNoteNodes = originalNoteNodes,
        )
    }

    private fun parseParagraphs(
        root: Element,
        originalNodes: MutableMap<String, Element>,
        sceneProps: MutableMap<String, Element>,
    ): List<ScreenplayParagraph> {
        val content = root.firstChild("Content") ?: return emptyList()
        val result = ArrayList<ScreenplayParagraph>()
        var index = 0
        for (p in content.childElements("Paragraph")) {
            val key = "p$index"
            index++
            val attrs = p.attributesMap()
            val typeName = attrs["Type"] ?: ElementType.GENERAL.fdxName
            val number = attrs["Number"]
            val extra = attrs.filterKeys { it != "Type" && it != "Number" }

            var hasScene = false
            val runs = ArrayList<StyledRun>()
            for (child in p.childElements()) {
                when (child.tagName) {
                    "Text" -> runs.add(parseTextRun(child))
                    "SceneProperties" -> {
                        hasScene = true
                        sceneProps[key] = child.cloneNode(true) as Element
                    }
                }
            }
            originalNodes[key] = p.cloneNode(true) as Element
            result.add(
                ScreenplayParagraph(
                    key = key,
                    typeName = typeName,
                    runs = runs,
                    number = number,
                    extraAttributes = extra,
                    hasSceneProperties = hasScene,
                    dirty = false,
                ),
            )
        }
        return result
    }

    private fun parseTextRun(e: Element): StyledRun {
        val attrs = e.attributesMap()
        val style = StyledRun.parseStyle(attrs["Style"])
        val other = attrs.filterKeys { it != "Style" }
        return StyledRun(text = e.textContent ?: "", style = style, attributes = other)
    }

    private fun parseBeats(root: Element, originalNodes: MutableMap<String, Element>): List<Beat> {
        val listItems = root.firstChild("ListItems") ?: return emptyList()

        // Positions live on the Beat-type DisplayBoard.
        val positions = HashMap<String, IntArray>()
        root.firstChild("DisplayBoards")
            ?.childElements("DisplayBoard")
            ?.firstOrNull { it.getAttribute("Type") == "Beat" }
            ?.let { board ->
                for (item in board.childElements("Item")) {
                    positions[item.getAttribute("Id")] = intArrayOf(
                        item.getAttribute("Left").toIntOrNull() ?: 0,
                        item.getAttribute("Top").toIntOrNull() ?: 0,
                        item.getAttribute("Width").toIntOrNull() ?: 0,
                        item.getAttribute("Height").toIntOrNull() ?: 0,
                    )
                }
            }

        val result = ArrayList<Beat>()
        for (li in listItems.childElements("ListItem")) {
            val id = li.getAttribute("Id")
            val bodyLines = li.firstChild("Content")
                ?.childElements("Paragraph")
                ?.map { it.concatTextRuns() }
                ?: emptyList()
            val pos = positions[id] ?: intArrayOf(0, 0, 0, 0)
            originalNodes[id] = li.cloneNode(true) as Element
            result.add(
                Beat(
                    id = id,
                    title = li.getAttribute("Title"),
                    color = li.getAttribute("Color").ifEmpty { FdxColor.WHITE },
                    bodyLines = bodyLines,
                    left = pos[0], top = pos[1], width = pos[2], height = pos[3],
                    dirty = false,
                ),
            )
        }
        return result
    }

    private fun parseNotes(root: Element, originalNodes: MutableMap<String, Element>): List<NoteAnnotation> {
        val scriptNotes = root.firstChild("ScriptNotes") ?: return emptyList()
        val result = ArrayList<NoteAnnotation>()
        for (note in scriptNotes.childElements("ScriptNote")) {
            val attrs = note.attributesMap()
            val (start, end) = parseRange(attrs["Range"])
            val body = note.childElements("Paragraph").map { para ->
                para.childElements("Text").map { parseTextRun(it) }
            }
            val known = setOf("Range", "Id", "Type", "Color", "WriterName", "WriterID", "DateTime", "DateModified", "Name")
            val id = attrs["Id"] ?: continue
            originalNodes[id] = note.cloneNode(true) as Element
            result.add(
                NoteAnnotation(
                    id = id,
                    start = start, end = end,
                    type = attrs["Type"] ?: "",
                    color = attrs["Color"] ?: FdxColor.WHITE,
                    writerName = attrs["WriterName"] ?: "",
                    writerId = attrs["WriterID"] ?: "",
                    dateTime = attrs["DateTime"] ?: "",
                    dateModified = attrs["DateModified"] ?: "",
                    name = attrs["Name"] ?: "",
                    body = body,
                    extraAttributes = attrs.filterKeys { it !in known },
                    dirty = false,
                ),
            )
        }
        return result
    }

    private fun parseRange(value: String?): Pair<Int, Int> {
        val parts = value?.split(',') ?: return 0 to 0
        val start = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
        val end = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: start
        return start to end
    }

    private fun newSecureBuilder() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isExpandEntityReferences = false
        trySetFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        trySetFeature("http://xml.org/sax/features/external-general-entities", false)
        trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
    }.newDocumentBuilder()

    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        try {
            setFeature(name, value)
        } catch (_: Exception) {
            // Not all parsers support every feature; secure-by-default where possible.
        }
    }

    /**
     * Drop any bytes after the root `</FinalDraft>` close tag. An earlier save path could leave
     * stale trailing bytes when overwriting a longer file on a provider that ignored truncation;
     * trimming here lets those files still open. Harmless for well-formed files (the only thing
     * after the close tag is insignificant trailing whitespace).
     */
    private fun sanitize(bytes: ByteArray): ByteArray {
        val marker = "</FinalDraft>".toByteArray(Charsets.UTF_8)
        val start = indexOf(bytes, marker)
        if (start < 0) return bytes
        val end = start + marker.size
        return if (end < bytes.size) bytes.copyOf(end) else bytes
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }
}

/** Convenience for tests/readers that already hold the XML as a string. */
fun parseFdx(xml: String): Document = FdxDocument.parse(xml).dom
