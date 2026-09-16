package com.flipcash.app.lab.internal

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.getcode.theme.DesignSystem
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
class ContractInfoRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `shows the package name and the version-commit detail`() {
        composeTestRule.setContent {
            DesignSystem {
                ContractInfoRow(
                    ContractInfo(name = "ocp", version = "0.5.0", commit = "82202912", isLocal = false)
                )
            }
        }

        composeTestRule.onNodeWithText("ocp").assertIsDisplayed()
        composeTestRule.onNodeWithText("0.5.0 · 82202912").assertIsDisplayed()
    }

    @Test
    fun `a local contract shows LOCAL in place of a commit`() {
        composeTestRule.setContent {
            DesignSystem {
                ContractInfoRow(
                    ContractInfo(name = "flipcash2", version = "0.9.0", commit = "LOCAL", isLocal = true)
                )
            }
        }

        composeTestRule.onNodeWithText("0.9.0 · LOCAL").assertIsDisplayed()
    }
}
