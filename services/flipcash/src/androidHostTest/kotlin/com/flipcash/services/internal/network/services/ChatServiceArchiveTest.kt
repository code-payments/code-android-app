package com.flipcash.services.internal.network.services

import com.codeinc.flipcash.gen.chat.v1.ChatService as RpcChatService
import com.codeinc.flipcash.gen.chat.v1.Model as ChatModel
import com.flipcash.services.internal.network.api.ChatApi
import com.flipcash.services.models.ArchiveChatError
import com.flipcash.services.models.UnarchiveChatError
import com.flipcash.services.models.chat.ChatId
import com.getcode.ed25519kmp.Ed25519
import com.google.protobuf.Timestamp
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatServiceArchiveTest {

    private val api = mockk<ChatApi>()
    private val service = ChatService(api)
    private val owner = mockk<Ed25519.KeyPair>(relaxed = true)
    private val chatId = ChatId(ByteArray(32) { 7 })

    private val archivedViewerState = ChatModel.ViewerState.newBuilder()
        .setVersion(4)
        .setSettings(
            ChatModel.ViewerState.Settings.newBuilder()
                .setArchive(
                    ChatModel.ArchiveState.newBuilder()
                        .setArchivedAt(Timestamp.newBuilder().setSeconds(1_700_000_000))
                )
        )
        .build()

    // region archiveChat

    @Test
    fun `archiveChat OK returns the server's viewer state`() = runTest {
        coEvery { api.archiveChat(any(), any()) } returns
            RpcChatService.ArchiveChatResponse.newBuilder()
                .setResult(RpcChatService.ArchiveChatResponse.Result.OK)
                .setViewerState(archivedViewerState)
                .build()

        val result = service.archiveChat(owner, chatId)

        assertEquals(4, result.getOrThrow().version)
        assertTrue(result.getOrThrow().settings.hasArchive())
    }

    @Test
    fun `archiveChat DENIED returns Denied`() = runTest {
        coEvery { api.archiveChat(any(), any()) } returns
            RpcChatService.ArchiveChatResponse.newBuilder()
                .setResult(RpcChatService.ArchiveChatResponse.Result.DENIED)
                .build()

        assertIs<ArchiveChatError.Denied>(service.archiveChat(owner, chatId).exceptionOrNull())
    }

    @Test
    fun `archiveChat NOT_FOUND returns NotFound`() = runTest {
        coEvery { api.archiveChat(any(), any()) } returns
            RpcChatService.ArchiveChatResponse.newBuilder()
                .setResult(RpcChatService.ArchiveChatResponse.Result.NOT_FOUND)
                .build()

        assertIs<ArchiveChatError.NotFound>(service.archiveChat(owner, chatId).exceptionOrNull())
    }

    @Test
    fun `archiveChat on a server without the RPC returns Unimplemented`() = runTest {
        coEvery { api.archiveChat(any(), any()) } throws StatusException(Status.UNIMPLEMENTED)

        assertIs<ArchiveChatError.Unimplemented>(service.archiveChat(owner, chatId).exceptionOrNull())
    }

    @Test
    fun `archiveChat UNIMPLEMENTED as a runtime status is still Unimplemented`() = runTest {
        coEvery { api.archiveChat(any(), any()) } throws StatusRuntimeException(Status.UNIMPLEMENTED)

        assertIs<ArchiveChatError.Unimplemented>(service.archiveChat(owner, chatId).exceptionOrNull())
    }

    @Test
    fun `archiveChat transport failure is Other, not Unimplemented`() = runTest {
        coEvery { api.archiveChat(any(), any()) } throws StatusException(Status.UNAVAILABLE)

        assertIs<ArchiveChatError.Other>(service.archiveChat(owner, chatId).exceptionOrNull())
    }

    // endregion

    // region unarchiveChat

    @Test
    fun `unarchiveChat OK returns the viewer state and the chat`() = runTest {
        val metadata = ChatModel.Metadata.newBuilder().setTitle("crew").build()
        coEvery { api.unarchiveChat(any(), any()) } returns
            RpcChatService.UnarchiveChatResponse.newBuilder()
                .setResult(RpcChatService.UnarchiveChatResponse.Result.OK)
                .setViewerState(ChatModel.ViewerState.newBuilder().setVersion(5))
                .setChat(metadata)
                .build()

        val response = service.unarchiveChat(owner, chatId).getOrThrow()

        assertEquals(5, response.viewerState.version)
        assertTrue(!response.viewerState.settings.hasArchive())
        assertEquals("crew", response.chat.title)
    }

    @Test
    fun `unarchiveChat DENIED returns Denied`() = runTest {
        coEvery { api.unarchiveChat(any(), any()) } returns
            RpcChatService.UnarchiveChatResponse.newBuilder()
                .setResult(RpcChatService.UnarchiveChatResponse.Result.DENIED)
                .build()

        assertIs<UnarchiveChatError.Denied>(service.unarchiveChat(owner, chatId).exceptionOrNull())
    }

    @Test
    fun `unarchiveChat NOT_FOUND returns NotFound`() = runTest {
        coEvery { api.unarchiveChat(any(), any()) } returns
            RpcChatService.UnarchiveChatResponse.newBuilder()
                .setResult(RpcChatService.UnarchiveChatResponse.Result.NOT_FOUND)
                .build()

        assertIs<UnarchiveChatError.NotFound>(service.unarchiveChat(owner, chatId).exceptionOrNull())
    }

    @Test
    fun `unarchiveChat on a server without the RPC returns Unimplemented`() = runTest {
        coEvery { api.unarchiveChat(any(), any()) } throws StatusException(Status.UNIMPLEMENTED)

        assertIs<UnarchiveChatError.Unimplemented>(service.unarchiveChat(owner, chatId).exceptionOrNull())
    }

    // endregion
}
