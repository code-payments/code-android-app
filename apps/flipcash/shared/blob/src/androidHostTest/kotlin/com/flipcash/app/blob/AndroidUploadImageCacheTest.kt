package com.flipcash.app.blob

import android.content.Context
import android.webkit.MimeTypeMap
import androidx.test.core.app.ApplicationProvider
import com.getcode.util.resources.AndroidContentReader
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals

/** The real cache, so a member that shadows the top-level `uploadMimeFor` recurses here instead of in production. */
@RunWith(RobolectricTestRunner::class)
class AndroidUploadImageCacheTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cache = AndroidUploadImageCache(context, AndroidContentReader(context))

    @Before
    fun registerMimeTypes() {
        shadowOf(MimeTypeMap.getSingleton()).apply {
            addExtensionMimeTypeMapping("jpeg", "image/jpeg")
            addExtensionMimeTypeMapping("jpg", "image/jpeg")
            addExtensionMimeTypeMapping("png", "image/png")
            addExtensionMimeTypeMapping("heic", "image/heic")
        }
    }

    @Test
    fun `a JPEG source uploads as JPEG`() {
        assertEquals("image/jpeg", cache.uploadMimeFor("image/jpeg"))
    }

    @Test
    fun `a non-JPEG source uploads as PNG`() {
        assertEquals("image/png", cache.uploadMimeFor("image/heic"))
    }

    @Test
    fun `an unknown source uploads as PNG`() {
        assertEquals("image/png", cache.uploadMimeFor(null))
    }
}
