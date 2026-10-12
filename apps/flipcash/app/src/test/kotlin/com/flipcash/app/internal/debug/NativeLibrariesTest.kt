package com.flipcash.app.internal.debug

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeLibrariesTest {

    private val names = listOf("jnidispatch", "bugsnag-ndk")

    @Test
    fun `an install with every library is missing none`() {
        val missing = missingLibraries(names) { "/data/app/base.apk!/lib/arm64-v8a/lib$it.so" }

        assertTrue(missing.isEmpty())
    }

    @Test
    fun `an install without its ABI split is missing all of them`() {
        val missing = missingLibraries(names) { null }

        assertEquals(names, missing)
    }

    @Test
    fun `only the libraries that do not resolve are missing`() {
        val missing = missingLibraries(names) { if (it == "jnidispatch") null else "/lib$it.so" }

        assertEquals(listOf("jnidispatch"), missing)
    }

    @Test
    fun `a lookup that throws counts as missing`() {
        val missing = missingLibraries(names) { throw UnsatisfiedLinkError(it) }

        assertEquals(names, missing)
    }
}
