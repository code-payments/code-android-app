package com.flipcash.app.messenger.internal.link

import okio.Buffer
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The body reader stops once the head has ended, and reads to the cap when it never does. */
class HeadReaderTest {

    private val filler = "z".repeat(5000)

    private fun source(text: String) = Buffer().writeUtf8(text)

    @Test
    fun `reading stops after the head closes and the rest is left unread`() {
        val src = source("<html><head><title>T</title></head>$filler")
        val read = src.readHead(cap = 1 shl 20, chunk = 16)
        assertTrue(String(read).contains("</head"))
        assertTrue(src.size > 4000, "bytes after the head must stay unread, left ${src.size}")
    }

    @Test
    fun `an opening body tag also ends the head, in any case`() {
        val src = source("<html><title>T</title><BoDy>$filler")
        val read = src.readHead(cap = 1 shl 20, chunk = 16)
        assertTrue(String(read).contains("<BoDy"))
        assertTrue(src.size > 4000, "left ${src.size}")
    }

    @Test
    fun `an upper case closing head tag ends the head`() {
        val src = source("<HEAD><TITLE>T</TITLE></HEAD>$filler")
        src.readHead(cap = 1 shl 20, chunk = 16)
        assertTrue(src.size > 4000, "left ${src.size}")
    }

    @Test
    fun `a marker split across two reads is still seen`() {
        for (chunk in 1L..9L) for (pad in 0..8) {
            val src = source("a".repeat(pad) + "</head>" + filler)
            src.readHead(cap = 1 shl 20, chunk = chunk)
            assertTrue(src.size > 4000, "chunk $chunk pad $pad: left ${src.size}")
            val body = source("a".repeat(pad) + "<body>" + filler)
            body.readHead(cap = 1 shl 20, chunk = chunk)
            assertTrue(body.size > 4000, "body chunk $chunk pad $pad: left ${body.size}")
        }
    }

    @Test
    fun `without either marker it reads to the cap`() {
        val src = source("<head>" + filler)
        val read = src.readHead(cap = 1000, chunk = 64)
        assertEquals(1000, read.size)
    }

    @Test
    fun `a short body without a marker is returned whole`() {
        val read = source("<head><title>T</title>").readHead(cap = 1000)
        assertEquals("<head><title>T</title>", String(read))
    }
}
