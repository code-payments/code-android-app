package com.flipcash.shared.chat.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ChatPhotoSourceBoundsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a bubble scrolled partly past the list's top keeps its full bounds`() {
        var bounds: Rect? = null
        var px = 0f
        rule.setContent {
            px = with(LocalDensity.current) { 1.dp.toPx() }
            Box(Modifier.offset(y = 100.dp).size(200.dp).clipToBounds()) {
                Box(
                    Modifier
                        .offset(y = (-40).dp)
                        .size(width = 120.dp, height = 80.dp)
                        .onGloballyPositioned { bounds = it.unclippedBoundsInRoot() },
                )
            }
        }
        rule.waitForIdle()

        val b = bounds!!
        assertEquals(60 * px, b.top, 0.5f)
        assertEquals(80 * px, b.height, 0.5f)
        assertEquals(120 * px, b.width, 0.5f)
    }
}
