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
        chatJourney()
        walletJourney()
        giveJourney()
        menuJourney()
    }

    private fun MacrobenchmarkScope.scannerJourney() {
        // Scanner is the home screen — let it fully render
        device.wait(Until.findObject(By.res("scanner_view")), TIMEOUT)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.chatJourney() {
        // The chats tab lists conversations; open the first one and send a text message. A
        // message is fund-free (money-safety: never tap Send Cash / confirm a spend). The
        // conversation is not hardcoded — send_contact_row resolves to the first row.
        openTab("nav_chats", "tips_screen")

        device.wait(Until.findObject(By.res("send_contact_row")), TIMEOUT)?.click()
        device.wait(Until.findObject(By.res("chat_screen")), LOGIN_TIMEOUT)
        device.waitForIdle()
        device.wait(Until.findObject(By.res("chat_message_input")), TIMEOUT)?.click()
        device.waitForIdle()
        device.executeShellCommand("input text BaselineProfileTest")
        device.waitForIdle()
        device.findObject(By.res("chat_send_icon"))?.click()
        device.waitForIdle()

        // Scroll the message list so MessageList / bubble composition gets compiled.
        flingScroll("chat_message_list", Direction.DOWN, 2)
        flingScroll("chat_message_list", Direction.UP, 2)

        // Back to the conversation list, fling it for coverage, then back to the scanner.
        device.pressBack()
        device.wait(Until.findObject(By.res("tips_screen")), TIMEOUT)
        device.waitForIdle()
        flingScroll("chat_list", Direction.UP, 2)
        flingScroll("chat_list", Direction.DOWN, 1)
        returnToScanner()
    }

    private fun MacrobenchmarkScope.discoveryJourney() {
        // Discovery is a wallet action tile now, not a tab of its own.
        openTab("nav_wallet", "wallet_screen")
        device.wait(Until.findObject(By.text("Discover Currencies")), TIMEOUT)?.click()

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
        device.pressBack()
        device.wait(Until.findObject(By.res("discovery_leaderboard")), TIMEOUT)
        device.waitForIdle()
        flingScroll("discovery_leaderboard", Direction.UP, 3)   // scroll down through the list
        flingScroll("discovery_leaderboard", Direction.DOWN, 2) // and back up

        returnToScanner()
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
        openTab("nav_wallet", "wallet_screen")

        // Tapping a card EXPANDS it in place (card-expand) rather than pushing a screen; the
        // overlay carries the same token_info_screen anchor as the pushed currency-info screen.
        device.wait(Until.findObject(By.text("Float")), TIMEOUT)?.click()
        device.wait(Until.findObject(By.res("token_info_screen")), TIMEOUT)
        device.waitForIdle()

        // Collapse the card back into the deck.
        device.pressBack()
        device.waitForIdle()

        returnToScanner()
    }

    private fun MacrobenchmarkScope.giveJourney() {
        // There is no cash tab: giving is an action on a currency you hold, reached from that
        // currency's own info surface.
        openTab("nav_wallet", "wallet_screen")
        device.wait(Until.findObject(By.text("Float")), TIMEOUT)?.click()
        device.wait(Until.findObject(By.res("token_info_screen")), TIMEOUT)
        device.waitForIdle()
        device.findObject(By.text("Give"))?.click()
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
        // Cancelling puts the bill back and pops to the currency it was given from.
        device.findObject(By.text("Cancel"))?.click()
        device.wait(Until.findObject(By.res("token_info_screen")), TIMEOUT)
        device.waitForIdle()

        returnToScanner()
    }

    private fun MacrobenchmarkScope.menuJourney() {
        // The "You" tab is the menu surface: tip card on top, settings list below.
        openTab("nav_tipcard", "menu_screen")
        flingScroll("menu_screen", Direction.UP, 2)
        flingScroll("menu_screen", Direction.DOWN, 1)
        returnToScanner()
    }

    /** Switch to a tab by its nav-bar anchor and wait for that tab's home to render. */
    private fun MacrobenchmarkScope.openTab(navResId: String, homeResId: String) {
        device.wait(Until.findObject(By.res(navResId)), TIMEOUT)?.click()
        device.wait(Until.findObject(By.res(homeResId)), LOGIN_TIMEOUT)
        device.waitForIdle()
    }

    /**
     * Return to the scanner tab.
     *
     * Tabs are REPLACED on a single root back stack, so Back never unwinds between them — the
     * only way home is the tab itself. Pop anything pushed over the current tab first (bounded,
     * so a stuck screen can't spin), then switch. An EXPANDED wallet card is not a nav entry and
     * leaves the nav bar in the hierarchy behind it (merely faded), so it's probed separately.
     */
    private fun MacrobenchmarkScope.returnToScanner() {
        var guard = 0
        while (
            guard++ < 4 &&
            (!device.hasObject(By.res("nav_scanner")) || device.hasObject(By.res("token_info_screen")))
        ) {
            device.pressBack()
            device.waitForIdle()
        }
        if (!device.hasObject(By.res("scanner_view"))) {
            device.wait(Until.findObject(By.res("nav_scanner")), TIMEOUT)?.click()
        }
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
