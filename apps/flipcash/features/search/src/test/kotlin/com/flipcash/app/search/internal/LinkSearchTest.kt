package com.flipcash.app.search.internal

import com.flipcash.services.models.chat.LinkPreview
import com.flipcash.services.models.chat.MessageContent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkSearchTest {

    @Test
    fun `trailing sentence punctuation is not part of a url`() {
        assertEquals(
            listOf("https://x.com/a", "http://y.org"),
            extractWebUrls("see https://x.com/a. and (http://y.org)!"),
        )
    }

    @Test
    fun `links without an http scheme are not collected`() {
        assertEquals(emptyList(), extractWebUrls("flipcash://c/abc mailto:a@b.com x.com"))
    }

    @Test
    fun `third-party links are kept with their host`() {
        assertEquals("nytimes.com", searchableLinkHost("https://nytimes.com/2026/story"))
        assertEquals("www.youtube.com", searchableLinkHost("https://www.youtube.com/watch?v=1"))
    }

    @Test
    fun `cash links are excluded`() {
        assertNull(searchableLinkHost("https://send.flipcash.com/c/#/e=abc"))
        assertNull(searchableLinkHost("https://app.flipcash.com/cash/#/e=abc"))
    }

    @Test
    fun `group invites and chat message links are excluded`() {
        assertNull(searchableLinkHost("https://app.flipcash.com/chat/6f1c2a"))
        assertNull(searchableLinkHost("https://app.flipcash.com/chat/6f1c2a/m/12"))
    }

    @Test
    fun `tip card and profile links are excluded`() {
        assertNull(searchableLinkHost("https://flipcash.com/tip/abc"))
        assertNull(searchableLinkHost("https://flipcash.com/ted"))
        assertNull(searchableLinkHost("https://www.flipcash.com/2f9a7e0c-1111-4a2b-9c3d-123456789abc"))
    }

    @Test
    fun `currency links are excluded`() {
        assertNull(searchableLinkHost("https://app.flipcash.com/token/So11111111111111111111111111111111111111112"))
    }

    @Test
    fun `every flipcash host is excluded, not just known paths`() {
        assertNull(searchableLinkHost("https://jump.flipcash.com/login?x=1"))
    }

    @Test
    fun `a lookalike host is not mistaken for flipcash`() {
        assertEquals("evilflipcash.com", searchableLinkHost("https://evilflipcash.com/c/abc"))
    }

    @Test
    fun `collected links drop flipcash links and keep the newest share of each url`() {
        val links = collectLinks(
            listOf(
                stored(3, text("again https://x.com/a")),
                stored(2, text("https://x.com/a and https://app.flipcash.com/token/abc"), chatId = ChatB),
                stored(1, text("https://send.flipcash.com/c/#/e=1 https://www.blog.dev/post")),
            ),
        )
        assertEquals(listOf("https://x.com/a", "https://www.blog.dev/post"), links.map { it.url })
        assertEquals(3, links.first().messageId)
        assertEquals("blog.dev", links.last().host)
    }

    @Test
    fun `links in replies and media captions are collected`() {
        val links = collectLinks(
            listOf(
                stored(2, MessageContent.Reply(1, listOf(text("https://a.com")))),
                stored(1, MessageContent.Media(emptyList(), text("https://b.com"))),
            ),
        )
        assertEquals(listOf("https://a.com", "https://b.com"), links.map { it.url })
    }

    @Test
    fun `a preview is kept only for the url it describes`() {
        val preview = LinkPreview(url = "https://a.com", title = "A")
        val links = collectLinks(
            listOf(stored(1, MessageContent.Text("https://a.com https://b.com", preview))),
        )
        assertEquals(preview, links.single { it.url == "https://a.com" }.preview)
        assertNull(links.single { it.url == "https://b.com" }.preview)
    }

    @Test
    fun `links match on title, sharer and chat name`() {
        val links = collectLinks(
            listOf(
                stored(2, MessageContent.Text("https://a.com", LinkPreview("https://a.com", "Weekly recipes"))),
                stored(1, text("https://b.com"), chatId = ChatB),
            ),
        )
        fun find(query: String) = filterLinks(
            links = links,
            query = SearchMatching.query(query)!!,
            sharerName = { "Alice" },
            chatName = { if (it == ChatB) "Book club" else "Ted" },
        ).map { it.url }

        assertEquals(listOf("https://a.com"), find("recipe"))
        assertEquals(listOf("https://b.com"), find("book"))
        assertEquals(listOf("https://a.com", "https://b.com"), find("alice"))
        assertTrue(find("nothing").isEmpty())
    }

    @Test
    fun `a currency link gives its mint`() {
        assertEquals("abc", currencyMintOf("https://app.flipcash.com/token/abc?ref=1"))
        assertNull(currencyMintOf("https://app.flipcash.com/chat/abc"))
        assertNull(currencyMintOf("https://example.com/token/abc"))
    }

    @Test
    fun `the newest message linking a currency is its share`() {
        val shares = latestCurrencyShares(
            listOf(
                stored(2, text("https://app.flipcash.com/token/abc")),
                stored(1, text("https://app.flipcash.com/token/abc https://app.flipcash.com/token/def")),
            ),
        )
        assertEquals(2, shares.getValue("abc").message.messageId)
        assertEquals(1, shares.getValue("def").message.messageId)
    }
}
