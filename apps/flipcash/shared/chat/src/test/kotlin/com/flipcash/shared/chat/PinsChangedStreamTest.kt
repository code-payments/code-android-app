package com.flipcash.shared.chat

import com.flipcash.app.persistence.sources.ChatMetadataDataSource
import com.flipcash.services.controllers.EventStreamingController
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.MetadataUpdate
import com.flipcash.services.models.chat.PinChange
import com.flipcash.services.models.chat.PinSummary
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.delegates.EventStreamDelegate
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import org.junit.Test

/**
 * A `PinsChanged` event hands its summary to the data source, which applies it only when strictly
 * newer than the held one (see `PinSummaryHolderTest` for that rule).
 */
class PinsChangedStreamTest {

    @Test
    fun `a pins changed event offers its summary to the held pins`() {
        val chatId = ChatId("aabbccdd")
        val summary = PinSummary(count = 1, version = 7)
        val update = ChatUpdate(
            chatId = chatId,
            metadataUpdates = listOf(
                MetadataUpdate.PinsChanged(change = PinChange.Unpinned(messageId = 9), summary = summary),
            ),
        )
        val streaming = mockk<EventStreamingController>(relaxed = true)
        every { streaming.isConnected } returns true
        every { streaming.chatUpdates } returns flowOf(update)
        val metadata = mockk<ChatMetadataDataSource>(relaxed = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        try {
            EventStreamDelegate(
                eventStreamingController = streaming,
                messagingController = mockk(relaxed = true),
                metadataDataSource = metadata,
                messageDataSource = mockk(relaxed = true),
                memberDataSource = mockk(relaxed = true),
                tokenCoordinator = mockk(relaxed = true),
                userManager = mockk(relaxed = true),
                stateHolder = ChatStateHolder(),
                analytics = mockk(relaxed = true),
                exchange = mockk(relaxed = true),
            ).apply {
                initialize(scope)
                open()
            }

            io.mockk.verify(timeout = 5_000) { metadata.updatePinSummary(chatId, summary) }
        } finally {
            scope.cancel()
        }
    }
}
