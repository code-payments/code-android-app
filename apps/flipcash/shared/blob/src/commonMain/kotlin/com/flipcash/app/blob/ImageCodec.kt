package com.flipcash.app.blob

/** A photo decoded for upload: display orientation applied, flattened onto white. */
interface DecodedImage : AutoCloseable {
    val width: Int
    val height: Int

    /** The image as JPEG at [quality] (0-100), or null if the encoder failed. */
    fun compressJpeg(quality: Int): ByteArray?
}

/** A small ARGB copy of a photo, plus the size of the photo it was taken from. */
class ArgbThumbnail(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

/**
 * The image decoding and encoding the upload path needs from the platform: `ImageDecoder` and
 * `Bitmap` on Android, Skia on desktop. Everything that decides what to do with the pixels (target
 * size, quality ladder, blurhash maths) is shared and sits above this.
 */
interface ImageCodec {
    /** The photo's size as stored, before any rotation or scaling; null if it can't be read. */
    fun sourceSize(photo: PickedPhoto): PixelSize?

    /**
     * Decodes [photo] with its EXIF rotation applied and scales it to the bounds in [limits]
     * (see [ChatMediaDownscale]). JPEG has no alpha, so transparency is composited on white.
     */
    fun decode(photo: PickedPhoto, limits: ChatMediaLimits): Result<DecodedImage>

    /** An ARGB thumbnail of [jpeg] no larger than [maxEdge] on its longer side; null if it won't decode. */
    fun thumbnail(jpeg: ByteArray, maxEdge: Int): ArgbThumbnail?
}
