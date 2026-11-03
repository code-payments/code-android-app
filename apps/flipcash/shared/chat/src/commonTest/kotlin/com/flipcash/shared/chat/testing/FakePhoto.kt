package com.flipcash.shared.chat.testing

import com.flipcash.app.blob.PickedPhoto
import java.io.InputStream
import java.nio.file.Files

/** A picked photo that only has a name: enough for code that stages, keys and passes photos along. */
data class FakePhoto(val name: String) : PickedPhoto {
    override val key: String get() = "fake://$name"
    override fun open(): InputStream? = null
}

/** A scratch directory deleted when the JVM exits, standing in for JUnit's `TemporaryFolder`. */
fun tempRoot(): java.io.File = Files.createTempDirectory("chat-test").toFile().also { it.deleteOnExit() }
