package com.flipcash.app.theme.internal

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

class Flipcash2ColorSpecTest {

    // The flat token is a derivation, so a change to the blue or the background has to move it too.
    // Worked per channel at an exact 10% (as iOS derives its secondaries); Compose's `compositeOver`
    // quantises the alpha to 26/255 and lands one step greener.
    @Test
    fun `notificationSecondary is the notification blue at a tenth over the main background`() {
        with(Flipcash2ColorSpec) {
            assertEquals(tenthOver(notification, primary), rgb(notificationSecondary))
        }
    }

    private fun tenthOver(top: Color, ground: Color): List<Int> =
        listOf(top.red to ground.red, top.green to ground.green, top.blue to ground.blue)
            .map { (t, g) -> ((0.1f * t + 0.9f * g) * 255).roundToInt() }

    private fun rgb(color: Color): List<Int> =
        listOf(color.red, color.green, color.blue).map { (it * 255).roundToInt() }
}
