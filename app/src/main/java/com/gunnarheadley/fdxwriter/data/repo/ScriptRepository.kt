package com.gunnarheadley.fdxwriter.data.repo

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import com.gunnarheadley.fdxwriter.data.fdx.FdxDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

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

    fun displayName(uri: Uri): String? = try {
        appContext.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (e: Exception) {
        null
    }
}
