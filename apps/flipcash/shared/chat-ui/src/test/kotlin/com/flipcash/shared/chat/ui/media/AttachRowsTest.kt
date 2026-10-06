package com.flipcash.shared.chat.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals

class AttachRowsTest {

    @Test
    fun allRowsInOrder() {
        assertEquals(
            listOf(AttachRow.Cash, AttachRow.Camera, AttachRow.Photos),
            attachRows(cashOffered = true, acceptsMedia = true, stagedCount = 0),
        )
    }

    @Test
    fun mediaRowsHiddenWithoutMedia() {
        assertEquals(
            listOf(AttachRow.Cash),
            attachRows(cashOffered = true, acceptsMedia = false, stagedCount = 0),
        )
    }

    @Test
    fun cashRowHiddenWithoutCash() {
        assertEquals(
            listOf(AttachRow.Camera, AttachRow.Photos),
            attachRows(cashOffered = false, acceptsMedia = true, stagedCount = 3),
        )
    }

    @Test
    fun noRows() {
        assertEquals(emptyList(), attachRows(cashOffered = false, acceptsMedia = false, stagedCount = 0))
    }

    @Test
    fun mediaRowsHiddenAtLimit() {
        assertEquals(
            listOf(AttachRow.Cash),
            attachRows(cashOffered = true, acceptsMedia = true, stagedCount = 10),
        )
    }

}
