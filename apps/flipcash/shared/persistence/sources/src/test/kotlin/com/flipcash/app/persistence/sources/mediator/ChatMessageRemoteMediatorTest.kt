package com.flipcash.app.persistence.sources.mediator

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.flipcash.app.persistence.entities.ChatMessageEntity
import com.flipcash.app.persistence.sources.ChatMessageDataSource
import com.flipcash.services.controllers.ChatMessagingController
import com.flipcash.services.models.chat.ChatId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * A failed older-page fetch has to reach Paging as an error. Reported as "end of pagination" it
 * reads as the start of the chat: older messages stop loading and Paging never asks again.
 */
@OptIn(ExperimentalPagingApi::class)
class ChatMessageRemoteMediatorTest {

    private val chatId = ChatId("01")
    private val controller = mockk<ChatMessagingController>()
    private val dataSource = mockk<ChatMessageDataSource>(relaxed = true)

    private fun mediator() = ChatMessageRemoteMediator(chatId, controller, dataSource)

    private val oldest = mockk<ChatMessageEntity> { every { messageId } returns 42L }

    private val loadedState = PagingState(
        pages = listOf(
            PagingSource.LoadResult.Page<Int, ChatMessageEntity>(
                data = listOf(oldest),
                prevKey = null,
                nextKey = null,
            ),
        ),
        anchorPosition = null,
        config = PagingConfig(pageSize = 20),
        leadingPlaceholderCount = 0,
    )

    @Test
    fun `a failed fetch is an error, not the end of the chat`() = runTest {
        val failure = java.io.IOException("offline")
        coEvery { controller.getMessages(chatId, any()) } returns Result.failure(failure)

        val result = mediator().load(LoadType.APPEND, loadedState)

        assertSame(failure, (result as RemoteMediator.MediatorResult.Error).throwable)
        coVerify(exactly = 0) { dataSource.upsert(any(), any()) }
    }

    @Test
    fun `an empty page is the end of the chat`() = runTest {
        coEvery { controller.getMessages(chatId, any()) } returns Result.success(emptyList())

        val result = mediator().load(LoadType.APPEND, loadedState)

        assertEquals(true, (result as RemoteMediator.MediatorResult.Success).endOfPaginationReached)
    }

    @Test
    fun `cancellation is rethrown, not reported as an error`() = runTest {
        coEvery { controller.getMessages(chatId, any()) } throws CancellationException("scope gone")

        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { mediator().load(LoadType.APPEND, loadedState) }
        }
    }
}
