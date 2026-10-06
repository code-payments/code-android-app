package com.getcode.ui.components.chat

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposerStackingTest {
    @Test
    fun fitsInline() {
        assertFalse(composerStacks("Hi", wasStacked = false, draftLineWidth = 40, inlineWidth = 200))
    }

    @Test
    fun wrapStacks() {
        assertTrue(composerStacks("a long draft", wasStacked = false, draftLineWidth = 260, inlineWidth = 200))
    }

    @Test
    fun newlineStacks() {
        assertTrue(composerStacks("Hi\n", wasStacked = false, draftLineWidth = 20, inlineWidth = 200))
    }

    @Test
    fun staysStacked() {
        // Deleting back under one line does not unstack.
        assertTrue(composerStacks("Hi", wasStacked = true, draftLineWidth = 20, inlineWidth = 200))
    }

    @Test
    fun clearUnstacks() {
        assertFalse(composerStacks("", wasStacked = true, draftLineWidth = 0, inlineWidth = 200))
    }

    @Test
    fun unmeasured() {
        // Before the field has a width only a newline stacks.
        assertFalse(composerStacks("a long draft", wasStacked = false, draftLineWidth = 260, inlineWidth = 0))
        assertTrue(composerStacks("a\nb", wasStacked = false, draftLineWidth = 20, inlineWidth = 0))
    }
}

class ComposerCornerTest {
    // The shipped geometry on the static grid: chip corner 10, header inset 10, field corner 30.
    private fun radius(height: Float, hasHeader: Boolean) = composerCornerRadius(
        height = height,
        hasHeader = hasHeader,
        headerInnerCorner = 10f,
        headerInset = 10f,
        stackedCorner = 30f,
    )

    @kotlin.test.Test
    fun concentricIsInnerPlusInset() {
        kotlin.test.assertEquals(20f, concentricRadius(10f, 10f))
    }

    @kotlin.test.Test
    fun aroundChipsItIsChipPlusInset() {
        kotlin.test.assertEquals(20f, radius(height = 120f, hasHeader = true))
    }

    @kotlin.test.Test
    fun oneLineIsACapsule() {
        kotlin.test.assertEquals(25f, radius(height = 50f, hasHeader = false))
    }

    @kotlin.test.Test
    fun stackedTextUsesTheFieldCorner() {
        kotlin.test.assertEquals(30f, radius(height = 90f, hasHeader = false))
    }

    @kotlin.test.Test
    fun cornerGrowsWithHeightThroughTheTransition() {
        kotlin.test.assertTrue(radius(60f, false) in 25f..30f)
    }

    @kotlin.test.Test
    fun oneLineFieldIsAsTallAsTheOutsideSlot() {
        // Shipped: padding 10 + accessory 30 + padding 10 = the 50 the `$` button is.
        kotlin.test.assertEquals(50.dp, oneLineFieldHeight(fieldPad = 10.dp, accessorySize = 30.dp))
    }
}
