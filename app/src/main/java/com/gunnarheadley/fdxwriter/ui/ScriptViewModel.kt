package com.gunnarheadley.fdxwriter.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gunnarheadley.fdxwriter.data.fdx.Beat
import com.gunnarheadley.fdxwriter.data.fdx.ElementType
import com.gunnarheadley.fdxwriter.data.fdx.FdxColor
import com.gunnarheadley.fdxwriter.data.fdx.FdxDocument
import com.gunnarheadley.fdxwriter.data.fdx.NoteAnchors
import com.gunnarheadley.fdxwriter.data.fdx.NoteAnnotation
import com.gunnarheadley.fdxwriter.data.fdx.ScreenplayParagraph
import com.gunnarheadley.fdxwriter.data.fdx.ScriptModel
import com.gunnarheadley.fdxwriter.data.fdx.StyledRun
import com.gunnarheadley.fdxwriter.data.fdx.TextEdits
import com.gunnarheadley.fdxwriter.data.repo.AppSettings
import com.gunnarheadley.fdxwriter.data.repo.RecentFile
import com.gunnarheadley.fdxwriter.data.repo.RecentFilesStore
import com.gunnarheadley.fdxwriter.data.repo.ScriptRepository
import com.gunnarheadley.fdxwriter.data.repo.SettingsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val MAX_UNDO = 50

/** A request for the editor to scroll to [paragraphIndex]; [token] makes repeat reveals distinct. */
data class RevealRequest(val paragraphIndex: Int, val token: Long)

/** Tells the editor to focus paragraph [key] and place the caret at [caret]. */
data class FocusTarget(val key: String, val caret: Int)

/** A search hit: paragraph [index]/[key] and the local character range [start, end). */
data class SearchMatch(val index: Int, val key: String, val start: Int, val end: Int)

/** Find/replace UI state. [current] indexes into [matches]. */
data class SearchUiState(
    val active: Boolean = false,
    val query: String = "",
    val matches: List<SearchMatch> = emptyList(),
    val current: Int = -1,
)

/** Observable state of the currently open script. */
data class ScriptUiState(
    val document: FdxDocument? = null,
    val fileName: String? = null,
    val uri: Uri? = null,
    val isLoading: Boolean = false,
    val isDirty: Boolean = false,
    val errorMessage: String? = null,
    val savedAt: Long? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
) {
    val isOpen: Boolean get() = document != null
    val paragraphCount: Int get() = document?.model?.paragraphs?.size ?: 0
    val beatCount: Int get() = document?.model?.beats?.size ?: 0
    val noteCount: Int get() = document?.model?.notes?.size ?: 0
}

