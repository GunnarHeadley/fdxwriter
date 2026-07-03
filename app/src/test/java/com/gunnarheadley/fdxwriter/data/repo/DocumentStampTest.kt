package com.gunnarheadley.fdxwriter.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentStampTest {

    private val base = DocumentStamp(lastModified = 1000L, size = 500L)

    @Test
    fun unchangedWhenStampsAreEqual() {
        assertFalse(DocumentStamp.isExternallyModified(base, DocumentStamp(1000L, 500L)))
    }

    @Test
    fun changedWhenLastModifiedDiffers() {
        assertTrue(DocumentStamp.isExternallyModified(base, DocumentStamp(2000L, 500L)))
    }

    @Test
    fun changedWhenSizeDiffers() {
        assertTrue(DocumentStamp.isExternallyModified(base, DocumentStamp(1000L, 999L)))
    }

    @Test
    fun assumesUnchangedWhenEitherStampIsUnknown() {
        assertFalse(DocumentStamp.isExternallyModified(null, base))
        assertFalse(DocumentStamp.isExternallyModified(base, null))
        assertFalse(DocumentStamp.isExternallyModified(null, null))
    }
}
