package com.flipcash.app.messenger.internal.screens.profile.edit

import com.flipcash.services.models.chat.BlobId
import com.flipcash.services.models.chat.DescriptionEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * `EditChat` leaves a field unchanged when it is unset, which makes an over-filled request
 * destructive rather than merely wasteful: renaming a group while also sending the picture field
 * would rewrite the picture, and sending it as null would clear it.
 *
 * These assert the one property that matters at the call site — each edit carries its own field
 * and nothing else.
 */
class PartialEditsTest {

    @Test
    fun `renaming sends a title and no picture`() {
        val parameters = titleOnly("Book Club")

        assertEquals("Book Club", parameters.title)
        assertNull(parameters.picture, "a rename must not touch the group's picture")
    }

    @Test
    fun `renaming sends the trimmed title`() {
        assertEquals("Book Club", titleOnly("  Book Club  ").title)
    }

    @Test
    fun `changing the picture sends a blob and no title`() {
        val blob = BlobId(byteArrayOf(1, 2, 3))

        val parameters = pictureOnly(blob)

        assertEquals(blob, parameters.picture)
        assertNull(parameters.title, "a picture change must not touch the group's name")
    }

    @Test
    fun `changing the cover sends a cover blob and nothing else`() {
        val blob = BlobId(byteArrayOf(4, 5, 6))

        val parameters = coverOnly(blob)

        assertEquals(blob, parameters.coverPicture)
        assertNull(parameters.title)
        assertNull(parameters.picture)
        assertNull(parameters.description)
    }

    @Test
    fun `a description edit sends the description and nothing else`() {
        val parameters = descriptionOnly(DescriptionEdit.Set("Good boys only"))

        assertEquals(DescriptionEdit.Set("Good boys only"), parameters.description)
        assertNull(parameters.title)
        assertNull(parameters.picture)
        assertNull(parameters.coverPicture)
    }

    @Test
    fun `description text is sent trimmed`() {
        assertEquals(
            DescriptionEdit.Set("Good boys only"),
            descriptionOnly(DescriptionEdit.Set("  Good boys only \n")).description,
        )
    }

    @Test
    fun `an emptied description is a clear and not an empty set`() {
        assertEquals(DescriptionEdit.Clear, descriptionOnly(DescriptionEdit.Set("")).description)
        assertEquals(DescriptionEdit.Clear, descriptionOnly(DescriptionEdit.Set("  \n ")).description)
        assertEquals(DescriptionEdit.Clear, descriptionOnly(DescriptionEdit.Clear).description)
    }
}
