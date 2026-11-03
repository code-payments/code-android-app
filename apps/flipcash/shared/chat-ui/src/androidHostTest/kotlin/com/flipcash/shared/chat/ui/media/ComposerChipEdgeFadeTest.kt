package com.flipcash.shared.chat.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals

class ComposerChipEdgeFadeTest {
    @Test
    fun atRestWithRoomToTheEndOnlyTheEndFades() =
        assertEquals(ChipEdgeFades(start = false, end = true), chipEdgeFades(false, true))

    @Test
    fun scrolledToTheEndOnlyTheStartFades() =
        assertEquals(ChipEdgeFades(start = true, end = false), chipEdgeFades(true, false))

    @Test
    fun fitsWithoutScrollingNothingFades() =
        assertEquals(ChipEdgeFades(start = false, end = false), chipEdgeFades(false, false))

    @Test
    fun midScrollBothFade() =
        assertEquals(ChipEdgeFades(start = true, end = true), chipEdgeFades(true, true))
}
