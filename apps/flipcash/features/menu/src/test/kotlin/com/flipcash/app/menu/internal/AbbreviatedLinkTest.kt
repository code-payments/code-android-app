package com.flipcash.app.menu.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class AbbreviatedLinkTest {

    @Test
    fun `an id link keeps the first five characters of the id`() {
        assertEquals(
            "app.flipcash.com/tip/b0ced…",
            "https://app.flipcash.com/tip/b0ced1f2a3b4c5d6".abbreviatedLink(),
        )
    }

    @Test
    fun `a vanity link is left whole`() {
        assertEquals(
            "flipcash.com/sally_streamer",
            "https://flipcash.com/sally_streamer".abbreviatedLink(),
        )
    }

    @Test
    fun `the longest allowed handle still isn't abbreviated`() {
        assertEquals(
            "flipcash.com/abcdefghijklmno",
            "https://flipcash.com/abcdefghijklmno".abbreviatedLink(),
        )
    }

    @Test
    fun `a segment too long to be a handle is abbreviated`() {
        assertEquals(
            "flipcash.com/abcde…",
            "https://flipcash.com/abcdefghijklmnop".abbreviatedLink(),
        )
    }

    @Test
    fun `an id no longer than the stub is left alone rather than gaining an ellipsis`() {
        assertEquals(
            "app.flipcash.com/tip/AB-CD",
            "https://app.flipcash.com/tip/AB-CD".abbreviatedLink(),
        )
    }

    @Test
    fun `the scheme is always dropped`() {
        assertEquals(
            "flipcash.com/mcansh",
            "http://flipcash.com/mcansh".abbreviatedLink(),
        )
    }

    @Test
    fun `a link with no scheme survives unchanged`() {
        assertEquals(
            "flipcash.com/mcansh",
            "flipcash.com/mcansh".abbreviatedLink(),
        )
    }
}
