package com.getcode.theme

import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.PlatformFont
import kotlin.test.Test
import kotlin.test.assertEquals

class AvenirFamilyTest {

    @Test
    fun `each weight draws the same face as iOS`() {
        val faces = (PlatformFonts.avenir as FontListFontFamily).fonts
            .associate { it.weight to (it as PlatformFont).identity.substringBefore('-') }

        assertEquals(
            mapOf(
                FontWeight.Thin to "avenir_next_regular.otf",
                FontWeight.ExtraLight to "avenir_next_regular.otf",
                FontWeight.Light to "avenir_next_regular.otf",
                FontWeight.Normal to "avenir_next_regular.otf",
                FontWeight.Medium to "avenir_next_medium.otf",
                FontWeight.SemiBold to "avenir_next_demi.otf",
                FontWeight.Bold to "avenir_next_demi.otf",
                FontWeight.ExtraBold to "avenir_next_demi.otf",
                FontWeight.Black to "avenir_next_demi.otf",
            ),
            faces,
        )
    }
}
