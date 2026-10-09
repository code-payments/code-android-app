package com.flipcash.app.appsettings.internal

import com.flipcash.app.core.links.TrustedWebsite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals

class InternalTrustedWebsitesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val jobs = mutableListOf<Job>()
    private var clock = 1_000L

    private fun store(): InternalTrustedWebsites {
        val job = Job().also { jobs += it }
        return InternalTrustedWebsites(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { folder.root.resolve("trusted-websites.preferences_pb") },
            now = { clock },
        )
    }

    private fun InternalTrustedWebsites.awaitHosts(vararg hosts: String): List<TrustedWebsite> =
        runBlocking {
            withTimeout(5_000) { websites.first { list -> list.map { it.host } == hosts.toList() } }
        }

    @After
    fun tearDown() = runBlocking { jobs.forEach { it.cancelAndJoin() } }

    @Test
    fun `trusted hosts come back newest first with the time they were added`() {
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
    fun `remove takes the host out of the list`() {
        val store = store()
        store.trust("x.com")
        store.awaitHosts("x.com")

        store.remove("x.com")

        assertEquals(emptyList(), store.awaitHosts())
    }

    @Test
    fun `hosts are kept exactly as given, so a subdomain is its own entry`() {
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
    fun `the list outlives the instance that wrote it`() = runBlocking {
        val first = store()
        first.trust("x.com")
        first.awaitHosts("x.com")
        jobs.single().cancelAndJoin()

        assertEquals(listOf("x.com"), store().awaitHosts("x.com").map { it.host })
    }
}
