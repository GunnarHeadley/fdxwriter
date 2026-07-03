package com.gunnarheadley.fdxwriter.data.repo

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import com.gunnarheadley.fdxwriter.data.export.PdfExporter
import com.gunnarheadley.fdxwriter.data.fdx.FdxDocument
import com.gunnarheadley.fdxwriter.data.fdx.ScreenplayParagraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

/** A lightweight fingerprint of a document on disk, used to detect external modification. */
data class DocumentStamp(val lastModified: Long, val size: Long) {
    companion object {
        /**
         * True when the file changed on disk between [opened] (captured at open/last save) and
         * [current]. Best-effort: if either stamp is unknown (null), assume no external change
         * rather than block saving.
         */
        fun isExternallyModified(opened: DocumentStamp?, current: DocumentStamp?): Boolean =
            opened != null && current != null && current != opened
    }
}

/** Loads and saves FDX documents through the Storage Access Framework (content URIs). */
class ScriptRepository(context: Context) {

    private val appContext = context.applicationContext

    suspend fun load(uri: Uri): FdxDocument = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            FdxDocument.parse(input)
        } ?: throw IOException("Unable to open document.")
    }

    suspend fun save(uri: Uri, document: FdxDocument): Unit = withContext(Dispatchers.IO) {
        // Serialize to memory first so a serialization failure can't leave a half-written file.
        val bytes = ByteArrayOutputStream().use { buffer ->
            OutputStreamWriter(buffer, Charsets.UTF_8).use { document.serialize(it) }
            buffer.toByteArray()
        }
        // Many SAF providers silently ignore the "t" (truncate) flag, which leaves stale trailing
        // bytes when the new content is shorter than the old file. Truncate the descriptor
        // explicitly both before and after writing to guarantee no stale tail remains.
        val pfd = appContext.contentResolver.openFileDescriptor(uri, "wt")
            ?: throw IOException("Unable to write document.")
        val fd = pfd.fileDescriptor
        try {
            Os.ftruncate(fd, 0)
        } catch (e: ErrnoException) {
            // Non-seekable provider (e.g. a pipe); the "t" mode is the fallback for those.
        }
        ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { output ->
            output.write(bytes)
            output.flush()
            try {
                Os.ftruncate(fd, bytes.size.toLong())
            } catch (e: ErrnoException) {
                // Non-seekable provider; nothing more we can do.
            }
        }
    }

    suspend fun exportPdf(uri: Uri, paragraphs: List<ScreenplayParagraph>, title: String): Unit =
        withContext(Dispatchers.IO) {
            appContext.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                PdfExporter.export(paragraphs, out, title)
            } ?: throw IOException("Unable to create the PDF.")
        }

    fun displayName(uri: Uri): String? = try {
        appContext.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    /**
     * Best-effort last-modified + size fingerprint for [uri]. Returns null if the provider
     * doesn't report either (some do not), in which case external-change detection is skipped.
     */
    fun stamp(uri: Uri): DocumentStamp? = try {
        appContext.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val lastModified = if (cursor.isNull(0)) 0L else cursor.getLong(0)
                val size = if (cursor.isNull(1)) -1L else cursor.getLong(1)
                if (lastModified <= 0L && size < 0L) null else DocumentStamp(lastModified, size)
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }
}
