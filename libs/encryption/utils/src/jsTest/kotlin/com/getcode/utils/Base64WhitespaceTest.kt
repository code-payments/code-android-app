package com.getcode.utils

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * android.util.Base64 skips whitespace, so a stored value with a trailing newline still decodes.
 * The JS actual does the same. (java.util.Base64 on the desktop JVM throws, so this is JS-only.)
 */
class Base64WhitespaceTest {
    @Test
    fun skips_ascii_whitespace() {
        assertEquals("foobar", "Zm9v\nYmFy\n".decodeBase64().decodeToString())
        assertEquals("foobar", " Zm9vYmFy\r\n\t".decodeBase64().decodeToString())
    }
}
