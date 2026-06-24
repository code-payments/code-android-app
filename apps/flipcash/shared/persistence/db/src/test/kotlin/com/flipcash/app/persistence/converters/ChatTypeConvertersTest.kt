package com.flipcash.app.persistence.converters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatTypeConvertersTest {

    private val converter = ChatTypeConverters()

    // region ReactionSummary

    @Test
    fun `fromReactionSummary and toReactionSummary roundtrip`() {
        val original = ReactionSummarySerialized(
            messageId = 42L,
            reactions = listOf(
                EmojiReactionSerialized(
                    emoji = "\uD83D\uDE00",
                    count = 3,
                    reactedBySelf = true,
                    sampleReactors = listOf(
                        ReactorSerialized(userIdHex = "aabb", reactedAtEpochSeconds = 1000L),
                        ReactorSerialized(userIdHex = "ccdd", reactedAtEpochSeconds = 2000L),
                    ),
                    sequence = 5,
                ),
                EmojiReactionSerialized(
                    emoji = "\uD83D\uDC4D",
                    count = 1,
                    reactedBySelf = false,
                    sampleReactors = emptyList(),
                    sequence = 3,
                ),
            ),
        )
        val serialized = converter.toReactionSummary(original)
        val deserialized = converter.fromReactionSummary(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `fromReactionSummary returns null for null input`() {
        assertNull(converter.fromReactionSummary(null))
    }

    @Test
    fun `toReactionSummary returns null for null input`() {
        assertNull(converter.toReactionSummary(null))
    }

    @Test
    fun `roundtrip with empty reactions list`() {
        val original = ReactionSummarySerialized(
            messageId = 1L,
            reactions = emptyList(),
        )
        val serialized = converter.toReactionSummary(original)
        val deserialized = converter.fromReactionSummary(serialized)
        assertEquals(original, deserialized)
    }

    // endregion

    // region MessageContent

    @Test
    fun `fromMessageContentList and toMessageContentList roundtrip text`() {
        val original = listOf(MessageContentSerialized.Text("hello"))
        val serialized = converter.toMessageContentList(original)
        val deserialized = converter.fromMessageContentList(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `fromMessageContentList returns null for null input`() {
        assertNull(converter.fromMessageContentList(null))
    }

    @Test
    fun `toMessageContentList returns null for null input`() {
        assertNull(converter.toMessageContentList(null))
    }

    // endregion

    // region MessageStatus

    @Test
    fun `fromMessageStatus and toMessageStatus roundtrip`() {
        for (status in com.flipcash.app.persistence.entities.MessageStatus.entries) {
            val serialized = converter.fromMessageStatus(status)
            val deserialized = converter.toMessageStatus(serialized)
            assertEquals(status, deserialized)
        }
    }

    @Test
    fun `toMessageStatus falls back to SENT for unknown value`() {
        val result = converter.toMessageStatus("NONEXISTENT")
        assertEquals(com.flipcash.app.persistence.entities.MessageStatus.SENT, result)
    }

    // endregion

    // region UserProfile

    @Test
    fun `fromUserProfile and toUserProfile roundtrip`() {
        val original = UserProfileSerialized(
            displayName = "Alice",
            socialAccounts = listOf(
                SocialAccountSerialized.TwitterX(
                    id = "123",
                    username = "alice",
                    name = "Alice",
                    description = "hello",
                    profilePicUrl = "https://example.com/pic.jpg",
                    verifiedType = "BLUE",
                    followerCount = 100,
                )
            ),
            verifiedPhoneNumber = "+1234567890",
            verifiedEmailAddress = "alice@example.com",
        )
        val serialized = converter.toUserProfile(original)
        val deserialized = converter.fromUserProfile(serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `fromUserProfile returns null for null input`() {
        assertNull(converter.fromUserProfile(null))
    }

    @Test
    fun `toUserProfile returns null for null input`() {
        assertNull(converter.toUserProfile(null))
    }

    // endregion
}
