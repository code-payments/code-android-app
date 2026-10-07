package com.flipcash.shared.chat

import com.flipcash.services.controllers.ChatController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.MediaItem
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class FeaturedGroupsStoreTest {

    private val chatController = mockk<ChatController> {
        coEvery { getChat(any(), any()) } returns Result.failure(Throwable("not stubbed"))
    }
    private val store = FeaturedGroupsStore(chatController)

    private fun group(hex: String) = ChatMetadata(
        chatId = ChatId(hex),
        type = ChatType.GROUP,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochMilliseconds(0),
        title = hex,
    )

    @Test
    fun `starts empty`() {
        assertEquals(emptyList(), store.groups.value)
    }

    @Test
    fun `a load replaces the list with the server's order`() = runTest {
        val groups = listOf(group("bb"), group("aa"))
        coEvery { chatController.getFeaturedGroups("me") } returns Result.success(groups)

        assertTrue(store.load("me"))

        assertEquals(groups, store.groups.value)
    }

    @Test
    fun `a failed load keeps the list already held`() = runTest {
        val held = listOf(group("aa"))
        coEvery { chatController.getFeaturedGroups("me") } returns Result.success(held)
        store.load("me")

        coEvery { chatController.getFeaturedGroups("me") } returns Result.failure(Throwable("offline"))

        assertFalse(store.load("me"))
        assertEquals(held, store.groups.value)
    }

    @Test
    fun `a load fills in the covers the featured list leaves out`() = runTest {
        val cover = mockk<MediaItem>()
        coEvery { chatController.getFeaturedGroups("me") } returns Result.success(listOf(group("aa"), group("bb")))
        coEvery { chatController.getChat(ChatId("aa"), any()) } returns Result.success(group("aa").copy(coverPicture = cover))

        store.load("me")

        assertEquals(cover, store.peek(ChatId("aa"))?.coverPicture)
        // A group whose GetChat failed is still there, without a cover.
        assertEquals(null, store.peek(ChatId("bb"))?.coverPicture)
        assertEquals(group("bb"), store.peek(ChatId("bb")))
    }

    @Test
    fun `replace adopts a saved list and reset empties it`() {
        store.replace(listOf(group("aa")))
        assertEquals(listOf(group("aa")), store.groups.value)

        store.reset()

        assertEquals(emptyList(), store.groups.value)
    }
}
