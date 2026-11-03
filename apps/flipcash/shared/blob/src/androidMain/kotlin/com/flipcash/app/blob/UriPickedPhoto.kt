package com.flipcash.app.blob

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject

/** A photo behind an Android [Uri], read through [resolver]. Handles `content://` and `file://`. */
data class UriPickedPhoto(val uri: Uri, private val resolver: ContentResolver) : PickedPhoto {
    override val key: String get() = uri.toString()

    override fun open(): InputStream? = try {
        resolver.openInputStream(uri)
    } catch (_: FileNotFoundException) {
        null
    }
}

/** The [Uri] behind [this]; a file photo is addressed as `file://`. */
fun PickedPhoto.toUri(): Uri = when (this) {
    is UriPickedPhoto -> uri
    is FilePickedPhoto -> Uri.fromFile(file)
    else -> Uri.parse(key)
}

internal class ContentResolverPickedPhotos @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PickedPhotos {
    override fun fromLocation(location: String): PickedPhoto =
        UriPickedPhoto(Uri.parse(location), context.contentResolver)
}

/** [ImageUploadPreparer.prepare] for a picked [Uri]. */
fun ImageUploadPreparer.prepare(uri: Uri, policy: com.flipcash.services.models.blob.UploadPolicy?, fileNamePrefix: String): ImageUploadPreparer.Outcome =
    prepare(photos.fromLocation(uri.toString()), policy, fileNamePrefix)

/** The cached copy as a [Uri], for the platform APIs that take one. */
val ImageUploadPreparer.Outcome.Prepared.uri: Uri get() = photo.toUri()
