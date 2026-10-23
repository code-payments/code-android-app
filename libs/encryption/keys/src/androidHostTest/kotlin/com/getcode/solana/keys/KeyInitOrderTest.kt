package com.getcode.solana.keys

import java.io.File
import java.net.URLClassLoader
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyInitOrderTest {

    // Key32's companion builds PublicKeys, so which of the two classes the JVM
    // initializes first matters. A fresh class loader controls that order
    // regardless of what other tests in this JVM have already touched.
    @Test
    fun `Key32 initializes when it is loaded before PublicKey`() {
        freshClassLoader().use { loader ->
            val key32 = Class.forName("com.getcode.solana.keys.Key32", true, loader)
            val companion = key32.getField("Companion").get(null)
            val zero = companion.javaClass.getMethod("getZero").invoke(companion)

            val publicKey = Class.forName("com.getcode.solana.keys.PublicKey", false, loader)
            val publicKeyCompanion = publicKey.getField("Companion").get(null)
            val publicZero = publicKeyCompanion.javaClass.getMethod("getZERO").invoke(publicKeyCompanion)

            val bytes = key32.getMethod("getBytes")
            assertEquals(bytes.invoke(zero), bytes.invoke(publicZero))
        }
    }

    private fun freshClassLoader(): URLClassLoader {
        val urls = System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .map { File(it).toURI().toURL() }
            .toTypedArray()
        return URLClassLoader(urls, null)
    }
}
