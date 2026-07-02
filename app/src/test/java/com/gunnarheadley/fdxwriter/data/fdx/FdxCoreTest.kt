package com.gunnarheadley.fdxwriter.data.fdx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FdxCoreTest {

    private fun sample(): FdxDocument = FdxDocument.parse(SAMPLE_FDX)

    // ---- Parsing --------------------------------------------------------------------------

    @Test
    fun parsesSample() {
        val doc = sample()
        assertEquals("sample paragraph count", 5, doc.model.paragraphs.size)
    }

    @Test
    fun parsesBeatsAndNotes() {
        val doc = sample()
        assertEquals("sample has one beat", 1, doc.model.beats.size)
        assertEquals("beat title", "Opening", doc.model.beats[0].title)
        assertEquals("sample has one note", 1, doc.model.notes.size)
        assertTrue("note body preserved", doc.model.notes[0].bodyText.contains("office", ignoreCase = true))
    }

    @Test
    fun parsesInlineItalicAndSceneNumbers() {
        val doc = sample()
        val hasItalic = doc.model.paragraphs.any { p -> p.runs.any { it.italic } }
        assertTrue("sample uses an inline Style=\"Italic\" run", hasItalic)
        val hasNumbers = doc.model.paragraphs.any { it.number != null }
        assertTrue("sample has a manual scene Number", hasNumbers)
    }

    @Test
    fun handlesEmptyParagraphs() {
        // The sample ends with an empty <Paragraph Type="Action"/>.
        val doc = sample()
        assertTrue("should contain at least one empty paragraph", doc.model.paragraphs.any { it.runs.isEmpty() })
    }

    // ---- Round-trip -----------------------------------------------------------------------

    @Test
    fun roundTripsSampleLosslessly() {
        val original = sample()
        val xml = original.serializeToString()
        assertTrue("output keeps the XML declaration", xml.startsWith("<?xml"))
        assertTrue("output keeps standalone=\"no\"", xml.contains("standalone=\"no\""))

        val reparsed = FdxDocument.parse(xml)
        assertEquals(
            "paragraph text preserved",
            original.model.paragraphs.map { it.plainText },
            reparsed.model.paragraphs.map { it.plainText },
        )
        assertEquals(
            "paragraph types preserved",
            original.model.paragraphs.map { it.typeName },
            reparsed.model.paragraphs.map { it.typeName },
        )
        assertEquals(
            "scene numbers preserved",
            original.model.paragraphs.map { it.number },
            reparsed.model.paragraphs.map { it.number },
        )
        assertEquals("beat count preserved", original.model.beats.size, reparsed.model.beats.size)
        assertEquals("note count preserved", original.model.notes.size, reparsed.model.notes.size)
        assertTrue("inline italic survives round-trip", reparsed.model.paragraphs.any { p -> p.runs.any { it.italic } })
    }

    @Test
    fun roundTripPreservesBeatPositionsAndNoteRanges() {
        val original = sample()
        val reparsed = FdxDocument.parse(original.serializeToString())
        assertEquals(
            "beat positions preserved",
            original.model.beats.map { listOf(it.id, it.left, it.top, it.width, it.height) },
            reparsed.model.beats.map { listOf(it.id, it.left, it.top, it.width, it.height) },
        )
        assertEquals(
            "note ranges preserved",
            original.model.notes.map { Triple(it.id, it.start, it.end) },
            reparsed.model.notes.map { Triple(it.id, it.start, it.end) },
        )
    }

    @Test
    fun editingOneParagraphLeavesOthersUnchanged() {
        val doc = sample()
        val targetIndex = doc.model.paragraphs.indexOfFirst { it.plainText.isNotBlank() }
        assertTrue(targetIndex >= 0)

        val edited = doc.model.paragraphs.toMutableList()
        edited[targetIndex] = edited[targetIndex].copy(
            runs = listOf(StyledRun("EDITED LINE")),
            dirty = true,
        )
        val out = doc.withModel(doc.model.copy(paragraphs = edited)).serializeToString()
        val reparsed = FdxDocument.parse(out)

        assertEquals("edited paragraph reflects new text", "EDITED LINE", reparsed.model.paragraphs[targetIndex].plainText)
        for (i in doc.model.paragraphs.indices) {
            if (i == targetIndex) continue
            assertEquals(
                "paragraph $i unchanged",
                doc.model.paragraphs[i].plainText,
                reparsed.model.paragraphs[i].plainText,
            )
        }
    }

    @Test
    fun togglingBoldPreservesOtherStyleTokens() {
        val run = StyledRun("hi", style = listOf("AllCaps", "HiddenText"))
        val bolded = run.withStyleToken(StyledRun.BOLD, true)
        assertTrue(bolded.bold)
        assertTrue("AllCaps preserved", "AllCaps" in bolded.style)
        assertTrue("HiddenText preserved", "HiddenText" in bolded.style)
    }

    // ---- Offset model ---------------------------------------------------------------------

    @Test
    fun noteRangesFallWithinTheOffsetSpace() {
        val doc = sample()
        val mapper = doc.offsetMapper()
        for (note in doc.model.notes) {
            assertTrue(
                "note ${note.id} range ${note.start},${note.end} must be within 0..${mapper.totalLength}",
                mapper.isInBounds(note.start, note.end),
            )
        }
    }

    @Test
    fun offsetMappingRoundTrips() {
        val doc = sample()
        val mapper = doc.offsetMapper()
        for (note in doc.model.notes) {
            val pos = mapper.offsetToPosition(note.start)
            val back = mapper.positionToOffset(pos.paragraphIndex, pos.charInParagraph)
            assertEquals("offset ${note.start} should round-trip", note.start, back)
        }
    }

    // ---- Color ----------------------------------------------------------------------------

    @Test
    fun fdxColorConversion() {
        assertEquals(0xFFEB627B.toInt(), FdxColor.toArgb("#EBEB62627B7B"))
        assertEquals("#EBEB62627B7B", FdxColor.fromArgb(0xFFEB627B.toInt()))
        assertEquals(null, FdxColor.toArgb("nonsense"))
    }

    // ---- Reproduce: edit beats/notes, save to bytes, reopen -------------------------------

    @Test
    fun editedBeatsAndNotesRoundTripThroughBytes() {
        val doc = sample()
        val model = doc.model

        val beats = model.beats.toMutableList()
        if (beats.isNotEmpty()) {
            beats[0] = beats[0].copy(title = "Edited Title", bodyLines = listOf("line one", "line two"), dirty = true)
        }
        beats.add(
            Beat(
                id = "beat-repro", title = "New Beat", color = FdxColor.WHITE, bodyLines = emptyList(),
                left = 100, top = 100, width = 520, height = 200, dirty = true,
            ),
        )
        val notes = model.notes.toMutableList()
        if (notes.isNotEmpty()) {
            notes[0] = notes[0].copy(type = "First Pass", body = listOf(listOf(StyledRun("edited note body"))), dirty = true)
        }
        val paras = model.paragraphs.toMutableList()
        val pi = paras.indexOfFirst { it.plainText.isNotBlank() }
        if (pi >= 0) paras[pi] = paras[pi].copy(runs = listOf(StyledRun("EDITED")), dirty = true)

        val edited = doc.withModel(model.copy(paragraphs = paras, beats = beats, notes = notes))

        val bytes = java.io.ByteArrayOutputStream()
        java.io.OutputStreamWriter(bytes, Charsets.UTF_8).use { edited.serialize(it) }
        val out = bytes.toByteArray()

        // Re-open exactly like the device does (raw UTF-8 byte stream).
        FdxDocument.parse(java.io.ByteArrayInputStream(out))
    }

    @Test
    fun recoversFileWithTrailingBytesAfterRoot() {
        // Simulates a non-truncating overwrite: a valid document followed by stale tail bytes.
        val original = sample()
        val good = original.serializeToString()
        val corrupted = good + "StartsNewPage=\"No\">\n        <Text>stale</Text>\n</FinalDraft>\n"

        val recovered = FdxDocument.parse(corrupted)
        assertEquals(
            "all paragraphs recovered despite trailing garbage",
            original.model.paragraphs.size,
            recovered.model.paragraphs.size,
        )
        assertEquals("notes recovered", original.model.notes.size, recovered.model.notes.size)
    }

    // ---- Blank document -------------------------------------------------------------------

    @Test
    fun blankDocumentHasSingleEmptyParagraph() {
        val doc = FdxDocument.blank()
        assertEquals("blank script has one paragraph", 1, doc.model.paragraphs.size)
        assertEquals("blank paragraph is empty", "", doc.model.paragraphs[0].plainText)
        assertTrue("blank has no beats", doc.model.beats.isEmpty())
        assertTrue("blank has no notes", doc.model.notes.isEmpty())
    }

    @Test
    fun blankDocumentRoundTrips() {
        val doc = FdxDocument.blank()
        val xml = doc.serializeToString()
        assertTrue("keeps declaration", xml.startsWith("<?xml"))
        assertTrue("is a Script document", xml.contains("DocumentType=\"Script\""))
        val reparsed = FdxDocument.parse(xml)
        assertEquals(1, reparsed.model.paragraphs.size)
    }

    companion object {
        /**
         * A small synthetic screenplay exercising scene numbers, inline italic, an empty
         * paragraph, one beat (with a board position) and one anchored script note.
         */
        private val SAMPLE_FDX = """
            <?xml version="1.0" encoding="UTF-8" standalone="no" ?>
            <FinalDraft DocumentType="Script" Template="No" Version="5">
              <Content>
                <Paragraph Number="1" Type="Scene Heading">
                  <SceneProperties Length="2/8" Page="1" Title="">
                    <SceneArcBeats/>
                  </SceneProperties>
                  <Text>INT. OFFICE - DAY</Text>
                </Paragraph>
                <Paragraph Type="Action">
                  <Text>The </Text>
                  <Text Style="Italic">quick</Text>
                  <Text> brown fox.</Text>
                </Paragraph>
                <Paragraph Type="Character">
                  <Text>ALICE</Text>
                </Paragraph>
                <Paragraph Type="Dialogue">
                  <Text>Hello there, world.</Text>
                </Paragraph>
                <Paragraph Type="Action"/>
              </Content>
              <ListItems>
                <ListItem Color="#FFFFFFFFFFFF" Id="1" Title="Opening" Type="Beat">
                  <Content>
                    <Paragraph><Text>Beat body line.</Text></Paragraph>
                  </Content>
                </ListItem>
              </ListItems>
              <DisplayBoards>
                <DisplayBoard Height="600" Id="board-1" Type="Beat" Width="800" ZoomLevel="1">
                  <Item Height="200" Id="1" Left="120" Top="140" Width="520"/>
                </DisplayBoard>
              </DisplayBoards>
              <ScriptNotes>
                <ScriptNote Color="#E2E29898DDDD" Id="1" Name="" Range="5,10" Type="First Pass" WriterID="w-1" WriterName="Tester" DateTime="20260101T120000" DateModified="20260101T120000">
                  <Paragraph><Text>A note about the office.</Text></Paragraph>
                </ScriptNote>
              </ScriptNotes>
            </FinalDraft>
        """.trimIndent()
    }
}
