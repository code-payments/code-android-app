package com.flipcash.app.messenger.internal.screens.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The bar's input row and the jump-to-bottom button both place from [ComposerInsets.sidePx], so the
 * value is what keeps the jump button over the `$` button while the keyboard moves.
 */
class ComposerInsetsTest {

    private val density = Density(1f)

    private class FakeInsets(var bottom: Int) : WindowInsets {
        override fun getLeft(density: Density, layoutDirection: LayoutDirection) = 0
        override fun getTop(density: Density) = 0
        override fun getRight(density: Density, layoutDirection: LayoutDirection) = 0
        override fun getBottom(density: Density) = bottom
    }

    private fun insets(now: Int, source: Int = now, target: Int = now, compact: Dp = 24.dp, normal: Dp = 16.dp) =
        ComposerInsets(FakeInsets(now), FakeInsets(source), FakeInsets(target), compact, normal)

    private fun ComposerInsets.px(hasComposer: Boolean) = with(density) { with(this@px) { sidePx(hasComposer) } }

    @Test
    fun `keyboard down leaves the compact margin`() {
        assertEquals(24, insets(now = 0, source = 0, target = 800).px(hasComposer = true))
    }

    @Test
    fun `keyboard fully up opens out to the normal margin`() {
        assertEquals(16, insets(now = 800, source = 0, target = 800).px(hasComposer = true))
    }

    @Test
    fun `halfway through the animation is halfway between the margins`() {
        assertEquals(20, insets(now = 400, source = 0, target = 800).px(hasComposer = true))
    }

    @Test
    fun `no composer is always the normal margin`() {
        assertEquals(16, insets(now = 0, source = 0, target = 800).px(hasComposer = false))
    }
}
