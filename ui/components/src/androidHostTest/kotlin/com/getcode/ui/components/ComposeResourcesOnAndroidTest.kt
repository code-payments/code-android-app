package com.getcode.ui.components

import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.getcode.theme.DesignSystem
import com.getcode.util.resources.Res
import com.getcode.util.resources.ic_chevron_right
import org.jetbrains.compose.resources.painterResource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Common code reads strings and drawables through Compose resources, where Android used to read `R`.
 * These check the Android build packages them and resolves them at runtime, not only that it compiles.
 */
@RunWith(RobolectricTestRunner::class)
class ComposeResourcesOnAndroidTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `string from this module resolves`() {
        composeTestRule.setContent { DesignSystem { BetaIndicator() } }
        composeTestRule.onNodeWithText("Beta").assertIsDisplayed()
    }

    @Test
    fun `vector drawable from ui resources resolves`() {
        composeTestRule.setContent {
            Image(
                painter = painterResource(Res.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.testTag("chevron"),
            )
        }
        composeTestRule.onNodeWithTag("chevron").assertIsDisplayed()
    }
}
