package com.example.fdxwriter.ui

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Like [ActivityResultContracts.OpenDocument], but also requests writable and persistable access
 * so the app can save edits back to the same file across launches.
 */
class OpenFdxDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
    }
}

/** MIME filter for the open picker. FDX has no registered type, so allow everything. */
val FDX_OPEN_MIME_TYPES = arrayOf("*/*")

/** MIME used when creating a new document via Save As. */
const val FDX_CREATE_MIME = "application/octet-stream"
