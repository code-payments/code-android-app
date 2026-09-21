package com.flipcash.services.internal.network.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `authenticate` signs the builder as it stands, so a field assigned after it is outside the
 * signature and the call comes back `UNAUTHENTICATED`. Nothing in the type system says so, and the
 * mistake is invisible at a glance — it is a matter of which line a `setAuth` sits on.
 *
 * [buildAuthenticated] removes the choice by signing and building together. This keeps it that way:
 * a new API that reaches for the lower-level `authenticate` has to justify itself here first.
 */
class AuthenticateCallSiteTest {

    /**
     * `BlobStorageApi` hands `authenticate` to `getBlobsRequest`, which assigns it last under the
     * cover of `GetBlobsRequestSigningTest`.
     */
    private val allowed = setOf("BlobStorageApi.kt")

    @Test
    fun `authenticate is only called where the ordering is already accounted for`() {
        val callers = networkSources()
            .filter { it.readText().contains("authenticate(") }
            .map { it.name }
            .sorted()

        assertEquals(
            "Use buildAuthenticated so auth cannot be assigned before another field.",
            allowed.sorted(),
            callers,
        )
    }

    private fun networkSources(): List<File> {
        val root = File("src/main/kotlin/com/flipcash/services/internal/network")
        assertTrue(
            "Expected to scan sources from the module directory, looked in ${root.absolutePath}",
            root.isDirectory,
        )

        val sources = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.name == "AuthenticateMessage.kt" }
            .toList()

        // Guards against a silent pass if the scan ever stops finding the sources.
        assertTrue("Scanned only ${sources.size} sources", sources.size > 20)
        return sources
    }
}
