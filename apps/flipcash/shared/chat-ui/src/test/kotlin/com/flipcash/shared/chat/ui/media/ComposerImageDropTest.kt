package com.flipcash.shared.chat.ui.media

import android.net.Uri
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ComposerImageDropTest {

    private val png = Uri.parse("content://other.app/shot.png")
    private val jpg = Uri.parse("content://other.app/pic.jpg")
    private val pdf = Uri.parse("content://other.app/doc.pdf")
    private val unknown = Uri.parse("content://other.app/mystery")

    private val types = mapOf(png to "image/png", jpg to "image/jpeg", pdf to "application/pdf")
    private val mimeOf: (Uri) -> String? = { types[it] }

    @Test
    fun takesImages() {
        assertEquals(listOf(png, jpg), selectDroppedImages(listOf(png, jpg), true, 10, mimeOf))
    }

    @Test
    fun rejectsNonImagesAndUnknownTypes() {
        assertEquals(listOf(png), selectDroppedImages(listOf(pdf, png, unknown), true, 10, mimeOf))
        assertEquals(emptyList(), selectDroppedImages(listOf(pdf), true, 10, mimeOf))
    }

    @Test
    fun rejectsEverythingWhenConversationDoesNotAcceptMedia() {
        assertEquals(emptyList(), selectDroppedImages(listOf(png, jpg), false, 10, mimeOf))
    }

    @Test
    fun stopsAtRemainingRoom() {
        assertEquals(listOf(png), selectDroppedImages(listOf(png, jpg), true, 1, mimeOf))
        assertEquals(emptyList(), selectDroppedImages(listOf(png), true, 0, mimeOf))
    }

    @Test
    fun copiesInOrderAndSkipsFailures() = runTest {
        val local = { u: Uri -> Uri.parse("file:///cache/${u.lastPathSegment}") }
        val copies = copyDroppedImages(listOf(png, unknown, jpg)) { if (it == unknown) null else local(it) }
        assertEquals(listOf(local(png), local(jpg)), copies)
        val thrown = copyDroppedImages(listOf(png, jpg)) { if (it == png) error("grant revoked") else local(it) }
        assertEquals(listOf(local(jpg)), thrown)
    }
}
