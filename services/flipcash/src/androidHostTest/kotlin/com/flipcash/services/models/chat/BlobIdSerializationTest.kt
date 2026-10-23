package com.flipcash.services.models.chat

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * A blob id is persisted inside `MediaItem` JSON — `user_profiles.profile_picture_json` and chat
 * message contents — so its encoding is a storage format, not an implementation detail. Rows
 * written while the id was a value class hold it inlined; a build that can't read those drops the
 * whole `MediaItem` and the surface loses its image entirely.
 */
class BlobIdSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val persistedByValueClassBuild = """
        {"renditions":[{"role":"ORIGINAL","blobId":[1,2,3,4],"blob":null}]}
    """.trimIndent()

    @Test
    fun `a media item persisted by an older build still decodes`() {
        val decoded = json.decodeFromString<MediaItem>(persistedByValueClassBuild)

        assertEquals(BlobId(byteArrayOf(1, 2, 3, 4)), decoded.renditions.single().blobId)
    }

    @Test
    fun `an id encodes as bare bytes, so an older build can still read it back`() {
        val encoded = json.encodeToString(
            MediaItem(
                renditions = listOf(
                    MediaItemRendition(
                        role = MediaItemRendition.Role.ORIGINAL,
                        blobId = BlobId(byteArrayOf(1, 2, 3, 4)),
                        blob = null,
                    ),
                ),
            ),
        )

        assertContains(encoded, """"blobId":[1,2,3,4]""")
    }

    @Test
    fun `an id survives a round trip`() {
        val id = BlobId(byteArrayOf(9, 8, 7))

        assertEquals(id, json.decodeFromString<BlobId>(json.encodeToString(id)))
    }
}
