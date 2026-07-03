package com.gunnarheadley.fdxwriter.data.fdx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ElementTypeTest {

    @Test
    fun columnWidthMatchesScreenplayLayout() {
        assertEquals(35, ElementType.DIALOGUE.columnWidth)
        assertEquals(25, ElementType.PARENTHETICAL.columnWidth)
        assertEquals(38, ElementType.CHARACTER.columnWidth)
        assertEquals(60, ElementType.ACTION.columnWidth)
        assertEquals(60, ElementType.SCENE_HEADING.columnWidth)
        assertEquals(60, ElementType.TRANSITION.columnWidth)
    }

    @Test
    fun fromFdxResolvesKnownNamesAndNullsUnknown() {
        assertEquals(ElementType.SCENE_HEADING, ElementType.fromFdx("Scene Heading"))
        assertEquals(ElementType.CHARACTER, ElementType.fromFdx("Character"))
        assertNull(ElementType.fromFdx("Not A Real Type"))
        assertNull(ElementType.fromFdx(null))
    }

    @Test
    fun returnKeyBehaviorFollowsFinalDraftDefaults() {
        assertEquals(ElementType.ACTION, ElementType.SCENE_HEADING.returnType)
        assertEquals(ElementType.DIALOGUE, ElementType.CHARACTER.returnType)
        assertEquals(ElementType.ACTION, ElementType.DIALOGUE.returnType)
        assertEquals(ElementType.SCENE_HEADING, ElementType.TRANSITION.returnType)
    }
}
