package com.gunnarheadley.fdxwriter.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentStampTest {

    private val base = DocumentStamp(contentHash = "hash-A")

    @Test
    fun unchangedWhenStampsAreEqual() {
        assertFalse(DocumentStamp.isExternallyModified(base, DocumentStamp("hash-A")))
    }

    @Test
    fun changedWhenContentHashDiffers() {
        assertTrue(DocumentStamp.isExternallyModified(base, DocumentStamp("hash-B")))
    }

    @Test
    fun assumesUnchangedWhenEitherStampIsUnknown() {
        assertFalse(DocumentStamp.isExternallyModified(null, base))
        assertFalse(DocumentStamp.isExternallyModified(base, null))
        assertFalse(DocumentStamp.isExternallyModified(null, null))
    }
}
