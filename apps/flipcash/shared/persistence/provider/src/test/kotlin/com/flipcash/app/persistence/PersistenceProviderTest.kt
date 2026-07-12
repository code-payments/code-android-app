package com.flipcash.app.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
class PersistenceProviderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val provider = PersistenceProvider(context)

    @After
    fun tearDown() {
        FlipcashDatabase.closeDb()
    }

    @Test
    fun `openDatabase for the same user reuses the open instance`() {
        provider.openDatabase(ENTROPY_A)
        val first = FlipcashDatabase.requireInstance()

        provider.openDatabase(ENTROPY_A)

        assertSame(first, FlipcashDatabase.requireInstance())
    }

    @Test
    fun `openDatabase for a different user swaps the DB even while one is open`() {
        // Regression: after logout the DB is intentionally left open, so
        // openDatabase must NOT short-circuit on isOpen() — a different user
        // signing in has to reach init() and get their own database, not the
        // previous user's.
        provider.openDatabase(ENTROPY_A)
        val first = FlipcashDatabase.requireInstance()

        provider.openDatabase(ENTROPY_B)

        assertNotSame(first, FlipcashDatabase.requireInstance())
    }

    private companion object {
        const val ENTROPY_A = "aaaaaaaaaaaaaaaaaaaaaaaa"
        const val ENTROPY_B = "bbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
