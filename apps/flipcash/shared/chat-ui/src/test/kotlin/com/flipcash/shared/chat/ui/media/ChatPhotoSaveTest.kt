package com.flipcash.shared.chat.ui.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatPhotoSaveTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun jpegIsSavedAsItIs() {
        assertEquals("image/jpeg", sniffImageType(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0))?.mime)
    }

    @Test
    fun pngIsSavedAsItIs() {
        assertEquals("png", sniffImageType(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D))?.extension)
    }

    @Test
    fun webpNeedsBothRiffAndWebpMarkers() {
        val webp = bytes(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50)
        assertEquals("image/webp", sniffImageType(webp)?.mime)
        assertNull(sniffImageType(bytes(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x41, 0x56, 0x49, 0x20)))
    }

    @Test
    fun anythingElseFallsBackToReencoding() {
        assertNull(sniffImageType(bytes(1, 2, 3)))
        assertNull(sniffImageType(ByteArray(0)))
    }
}
