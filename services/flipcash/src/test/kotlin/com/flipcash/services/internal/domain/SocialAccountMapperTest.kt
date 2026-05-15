package com.flipcash.services.internal.domain

import com.codeinc.flipcash.gen.profile.v1.socialProfile
import com.codeinc.flipcash.gen.profile.v1.xProfile
import com.codeinc.flipcash.gen.profile.v1.Model
import com.flipcash.services.models.SocialAccount
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SocialAccountMapperTest {

    private val mapper = SocialAccountMapper()

    @Test
    fun `maps X profile fields`() {
        val proto = socialProfile {
            x = xProfile {
                id = "123"
                username = "testuser"
                name = "Test User"
                description = "Bio text"
                profilePicUrl = "https://example.com/pic.jpg"
                followerCount = 5000
            }
        }

        val result = mapper.map(proto)

        assertIs<SocialAccount.TwitterX>(result)
        assertEquals("123", result.id)
        assertEquals("testuser", result.username)
        assertEquals("Test User", result.name)
        assertEquals("Bio text", result.description)
        assertEquals("https://example.com/pic.jpg", result.profilePicUrl)
        assertEquals(5000, result.followerCount)
    }

    @Test
    fun `maps verified type NONE`() {
        val proto = socialProfile {
            x = xProfile {
                verifiedType = Model.XProfile.VerifiedType.NONE
            }
        }

        val result = mapper.map(proto) as SocialAccount.TwitterX
        assertEquals(SocialAccount.TwitterX.VerifiedType.NONE, result.verifiedType)
    }

    @Test
    fun `maps verified type BLUE`() {
        val proto = socialProfile {
            x = xProfile {
                verifiedType = Model.XProfile.VerifiedType.BLUE
            }
        }

        val result = mapper.map(proto) as SocialAccount.TwitterX
        assertEquals(SocialAccount.TwitterX.VerifiedType.BLUE, result.verifiedType)
    }

    @Test
    fun `maps verified type BUSINESS`() {
        val proto = socialProfile {
            x = xProfile {
                verifiedType = Model.XProfile.VerifiedType.BUSINESS
            }
        }

        val result = mapper.map(proto) as SocialAccount.TwitterX
        assertEquals(SocialAccount.TwitterX.VerifiedType.BUSINESS, result.verifiedType)
    }

    @Test
    fun `maps verified type GOVERNMENT`() {
        val proto = socialProfile {
            x = xProfile {
                verifiedType = Model.XProfile.VerifiedType.GOVERNMENT
            }
        }

        val result = mapper.map(proto) as SocialAccount.TwitterX
        assertEquals(SocialAccount.TwitterX.VerifiedType.GOVERNMENT, result.verifiedType)
    }

    @Test
    fun `TYPE_NOT_SET returns null`() {
        val proto = socialProfile { }

        val result = mapper.map(proto)

        assertNull(result)
    }
}
