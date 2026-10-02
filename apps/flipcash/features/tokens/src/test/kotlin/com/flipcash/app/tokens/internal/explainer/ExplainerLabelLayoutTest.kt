package com.flipcash.app.tokens.internal.explainer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExplainerLabelLayoutTest {

    @Test
    fun `label layout always draws today and drops labels that come within the gap`() {
        val labels = listOf(
            ExplainerLabelBox(centre = 20f, width = 40f, isToday = false),
            ExplainerLabelBox(centre = 100f, width = 40f, isToday = true),
            ExplainerLabelBox(centre = 130f, width = 40f, isToday = false),
            ExplainerLabelBox(centre = 300f, width = 40f, isToday = false),
            ExplainerLabelBox(centre = 395f, width = 40f, isToday = false),
        )
        val lefts = layoutExplainerLabels(labels, totalWidth = 400f, minGap = 12f)
        assertEquals(0f, lefts[0]) // clamped inside, 0..40 vs today 80..120: gap 40 >= 12
        assertEquals(80f, lefts[1])
        assertNull(lefts[2]) // overlaps Today
        assertEquals(280f, lefts[3])
        assertEquals(360f, lefts[4]) // clamped to the right edge
    }
}
