package com.flipcash.shared.chat.ui

import com.flipcash.services.models.chat.LinkPreview
import com.flipcash.services.models.chat.MessageContent
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the photo bubble's caption is handed to draw pills from. Robolectric for [detectUrls]. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MediaCaptionMentionTest {

    private fun photo(caption: String?) =
        MessageContent.Media(items = emptyList(), caption = caption?.let(MessageContent::Text))

    @Test
    fun `a photo caption yields its mentions`() {
        val mentions = photo("cc @jeff @mcansh?").detectedMentions()
        assertEquals(listOf("jeff", "mcansh"), mentions.map { it.username })
        assertEquals(3 to 8, mentions.first().let { it.start to it.end })
    }

    @Test
    fun `a photo reply yields the caption's mentions, not the reply text's`() {
        val reply = MessageContent.Reply(
            repliedMessageId = 1L,
            content = listOf(photo("hi @jeff"), MessageContent.Text("@other")),
        )
        assertEquals(listOf("jeff"), reply.detectedMentions().map { it.username })
    }

    @Test
    fun `a photo with no caption has no mentions`() {
        assertEquals(emptyList(), photo(null).detectedMentions())
    }

    @Test
    fun `a media caption is the link text, as a text body is`() {
        val url = "see https://a.com/x ok"
        assertEquals(url, photo(url).linkableText())
        assertEquals(
            detectUrls(MessageContent.Text(url).linkableText().orEmpty()),
            detectUrls(photo(url).linkableText().orEmpty()),
        )
        assertEquals(null, photo(null).linkableText())
    }

    @Test
    fun `a photo reply's link text is the caption, never the reply's own text`() {
        val reply = MessageContent.Reply(
            repliedMessageId = 1L,
            content = listOf(photo("cap https://a.com"), MessageContent.Text("https://other.com")),
        )
        assertEquals("cap https://a.com", reply.linkableText())
        val uncaptioned = MessageContent.Reply(
            repliedMessageId = 1L,
            content = listOf(photo(null), MessageContent.Text("https://other.com")),
        )
        assertEquals(null, uncaptioned.linkableText())
    }

    @Test
    fun `a text reply still reads its first text`() {
        val reply = MessageContent.Reply(
            repliedMessageId = 1L,
            content = listOf(MessageContent.Text("a"), MessageContent.Text("b")),
        )
        assertEquals("a", reply.linkableText())
    }

    @Test
    fun `a caption's sender-built preview is the content's preview`() {
        val preview = LinkPreview(url = "https://a.com", title = "A")
        val media = MessageContent.Media(items = emptyList(), caption = MessageContent.Text("https://a.com", preview))
        assertEquals(preview, media.linkPreview())
        assertEquals(
            preview,
            MessageContent.Reply(repliedMessageId = 1L, content = listOf(media)).linkPreview(),
        )
        assertEquals(null, photo("https://a.com").linkPreview())
    }
}
