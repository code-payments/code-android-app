package com.getcode.chatcipher

import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest

/**
 * Runs [block] once libsodium has loaded. On JS the load is an async import(), and a test that
 * reaches the cipher before it finishes fails; elsewhere [awaitSodium] returns at once. The
 * timeout matches the mocha timeout the vector suites need on Node.
 */
internal fun sodiumTest(block: () -> Unit): TestResult = runTest(timeout = 1.minutes) {
    awaitSodium()
    block()
}
