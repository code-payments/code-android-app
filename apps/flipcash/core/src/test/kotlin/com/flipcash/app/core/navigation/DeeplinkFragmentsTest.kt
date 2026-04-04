package com.flipcash.app.core.navigation

import android.net.Uri
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeeplinkFragmentsTest {

    @Test
    fun parsesEntropyFragment() {
        val uri = Uri.parse("https://send.flipcash.com/c/#/e=abc123")
        val fragments = uri.fragments
        assertEquals("abc123", fragments[Key.entropy])
    }

    @Test
    fun parsesPayloadFragment() {
        val uri = Uri.parse("https://send.flipcash.com/c/#/p=somePayload")
        val fragments = uri.fragments
        assertEquals("somePayload", fragments[Key.payload])
    }

    @Test
    fun parsesMultipleFragments() {
        val uri = Uri.parse("https://send.flipcash.com/c/#/e=abc123/p=payload456")
        val fragments = uri.fragments
        assertEquals("abc123", fragments[Key.entropy])
        assertEquals("payload456", fragments[Key.payload])
    }

    @Test
    fun emptyFragmentReturnsEmptyMap() {
        val uri = Uri.parse("https://send.flipcash.com/c/")
        val fragments = uri.fragments
        assertTrue(fragments.isEmpty())
    }

    @Test
    fun noMatchingKeysReturnsEmptyMap() {
        val uri = Uri.parse("https://send.flipcash.com/c/#/z=unknown")
        val fragments = uri.fragments
        assertTrue(fragments.isEmpty())
    }

    @Test
    fun parsesKeyFragment() {
        val uri = Uri.parse("https://send.flipcash.com/c/#/k=myKey")
        val fragments = uri.fragments
        assertEquals("myKey", fragments[Key.key])
    }

    @Test
    fun parsesDataFragment() {
        val uri = Uri.parse("https://send.flipcash.com/c/#/d=someData")
        val fragments = uri.fragments
        assertEquals("someData", fragments[Key.data])
    }

    @Test
    fun handlesValueWithEqualsSign() {
        val uri = Uri.parse("https://send.flipcash.com/c/#/e=abc=def")
        val fragments = uri.fragments
        assertEquals("abc=def", fragments[Key.entropy])
    }

    @Test
    fun allFourKeysPresent() {
        val uri = Uri.parse("https://send.flipcash.com/c/#/e=entropy/p=payload/k=key/d=data")
        val fragments = uri.fragments
        assertEquals(4, fragments.size)
        assertEquals("entropy", fragments[Key.entropy])
        assertEquals("payload", fragments[Key.payload])
        assertEquals("key", fragments[Key.key])
        assertEquals("data", fragments[Key.data])
    }
}
