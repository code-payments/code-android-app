package com.flipcash.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
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
                authenticatedJourneys()
            } else {
                val seed = seedPhrase
                if (seed.isNullOrBlank()) {
                    preAuthJourney()
                } else {
                    login(seed)
                    authenticatedJourneys()
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

    /** All authenticated journeys, run from the scanner. */
    private fun MacrobenchmarkScope.authenticatedJourneys() {
        scannerJourney()
        discoveryJourney()
        sendChatJourney()
        walletJourney()
        giveJourney()
        menuJourney()
    }

    private fun MacrobenchmarkScope.scannerJourney() {
        // Scanner is the home screen — let it fully render
        device.wait(Until.findObject(By.res("scanner_view")), TIMEOUT)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.sendChatJourney() {
        // Open the Send tab -> contact list
        device.wait(Until.findObject(By.text("Send")), TIMEOUT)?.click()
        // Dismiss the "N Contacts Already On Flipcash" info dialog if it appears
        device.waitForIdle()
        device.findObject(By.text("OK"))?.click()
        device.wait(Until.findObject(By.res("send_contact_list")), LOGIN_TIMEOUT)
        device.waitForIdle()

        // Pull down to reveal the search bar, type random chars (exercises the empty
        // search state), then clear it.
        device.findObject(By.res("send_contact_list"))?.let { list ->
            val b = list.visibleBounds
            device.swipe(b.centerX(), b.top + 40, b.centerX(), b.centerY() + 200, 20)
        }
        device.wait(Until.findObject(By.res("send_search_field")), TIMEOUT)?.click()
        device.waitForIdle()
        device.executeShellCommand("input text zzqxwv")
        device.waitForIdle()
        device.findObject(By.res("send_search_clear"))?.click()
        device.waitForIdle()
        device.pressBack() // dismiss the keyboard

        // Open the FIRST contact's chat and send a text message. A message is fund-free
        // (money-safety: never tap Send Cash / confirm a spend). Contact is not
        // hardcoded — send_contact_row resolves to the first row.
        device.wait(Until.findObject(By.res("send_contact_row")), TIMEOUT)?.click()
        device.wait(Until.findObject(By.res("chat_screen")), LOGIN_TIMEOUT)
        device.waitForIdle()
        device.findObject(By.res("chat_send_message_button"))?.click()
        device.wait(Until.findObject(By.res("chat_message_input")), TIMEOUT)
        device.waitForIdle()
        device.executeShellCommand("input text BaselineProfileTest")
        device.waitForIdle()
        device.findObject(By.res("chat_send_icon"))?.click()
        device.waitForIdle()

        // Scroll the message list so MessageList / bubble composition gets compiled.
        flingScroll("chat_message_list", Direction.DOWN, 2)
        flingScroll("chat_message_list", Direction.UP, 2)

        // Back to the contact list, fling it for coverage, then back to the scanner.
        device.pressBack()
        device.wait(Until.findObject(By.res("send_contact_list")), TIMEOUT)
        device.waitForIdle()
        flingScroll("send_contact_list", Direction.UP, 2)
        flingScroll("send_contact_list", Direction.DOWN, 1)
        device.pressBack()
        device.wait(Until.findObject(By.res("scanner_view")), TIMEOUT)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.discoveryJourney() {
        // Open the Discover tab from the scanner bottom nav
        device.wait(Until.findObject(By.text("Discover")), TIMEOUT)?.click()

        device.wait(Until.findObject(By.res("discovery_leaderboard")), LOGIN_TIMEOUT)
        device.waitForIdle()

        // Open a token's info screen from the FRESH leaderboard first. (Tapping a row
        // right after flinging is unreliable — the row is still settling — so do the
        // token-info journey before scrolling the list.)
        device.wait(Until.findObject(By.res("leaderboard_token_row")), TIMEOUT)?.click()
        device.wait(Until.findObject(By.res("token_info_screen")), LOGIN_TIMEOUT)
        device.waitForIdle()

        // Scroll the token-info screen down until the market-cap chart's period tabs
        // (the bottom-most element) are on screen — the chart sits at the very bottom.
        scrollUntilVisible("token_info_screen", "market_cap_period_All", Direction.UP)

        // Interact with the market-cap chart: scrub across it (highlights points), then
        // toggle every time window (each reloads data + redraws the chart).
        device.findObject(By.res("market_cap_chart"))?.let { chart ->
            val b = chart.visibleBounds
            val y = b.centerY()
            device.drag(b.left + b.width() / 6, y, b.right - b.width() / 6, y, 40)
        }
        device.waitForIdle()
        listOf("Week", "Month", "Year", "All", "Day").forEach { period ->
            device.findObject(By.res("market_cap_period_$period"))?.click()
            device.waitForIdle()
        }

        // Back to the leaderboard, then fling-scroll it so TokenLeaderboard /
        // TokenMetricsRow / RankBadge composition + layout get compiled.
        device.wait(Until.findObject(By.res("action_back")), TIMEOUT)?.click()
        device.wait(Until.findObject(By.res("discovery_leaderboard")), TIMEOUT)
        device.waitForIdle()
        flingScroll("discovery_leaderboard", Direction.UP, 3)   // scroll down through the list
        flingScroll("discovery_leaderboard", Direction.DOWN, 2) // and back up

        // Close discovery to the scanner
        device.wait(Until.findObject(By.res("action_close")), TIMEOUT)?.click()
        device.wait(Until.findObject(By.res("scanner_view")), TIMEOUT)
        device.waitForIdle()
    }

    /**
     * Fling a scrollable [times], re-finding it each iteration. A Compose LazyColumn
     * recomposes on fling, invalidating a held UiObject2 — so we re-query and tolerate
     * a StaleObjectException rather than reusing a stale reference.
     */
    private fun MacrobenchmarkScope.flingScroll(resId: String, direction: Direction, times: Int) {
        repeat(times) {
            val scrollable = device.findObject(By.res(resId)) ?: return
            try {
                scrollable.setGestureMargin(device.displayWidth / 5)
                scrollable.fling(direction)
            } catch (_: StaleObjectException) {
                // View recomposed mid-fling; the next iteration re-finds it.
            }
            device.waitForIdle()
        }
    }

    /** Fling [scrollableResId] in [direction] (re-finding each time) until [targetResId] appears. */
    private fun MacrobenchmarkScope.scrollUntilVisible(
        scrollableResId: String,
        targetResId: String,
        direction: Direction,
        maxFlings: Int = 6,
    ) {
        repeat(maxFlings) {
            if (device.hasObject(By.res(targetResId))) return
            val scrollable = device.findObject(By.res(scrollableResId)) ?: return
            try {
                scrollable.setGestureMargin(device.displayWidth / 5)
                scrollable.fling(direction)
            } catch (_: StaleObjectException) {
            }
            device.waitForIdle()
        }
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
        // Open the Cash tab (the give/cash screen with the amount keypad)
        device.wait(Until.findObject(By.text("Cash")), TIMEOUT)?.click()
        device.wait(Until.findObject(By.res("keypad_dot")), TIMEOUT)
        device.waitForIdle()

        // Pull out the smallest bill ($0.01) to warm the keypad + bill rendering, then
        // Cancel to put it straight back — a self-reclaiming round-trip (net ~0).
        // MONEY-SAFETY: smallest amount, immediate Cancel, never Share/Collect. If an
        // iteration dies between Next and Cancel, the app reclaims the un-shared bill on
        // the next relaunch.
        device.findObject(By.res("keypad_dot"))?.click()
        device.findObject(By.res("keypad_0"))?.click()
        device.findObject(By.res("keypad_1"))?.click()
        device.findObject(By.text("Next"))?.click()
        device.wait(Until.findObject(By.res("cash_bill")), LOGIN_TIMEOUT)
        device.waitForIdle()
        device.findObject(By.text("Cancel"))?.click()
        device.wait(Until.findObject(By.res("scanner_view")), TIMEOUT)
        device.waitForIdle()
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
