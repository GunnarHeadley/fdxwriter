package com.gunnarheadley.fdxwriter.data.repo

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
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
import java.security.MessageDigest

/** A content fingerprint of a document on disk (a hash of its bytes), used to detect external modification. */
data class DocumentStamp(val contentHash: String) {
    companion object {
        /**
         * True when the file changed on disk between [opened] (captured at open/last save) and
         * [current]. Content-based rather than timestamp-based, so a cloud provider that re-stamps
         * the modified time after our own save doesn't look like an external edit. Best-effort: if
         * either fingerprint is unknown (null), assume no external change rather than block saving.
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
     * Best-effort content fingerprint for [uri] (a SHA-256 hash of the file's bytes). Returns null
     * if the file can't be read, in which case external-change detection is skipped. Hashing the
     * content (rather than trusting last-modified) means our own saves never look like an external
     * edit even when a cloud provider bumps the modified time afterwards.
     */
    suspend fun stamp(uri: Uri): DocumentStamp? = withContext(Dispatchers.IO) {
        try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
                DocumentStamp(digest.digest().joinToString("") { "%02x".format(it) })
            }
        } catch (e: Exception) {
            null
        }
    }
}
