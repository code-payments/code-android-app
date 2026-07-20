package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.blob.v1.Model as BlobModel
import com.codeinc.flipcash.gen.email.v1.emailAddress
import com.codeinc.flipcash.gen.phone.v1.phoneNumber
import com.codeinc.flipcash.gen.profile.v1.Model
import com.codeinc.flipcash.gen.profile.v1.socialProfile
import com.codeinc.flipcash.gen.profile.v1.xProfile
import com.flipcash.services.models.SocialAccount
import com.flipcash.services.models.chat.MediaItemRendition
import com.google.protobuf.ByteString
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserProfileMapperTest {

    private val mapper = UserProfileMapper(socialMapper = SocialAccountMapper())

    private fun userProfile(block: Model.UserProfile.Builder.() -> Unit): Model.UserProfile =
        Model.UserProfile.newBuilder().apply(block).build()

    @Test
    fun `maps display name`() {
        val proto = userProfile { displayName = "Alice" }
        assertEquals("Alice", mapper.map(proto).displayName)
    }

    @Test
    fun `maps social profiles`() {
        val proto = userProfile {
            displayName = "Bob"
            addSocialProfiles(socialProfile {
                x = xProfile {
                    id = "x-123"
                    username = "bob"
                }
            })
        }

        val result = mapper.map(proto)
        assertEquals(1, result.socialAccounts.size)
        assertIs<SocialAccount.TwitterX>(result.socialAccounts.first())
        assertEquals("bob", (result.socialAccounts.first() as SocialAccount.TwitterX).username)
    }

    @Test
    fun `filters out null social profiles`() {
        val proto = userProfile {
            displayName = "Charlie"
            addSocialProfiles(socialProfile { }) // TYPE_NOT_SET -> null
        }

        val result = mapper.map(proto)
        assertEquals(0, result.socialAccounts.size)
    }

    @Test
    fun `maps verified phone number`() {
        val proto = userProfile {
            displayName = "Dave"
            phoneNumber = phoneNumber { value = "+15551234567" }
        }

        assertEquals("+15551234567", mapper.map(proto).verifiedPhoneNumber)
    }

    @Test
    fun `maps verified email address`() {
        val proto = userProfile {
            displayName = "Eve"
            emailAddress = emailAddress { value = "eve@example.com" }
        }

        assertEquals("eve@example.com", mapper.map(proto).verifiedEmailAddress)
    }

    @Test
    fun `no phone or email returns null`() {
        val proto = userProfile { displayName = "Frank" }

        val result = mapper.map(proto)
        assertNull(result.verifiedPhoneNumber)
        assertNull(result.verifiedEmailAddress)
    }

    @Test
    fun `maps profile picture renditions`() {
        val proto = userProfile {
            displayName = "Grace"
            profilePicture = BlobModel.Media.newBuilder()
                .addRenditions(
                    BlobModel.Rendition.newBuilder()
                        .setRole(BlobModel.Rendition.Role.DISPLAY)
                        .setBlobId(BlobModel.BlobId.newBuilder().setValue(ByteString.copyFrom(ByteArray(4) { 1 })))
                )
                .build()
        }

        val result = mapper.map(proto)
        val picture = assertNotNull(result.profilePicture)
        assertEquals(1, picture.renditions.size)
        assertEquals(MediaItemRendition.Role.DISPLAY, picture.renditions.first().role)
    }

    @Test
    fun `no profile picture returns null`() {
        val proto = userProfile { displayName = "Heidi" }
        assertNull(mapper.map(proto).profilePicture)
    }
}
