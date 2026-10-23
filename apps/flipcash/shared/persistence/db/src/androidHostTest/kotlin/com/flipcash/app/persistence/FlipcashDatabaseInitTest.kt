package com.flipcash.app.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class FlipcashDatabaseInitTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun tearDown() {
        FlipcashDatabase.closeDb()
    }

    @Test
    fun `re-init with same entropy keeps the same open instance`() {
        FlipcashDatabase.init(context, ENTROPY_A)
        val first = FlipcashDatabase.requireInstance()
        // Force the (lazily-opened) connection pool open, as an active Room query would.
        first.openHelper.writableDatabase
        assertTrue(first.isOpen)

        // A soft re-login (or a concurrent AuthManager.init) must not tear down
        // the live connection pool that active Room queries depend on.
        FlipcashDatabase.init(context, ENTROPY_A)
        val second = FlipcashDatabase.requireInstance()

        assertSame(first, second)
        assertTrue(first.isOpen)
    }

    @Test
    fun `init with different entropy rebuilds the database`() {
        FlipcashDatabase.init(context, ENTROPY_A)
        val first = FlipcashDatabase.requireInstance()

        FlipcashDatabase.init(context, ENTROPY_B)
        val second = FlipcashDatabase.requireInstance()

        assertNotSame(first, second)
        second.openHelper.writableDatabase
        assertTrue(second.isOpen)
    }

    @Test
    fun `suspending query against the outgoing database fails after an account switch`() {
        runBlocking {
            FlipcashDatabase.init(context, ENTROPY_A)
            val outgoing = FlipcashDatabase.requireInstance()
            val outgoingDao = outgoing.tokenDao()
            // Proves the DAO and its connection pool are live before the switch.
            assertTrue(outgoingDao.getAll().isEmpty())

            FlipcashDatabase.init(context, ENTROPY_B)

            assertFalse(outgoing.isOpen)
            // The incoming database is usable straight away.
            assertTrue(FlipcashDatabase.requireInstance().tokenDao().getAll().isEmpty())

            // Room 2.8.5 made suspending queries throw IllegalStateException once the
            // database is closed; in practice close() cancels Room's internal scope
            // first, so the query fails with a CancellationException instead. Either
            // way it must fail rather than read through a closed pool, so this asserts
            // the failure and not its type.
            val error = runCatching { outgoingDao.getAll() }.exceptionOrNull()
            assertNotNull(error, "query against the closed outgoing database should fail")
        }
    }

    @Test
    fun `account switch does not fail a live collector on the outgoing database`() {
        runBlocking {
            FlipcashDatabase.init(context, ENTROPY_A)
            val outgoingDao = FlipcashDatabase.requireInstance().tokenDao()

            val firstEmission = CountDownLatch(1)
            val failed = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>(null)
            val collector = CoroutineScope(Dispatchers.IO).launch {
                outgoingDao.observeAll()
                    .catch {
                        failure.set(it)
                        failed.countDown()
                    }
                    .collect { firstEmission.countDown() }
            }

            try {
                assertTrue(
                    firstEmission.await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "collector never received an emission from the outgoing database",
                )

                // Switching accounts closes the outgoing database underneath the live
                // collector. A screen still observing the old account must not be handed
                // an exception, whatever Room does to the invalidation tracker.
                FlipcashDatabase.init(context, ENTROPY_B)

                assertFalse(
                    failed.await(QUIET_SECONDS, TimeUnit.SECONDS),
                    "collector on the outgoing database failed with ${failure.get()}",
                )
            } finally {
                collector.cancel()
            }
        }
    }

    private companion object {
        const val ENTROPY_A = "aaaaaaaaaaaaaaaaaaaaaaaa"
        const val ENTROPY_B = "bbbbbbbbbbbbbbbbbbbbbbbb"

        /** Upper bound on waiting for something that should happen. */
        const val TIMEOUT_SECONDS = 10L

        /** How long to wait to be satisfied that a failure is not coming. */
        const val QUIET_SECONDS = 2L
    }
}