/** Owns the open FDX document and drives load/save through [ScriptRepository]. */
class ScriptViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScriptRepository(application)
    private val recentFilesStore = RecentFilesStore(application)
    private val settingsStore = SettingsStore(application)

    private val _uiState = MutableStateFlow(ScriptUiState())
    val uiState: StateFlow<ScriptUiState> = _uiState.asStateFlow()

    val recentFiles: StateFlow<List<RecentFile>> = recentFilesStore.recentFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** The paragraph the editor caret last sat in, used to anchor new notes. */
    private var focusedParagraphKey: String? = null
    private var focusedSelStart = 0
    private var focusedSelEnd = 0

    fun setFocusedParagraph(key: String) {
        focusedParagraphKey = key
    }

    /** Report the caret/selection within the focused paragraph (used to add/remove notes). */
    fun setFocusedSelection(key: String, start: Int, end: Int) {
        focusedParagraphKey = key
        focusedSelStart = minOf(start, end)
        focusedSelEnd = maxOf(start, end)
    }

    private val _revealRequest = MutableStateFlow<RevealRequest?>(null)
    val revealRequest: StateFlow<RevealRequest?> = _revealRequest.asStateFlow()

    /** Ask the editor to scroll to the paragraph a note is anchored to. */
    fun revealNote(note: NoteAnnotation) {
        val doc = _uiState.value.document ?: return
        val index = doc.offsetMapper().offsetToPosition(note.start).paragraphIndex
        _revealRequest.value = RevealRequest(index, System.nanoTime())
    }

    fun consumeReveal() {
        _revealRequest.value = null
    }

    // ---- Find / Replace ------------------------------------------------------------------

    private val _search = MutableStateFlow(SearchUiState())
    val search: StateFlow<SearchUiState> = _search.asStateFlow()

    fun openSearch() { _search.value = _search.value.copy(active = true) }

    fun closeSearch() { _search.value = SearchUiState() }

    fun setSearchQuery(query: String) {
        _search.value = _search.value.copy(active = true, query = query)
        recomputeMatches(resetCurrent = true)
    }

    fun nextMatch() {
        val s = _search.value
        if (s.matches.isNotEmpty()) _search.value = s.copy(current = (s.current + 1).mod(s.matches.size))
    }

    fun prevMatch() {
        val s = _search.value
        if (s.matches.isNotEmpty()) _search.value = s.copy(current = (s.current - 1).mod(s.matches.size))
    }

    private fun recomputeMatches(resetCurrent: Boolean) {
        val s = _search.value
        val doc = _uiState.value.document
        if (!s.active || s.query.isEmpty() || doc == null) {
            _search.value = s.copy(matches = emptyList(), current = -1)
            return
        }
        val found = ArrayList<SearchMatch>()
        doc.model.paragraphs.forEachIndexed { i, p ->
            for (r in TextEdits.findRanges(p.plainText, s.query)) {
                found.add(SearchMatch(i, p.key, r.first, r.last + 1))
            }
        }
        val current = when {
            found.isEmpty() -> -1
            resetCurrent -> 0
            else -> s.current.coerceIn(0, found.size - 1)
        }
        _search.value = _search.value.copy(matches = found, current = current)
    }

    fun replaceCurrent(replacement: String) {
        val s = _search.value
        val match = s.matches.getOrNull(s.current) ?: return
        val doc = _uiState.value.document ?: return
        val list = doc.model.paragraphs.toMutableList()
        val i = list.indexOfFirst { it.key == match.key }.takeIf { it >= 0 } ?: return
        list[i] = list[i].copy(runs = TextEdits.spliceRuns(list[i].runs, match.start, match.end, replacement), dirty = true)
        commit(doc, list)
        recomputeMatches(resetCurrent = false)
    }

    fun replaceAll(replacement: String) {
        val s = _search.value
        if (s.query.isEmpty()) return
        val doc = _uiState.value.document ?: return
        val q = s.query
        val list = doc.model.paragraphs.toMutableList()
        var changed = false
        for (i in list.indices) {
            val ranges = TextEdits.findRanges(list[i].plainText, q)
            if (ranges.isEmpty()) continue
            var runs = list[i].runs
            for (r in ranges.asReversed()) runs = TextEdits.spliceRuns(runs, r.first, r.last + 1, replacement)
            list[i] = list[i].copy(runs = runs, dirty = true)
            changed = true
        }
        if (changed) {
            commit(doc, list)
            recomputeMatches(resetCurrent = true)
        }
    }

    private val undoStack = ArrayDeque<ScriptModel>()
    private val redoStack = ArrayDeque<ScriptModel>()
    private var coalesceKey: String? = null

    /** The model as last written to (or read from) disk, used to detect zero net change. */
    private var savedModel: ScriptModel? = null

    init {
        // Auto-save loop: when enabled, persist a dirty document once per configured interval.
        viewModelScope.launch {
            while (true) {
                val intervalSeconds = settings.value.autoSaveIntervalSeconds
                    .coerceAtLeast(SettingsStore.MIN_INTERVAL)
                delay(intervalSeconds.toLong() * 1000)
                val state = _uiState.value
                if (settings.value.autoSaveEnabled && state.isDirty && state.uri != null && !state.isLoading) {
                    save()
                }
            }
        }
    }

    fun setNoteAuthor(author: String) {
        viewModelScope.launch { settingsStore.setNoteAuthor(author) }
    }

    fun setAutoSaveEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoSaveEnabled(enabled) }
    }

    fun setAutoSaveIntervalSeconds(seconds: Int) {
        viewModelScope.launch { settingsStore.setAutoSaveIntervalSeconds(seconds) }
    }

    fun undo() {
        val doc = _uiState.value.document ?: return
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(doc.model)
        coalesceKey = null
        _uiState.value = _uiState.value.copy(
            document = doc.withModel(previous),
            isDirty = previous != savedModel,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )
    }

    fun redo() {
        val doc = _uiState.value.document ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(doc.model)
        coalesceKey = null
        _uiState.value = _uiState.value.copy(
            document = doc.withModel(next),
            isDirty = next != savedModel,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )
    }

    private fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
        coalesceKey = null
        savedModel = null
        _search.value = SearchUiState()
    }

    fun open(uri: Uri, persistPermission: Boolean = true) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                if (persistPermission) takePersistablePermission(uri)
                val document = repository.load(uri)
                val name = repository.displayName(uri) ?: uri.lastPathSegment ?: "script.fdx"
                clearHistory()
                savedModel = document.model
                _uiState.value = ScriptUiState(document = document, fileName = name, uri = uri)
                recentFilesStore.add(uri.toString(), name)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Couldn't open this file. Is it a valid .fdx?",
                )
            }
        }
    }

    fun save() {
        val state = _uiState.value
        val uri = state.uri ?: return
        val document = state.document ?: return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, errorMessage = null)
            try {
                repository.save(uri, document)
                savedModel = document.model
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isDirty = _uiState.value.document?.model != savedModel,
                    savedAt = System.currentTimeMillis(),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message ?: "Couldn't save the file.")
            }
        }
    }

    /** Create a brand-new empty script at [uri], write it, and open it in the editor. */
    fun createNew(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                takePersistablePermission(uri)
                val document = FdxDocument.blank()
                repository.save(uri, document)
                val name = repository.displayName(uri) ?: uri.lastPathSegment ?: "script.fdx"
                clearHistory()
                savedModel = document.model
                _uiState.value = ScriptUiState(document = document, fileName = name, uri = uri)
                recentFilesStore.add(uri.toString(), name)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Couldn't create the file.",
                )
            }
        }
    }

    fun saveAs(uri: Uri) {
        val document = _uiState.value.document ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                takePersistablePermission(uri)
                repository.save(uri, document)
                savedModel = document.model
                val name = repository.displayName(uri) ?: uri.lastPathSegment ?: "script.fdx"
                _uiState.value = _uiState.value.copy(
                    uri = uri, fileName = name, isLoading = false,
                    isDirty = _uiState.value.document?.model != savedModel,
                    savedAt = System.currentTimeMillis(),
                )
                recentFilesStore.add(uri.toString(), name)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message ?: "Couldn't save the file.")
            }
        }
    }

    fun close() {
        clearHistory()
        _uiState.value = ScriptUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun removeRecent(uri: String) {
        viewModelScope.launch { recentFilesStore.remove(uri) }
    }

    // ---- Editing -------------------------------------------------------------------------

    fun updateParagraphRuns(key: String, runs: List<StyledRun>) {
        val doc = _uiState.value.document ?: return
        val list = doc.model.paragraphs.toMutableList()
        val i = list.indexOfFirst { it.key == key }
        if (i < 0) return
        // Coalesce consecutive keystrokes in the same paragraph into a single undo step.
        val snapshot = coalesceKey != key
        list[i] = list[i].copy(runs = runs, dirty = true)
        commit(doc, list, snapshot)
        coalesceKey = key
    }

    fun setParagraphType(key: String, type: ElementType) = mutateParagraphs { list ->
        val i = list.indexOfFirst { it.key == key }
        if (i >= 0) list[i] = list[i].copy(typeName = type.fdxName, dirty = true)
    }

    fun deleteParagraph(key: String) = mutateParagraphs { list ->
        val i = list.indexOfFirst { it.key == key }
        if (i >= 0 && list.size > 1) list.removeAt(i)
    }

    /**
     * Merge paragraph [key] into the previous one (Backspace at the start of a line). Returns the
     * previous paragraph's key and the caret offset at the join point, or null if there's no
     * previous paragraph to merge into.
     */
    fun mergeWithPrevious(key: String): FocusTarget? {
        val doc = _uiState.value.document ?: return null
        val list = doc.model.paragraphs.toMutableList()
        val i = list.indexOfFirst { it.key == key }
        if (i <= 0) return null
        val prev = list[i - 1]
        val cur = list[i]
        val caret = prev.plainText.length
        list[i - 1] = prev.copy(runs = prev.runs + cur.runs, dirty = true)
        list.removeAt(i)
        commit(doc, list)
        return FocusTarget(prev.key, caret)
    }

    /** Split [key] into [before] (kept) and a new paragraph holding [after]. Returns the new key. */
    fun splitParagraph(key: String, before: List<StyledRun>, after: List<StyledRun>): String? {
        val doc = _uiState.value.document ?: return null
        val list = doc.model.paragraphs.toMutableList()
        val i = list.indexOfFirst { it.key == key }
        if (i < 0) return null
        val current = list[i]
        val newKey = "new-" + UUID.randomUUID().toString()
        list[i] = current.copy(runs = before, dirty = true)
        list.add(
            i + 1,
            ScreenplayParagraph(key = newKey, typeName = current.type.returnType.fdxName, runs = after, dirty = true),
        )
        commit(doc, list)
        return newKey
    }

    fun addParagraphAfter(key: String): String? {
        val doc = _uiState.value.document ?: return null
        val list = doc.model.paragraphs.toMutableList()
        val i = list.indexOfFirst { it.key == key }
        if (i < 0) return null
        val newKey = "new-" + UUID.randomUUID().toString()
        list.add(
            i + 1,
            ScreenplayParagraph(key = newKey, typeName = list[i].type.returnType.fdxName, runs = emptyList(), dirty = true),
        )
        commit(doc, list)
        return newKey
    }

    // ---- Beat board ----------------------------------------------------------------------

    fun moveBeat(id: String, left: Int, top: Int) = mutateBeats { list ->
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) list[i] = list[i].copy(left = left, top = top, dirty = true)
    }

    fun updateBeat(id: String, title: String, bodyLines: List<String>, color: String) = mutateBeats { list ->
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) list[i] = list[i].copy(title = title, bodyLines = bodyLines, color = color, dirty = true)
    }

    fun deleteBeat(id: String) = mutateBeats { list ->
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) list.removeAt(i)
    }

    /** Create a beat near the centroid of the existing cards. Returns its id. */
    fun addBeat(): String? {
        val doc = _uiState.value.document ?: return null
        val list = doc.model.beats.toMutableList()
        val id = "beat-" + UUID.randomUUID().toString()
        val left = if (list.isEmpty()) 200 else list.sumOf { it.left } / list.size
        val top = if (list.isEmpty()) 200 else list.sumOf { it.top } / list.size
        list.add(
            Beat(
                id = id, title = "New Beat", color = FdxColor.WHITE, bodyLines = emptyList(),
                left = left, top = top, width = 520, height = 200, dirty = true,
            ),
        )
        commitBeats(doc, list)
        return id
    }

    private fun mutateBeats(block: (MutableList<Beat>) -> Unit) {
        val doc = _uiState.value.document ?: return
        val list = doc.model.beats.toMutableList()
        block(list)
        commitBeats(doc, list)
    }

    private fun commitBeats(doc: FdxDocument, beats: List<Beat>) {
        applyModel(doc, doc.model.copy(beats = beats), snapshot = true)
    }

    // ---- Notes ---------------------------------------------------------------------------

    /** Add a note anchored to the start of the last-focused paragraph. Returns its id. */
    fun addNote(): String? {
        val doc = _uiState.value.document ?: return null
        val index = doc.model.paragraphs.indexOfFirst { it.key == focusedParagraphKey }.takeIf { it >= 0 } ?: 0
        val offset = doc.offsetMapper().positionToOffset(index, 0)
        return createNote(doc, offset, offset)
    }

    /** Add a note spanning the current selection in the focused paragraph. Returns its id. */
    fun addNoteForSelection(): String? {
        val doc = _uiState.value.document ?: return null
        val index = doc.model.paragraphs.indexOfFirst { it.key == focusedParagraphKey }.takeIf { it >= 0 }
            ?: return addNote()
        val mapper = doc.offsetMapper()
        val start = mapper.positionToOffset(index, focusedSelStart)
        val end = mapper.positionToOffset(index, focusedSelEnd)
        return createNote(doc, start, end)
    }

    /** The note overlapping the current focused selection, if any. */
    fun noteAtSelection(): NoteAnnotation? {
        val doc = _uiState.value.document ?: return null
        val index = doc.model.paragraphs.indexOfFirst { it.key == focusedParagraphKey }.takeIf { it >= 0 }
            ?: return null
        val mapper = doc.offsetMapper()
        val a = mapper.positionToOffset(index, focusedSelStart)
        val b = mapper.positionToOffset(index, focusedSelEnd)
        return doc.model.notes.firstOrNull { overlaps(a, b, it.start, it.end) }
    }

    /** Delete the note overlapping the current selection. Returns true if one was removed. */
    fun removeNoteAtSelection(): Boolean {
        val target = noteAtSelection() ?: return false
        deleteNote(target.id)
        return true
    }

    private fun overlaps(a: Int, b: Int, s: Int, e: Int): Boolean {
        val lo = minOf(a, b)
        val hi = maxOf(a, b)
        return if (lo == hi) lo in s..e else lo < e && s < hi
    }

    private fun createNote(doc: FdxDocument, start: Int, end: Int): String {
        val notes = doc.model.notes.toMutableList()
        val newId = ((notes.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: 0) + 1).toString()
        val now = noteTimestamp()
        notes.add(
            NoteAnnotation(
                id = newId,
                start = minOf(start, end), end = maxOf(start, end),
                type = "",
                color = "#E2E29898DDDD", // Final Draft's default note color
                writerName = settings.value.noteAuthor,
                writerId = UUID.randomUUID().toString(),
                dateTime = now, dateModified = now,
                name = "",
                body = listOf(emptyList()),
                dirty = true,
            ),
        )
        commitNotes(doc, notes)
        return newId
    }

    fun updateNote(id: String, bodyText: String, type: String, color: String) = mutateNotes { list ->
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) {
            val body = bodyText.split("\n").map { line ->
                if (line.isEmpty()) emptyList() else listOf(StyledRun(line))
            }
            list[i] = list[i].copy(
                body = body, type = type, color = color,
                dateModified = noteTimestamp(), dirty = true,
            )
        }
    }

    fun deleteNote(id: String) = mutateNotes { list ->
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) list.removeAt(i)
    }

    private fun mutateNotes(block: (MutableList<NoteAnnotation>) -> Unit) {
        val doc = _uiState.value.document ?: return
        val list = doc.model.notes.toMutableList()
        block(list)
        commitNotes(doc, list)
    }

    private fun commitNotes(doc: FdxDocument, notes: List<NoteAnnotation>) {
        applyModel(doc, doc.model.copy(notes = notes), snapshot = true)
    }

    private fun noteTimestamp(): String =
        SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).format(Date())

    private fun mutateParagraphs(block: (MutableList<ScreenplayParagraph>) -> Unit) {
        val doc = _uiState.value.document ?: return
        val list = doc.model.paragraphs.toMutableList()
        block(list)
        commit(doc, list)
    }

    private fun commit(doc: FdxDocument, paragraphs: List<ScreenplayParagraph>, snapshot: Boolean = true) {
        applyModel(doc, doc.model.copy(paragraphs = paragraphs), snapshot)
    }

    private fun applyModel(doc: FdxDocument, newModel: ScriptModel, snapshot: Boolean) {
        // Keep note anchors aligned when the edit changed paragraph text length or structure.
        val adjustedNotes = NoteAnchors.adjust(doc.model.paragraphs, newModel.paragraphs, newModel.notes)
        val model = if (adjustedNotes === newModel.notes) newModel else newModel.copy(notes = adjustedNotes)
        if (snapshot) {
            undoStack.addLast(doc.model)
            while (undoStack.size > MAX_UNDO) undoStack.removeFirst()
            redoStack.clear()
        }
        coalesceKey = null
        _uiState.value = _uiState.value.copy(
            document = doc.withModel(model),
            isDirty = model != savedModel,
            canUndo = undoStack.isNotEmpty(),
            canRedo = redoStack.isNotEmpty(),
        )
        if (_search.value.active) recomputeMatches(resetCurrent = false)
    }

    private fun takePersistablePermission(uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            // Some providers don't grant persistable write access; the file still opens for this session.
        }
    }
}
