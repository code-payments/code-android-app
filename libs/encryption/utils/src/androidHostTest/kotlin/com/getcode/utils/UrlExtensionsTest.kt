package com.getcode.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlExtensionsTest {

    @Test
    fun urlEncodeDecodeRoundtrip() {
        val original = "hello world & foo=bar"
        assertEquals(original, original.urlEncode().urlDecode())
    }

    @Test
    fun urlEncodeSpaces() {
        assertEquals("hello+world", "hello world".urlEncode())
    }
}
