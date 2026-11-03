package com.getcode.theme

import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import kotlin.test.Test
import kotlin.test.assertEquals

class AvenirFamilyTest {

    @Test
    fun `each weight draws the same face as iOS`() {
        val faces = (PlatformFonts.avenir as FontListFontFamily).fonts
            .filterIsInstance<ResourceFont>()
            .filter { it.style == FontStyle.Normal }
            .associate { it.weight to it.resId }

        assertEquals(
            mapOf(
                FontWeight.Thin to R.font.avenir_next_regular,
                FontWeight.ExtraLight to R.font.avenir_next_regular,
                FontWeight.Light to R.font.avenir_next_regular,
                FontWeight.Normal to R.font.avenir_next_regular,
                FontWeight.Medium to R.font.avenir_next_medium,
                FontWeight.SemiBold to R.font.avenir_next_demi,
                FontWeight.Bold to R.font.avenir_next_demi,
                FontWeight.ExtraBold to R.font.avenir_next_demi,
                FontWeight.Black to R.font.avenir_next_demi,
            ),
            faces,
        )
    }

    @Test
    fun `every Avenir style uses the one family`() {
        with(codeTypography) {
            listOf(
                displayExtraLarge, displayLarge, displayMedium, displayCompact, displaySmall,
                displayExtraSmall, keyboard, screenTitle, screenTitleLarge, textLarge, textMedium,
                textSmall, textMessage, caption, linkMedium, linkSmall,
            ).forEach { assertEquals(PlatformFonts.avenir, it.fontFamily) }
        }
    }
}
