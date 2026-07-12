package com.flipcash.app.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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

    private companion object {
        const val ENTROPY_A = "aaaaaaaaaaaaaaaaaaaaaaaa"
        const val ENTROPY_B = "bbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
