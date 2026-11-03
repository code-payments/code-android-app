package com.flipcash.app.blob

import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * A photo the user picked or the app staged, wherever it lives: a content URI on Android, a file on
 * desktop. Callers hold this instead of a platform handle so upload code is the same on both.
 */
interface PickedPhoto {
    /** Identifies the photo for as long as it exists: equal keys are the same photo. */
    val key: String

    /** A new stream over the photo's bytes, or null if it cannot be read. The caller closes it. */
    fun open(): InputStream?
}

/** The photo's bytes, or null if it cannot be read. */
fun PickedPhoto.readBytes(): ByteArray? = try {
    open()?.use { it.readBytes() }
} catch (_: FileNotFoundException) {
    null
}

/** A photo in a file. */
data class FilePickedPhoto(val file: File) : PickedPhoto {
    override val key: String get() = file.absolutePath

    override fun open(): InputStream? = file.takeIf { it.isFile }?.inputStream()
}

/**
 * Turns a platform location into a [PickedPhoto]: a content URI string on Android, a path on
 * desktop. Lets code that only holds a string-shaped location (an Android `Uri`) reach shared upload
 * code without the shared code naming the platform type.
 */
fun interface PickedPhotos {
    fun fromLocation(location: String): PickedPhoto
}
