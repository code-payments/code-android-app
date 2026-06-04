package com.flipcash.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a baseline profile by exercising critical user journeys.
 *
 * Pass a seed phrase via instrumentation args to enable authenticated flows:
 * ```
 * -Pandroid.testInstrumentationRunnerArguments.SEED_PHRASE="word1 word2 ..."
 * ```
 *
 * Without a seed phrase, only the pre-auth startup path is profiled.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val seedPhrase: String?
        get() = InstrumentationRegistry.getArguments().getString("SEED_PHRASE")

    @Test
    fun generateBaselineProfile() {
        rule.collect(
            packageName = PACKAGE_NAME,
        ) {
            // Cold start — app init, Hilt DI, Compose runtime bootstrap
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            // After login, app data persists across iterations so subsequent
            // iterations launch directly into the scanner (already authenticated).
            val onScanner = device.hasObject(By.res("scanner_view"))

            if (onScanner) {
                // Already logged in from a prior iteration
                scannerJourney()
                walletJourney()
                giveJourney()
                menuJourney()
            } else {
                val seed = seedPhrase
                if (seed.isNullOrBlank()) {
                    preAuthJourney()
                } else {
                    login(seed)
                    scannerJourney()
                    walletJourney()
                    giveJourney()
                    menuJourney()
                }
            }
        }
    }

    private fun MacrobenchmarkScope.preAuthJourney() {
        // Navigate to seed input — exercises nav transitions, text input composables
        device.wait(Until.findObject(By.res("login_button")), TIMEOUT)?.click()
        device.waitForIdle()

        // Back to onboarding — exercises pop transition
        device.pressBack()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.login(seed: String) {
        // Tap "Log in"
        device.wait(Until.findObject(By.res("login_button")), TIMEOUT)?.click()

        // Wait for seed input field and tap to focus
        device.wait(Until.findObject(By.res("seed_input_field")), TIMEOUT)?.click()
        device.waitForIdle()

        // Type seed phrase via IME — UiObject2.text doesn't work with Compose TextField
        val escaped = seed.replace(" ", "%s")
        device.executeShellCommand("input text $escaped")
        device.waitForIdle()

        // Wait for seed validation to enable the button, then confirm
        device.wait(Until.hasObject(By.res("login_confirm_button").enabled(true)), TIMEOUT)
        device.findObject(By.res("login_confirm_button"))?.click()

        // Wait for scanner screen
        device.wait(Until.findObject(By.res("scanner_view")), LOGIN_TIMEOUT)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.scannerJourney() {
        // Scanner is the home screen — let it fully render
        device.wait(Until.findObject(By.res("scanner_view")), TIMEOUT)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.walletJourney() {
        // Open wallet sheet
        device.wait(Until.findObject(By.text("Wallet")), TIMEOUT)?.click()
        device.wait(Until.findObject(By.res("wallet_screen")), TIMEOUT)
        device.waitForIdle()

        // Open token info
        device.wait(Until.findObject(By.text("Float")), TIMEOUT)?.click()
        device.wait(Until.findObject(By.res("token_info_screen")), TIMEOUT)
        device.waitForIdle()

        // Back to wallet
        device.pressBack()
        device.waitForIdle()

        // Close sheet — swipe down to return to scanner
        dismissSheet()
    }

    private fun MacrobenchmarkScope.giveJourney() {
        // Open give/cash screen
        device.wait(Until.findObject(By.text("Give")), TIMEOUT)?.click()
        device.wait(Until.findObject(By.res("cash_screen")), TIMEOUT)
        device.waitForIdle()

        // Close sheet
        dismissSheet()
    }

    private fun MacrobenchmarkScope.menuJourney() {
        // Open menu
        device.wait(Until.findObject(By.res("menu_button")), TIMEOUT)?.click()
        device.wait(Until.findObject(By.res("menu_screen")), TIMEOUT)
        device.waitForIdle()

        // Close sheet
        dismissSheet()
    }

    private fun MacrobenchmarkScope.dismissSheet() {
        // Swipe from mid-screen downward to dismiss bottom sheet.
        // Avoid starting near the top to prevent pulling the notification panel.
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight / 3,
            device.displayWidth / 2,
            device.displayHeight * 3 / 4,
            10,
        )
        device.wait(Until.findObject(By.res("scanner_view")), TIMEOUT)
        device.waitForIdle()
    }

    companion object {
        private const val PACKAGE_NAME = "com.flipcash.app.android"
        private const val TIMEOUT = 5_000L
        private const val LOGIN_TIMEOUT = 15_000L
    }
}

private typealias MacrobenchmarkScope = androidx.benchmark.macro.MacrobenchmarkScope
