package com.flipcash.app.userprofile.internal.username

import com.flipcash.services.models.MaxUsernameLength
import com.flipcash.services.models.MinUsernameLength
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

class LengthComplaintTest {

    @Test
    fun `an empty field is too short`() {
        assertIs<LengthComplaint.TooShort>(lengthComplaint(""))
    }

    @Test
    fun `one character short of the minimum is too short`() {
        assertIs<LengthComplaint.TooShort>(lengthComplaint("a".repeat(MinUsernameLength - 1)))
    }

    @Test
    fun `exactly the minimum passes`() {
        assertNull(lengthComplaint("a".repeat(MinUsernameLength)))
    }

    @Test
    fun `exactly the maximum passes`() {
        assertNull(lengthComplaint("a".repeat(MaxUsernameLength)))
    }

    @Test
    fun `one character past the maximum is too long`() {
        assertIs<LengthComplaint.TooLong>(lengthComplaint("a".repeat(MaxUsernameLength + 1)))
    }

    // The charset is the input transformation's job, not this one's — a handle of the right length
    // is accepted here even when the server would reject it, and gets the "Invalid Characters"
    // dialog off the wire instead.
    @Test
    fun `characters are not this check's concern`() {
        assertNull(lengthComplaint("Not A Handle!"))
    }
}
