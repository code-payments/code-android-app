package com.flipcash.app.appsettings.internal

import com.flipcash.app.core.links.TrustedWebsite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

/**
 * Each store runs on the test's own dispatcher, in a child of the test's job. A failure inside
 * `trust` or `remove` then fails the test with its own exception, rather than freezing [websites]
 * and surfacing later as a wall-clock timeout.
 */
class InternalTrustedWebsitesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val jobs = mutableListOf<Job>()
    private var clock = 1_000L

    private fun TestScope.store(): InternalTrustedWebsites {
        val job = Job(coroutineContext.job).also { jobs += it }
        return InternalTrustedWebsites(
            scope = CoroutineScope(coroutineContext + job),
            produceFile = { folder.root.resolve("trusted-websites.preferences_pb") },
            now = { clock },
        )
    }

    private suspend fun InternalTrustedWebsites.awaitHosts(vararg hosts: String): List<TrustedWebsite> =
        websites.first { list -> list.map { it.host } == hosts.toList() }

    /** Cancels every store before [runTest] returns, since each keeps an eager collector running. */
    private fun runStoreTest(block: suspend TestScope.() -> Unit) = runTest {
        try {
            block()
        } finally {
            jobs.forEach { it.cancelAndJoin() }
        }
    }

    @Test
    fun `trusted hosts come back newest first with the time they were added`() = runStoreTest {
        val store = store()
        clock = 1_000L
        store.trust("youtube.com")
        store.awaitHosts("youtube.com")
        clock = 2_000L
        store.trust("x.com")

        assertEquals(
            listOf(TrustedWebsite("x.com", 2_000L), TrustedWebsite("youtube.com", 1_000L)),
            store.awaitHosts("x.com", "youtube.com"),
        )
    }

    @Test
    fun `remove takes the host out of the list`() = runStoreTest {
        val store = store()
        store.trust("x.com")
        store.awaitHosts("x.com")

        store.remove("x.com")

        assertEquals(emptyList(), store.awaitHosts())
    }

    @Test
    fun `hosts are kept exactly as given, so a subdomain is its own entry`() = runStoreTest {
        val store = store()
        store.trust("x.com")

        assertEquals(listOf("x.com"), store.awaitHosts("x.com").map { it.host })
    }

    /**
     * Log Out and Switch Accounts end in `AuthManager.resetStateForUser`, which does not know this
     * store; what keeps the list across them is that it lives in its own device-level file. A fresh
     * instance over that file — as after logging in again, or a restart — still has the host.
     */
    @Test
    fun `the list outlives the instance that wrote it`() = runStoreTest {
        val first = store()
        first.trust("x.com")
        first.awaitHosts("x.com")
        jobs.single().cancelAndJoin()

        assertEquals(listOf("x.com"), store().awaitHosts("x.com").map { it.host })
    }
}
