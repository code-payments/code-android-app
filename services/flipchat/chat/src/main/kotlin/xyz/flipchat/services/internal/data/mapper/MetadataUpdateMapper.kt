package xyz.flipchat.services.internal.data.mapper

import com.codeinc.flipchat.gen.chat.v1.ChatService
import com.codeinc.flipchat.gen.chat.v1.ChatService.StreamChatEventsResponse.MetadataUpdate as ApiMetadataUpdate
import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.domain.model.chat.StreamMetadataUpdate
import javax.inject.Inject

class MetadataUpdateMapper @Inject constructor(): Mapper<ApiMetadataUpdate, StreamMetadataUpdate?> {
    override fun map(from: ApiMetadataUpdate): StreamMetadataUpdate? {
        println("metadata unread=${from.unreadCountChanged.numUnread}")
        return when (from.kindCase) {
            ChatService.StreamChatEventsResponse.MetadataUpdate.KindCase.FULL_REFRESH -> StreamMetadataUpdate.Refresh(from.fullRefresh.metadata)
            ChatService.StreamChatEventsResponse.MetadataUpdate.KindCase.UNREAD_COUNT_CHANGED -> StreamMetadataUpdate.UnreadCount(from.unreadCountChanged.numUnread, from.unreadCountChanged.hasMoreUnread)
            ChatService.StreamChatEventsResponse.MetadataUpdate.KindCase.DISPLAY_NAME_CHANGED -> StreamMetadataUpdate.DisplayName(from.displayNameChanged.newDisplayName)
            ChatService.StreamChatEventsResponse.MetadataUpdate.KindCase.COVER_CHARGE_CHANGED -> StreamMetadataUpdate.CoverCharge(from.coverChargeChanged.newCoverCharge.quarks.ifZeroOrElse(200) { it / 100_000 })
            ChatService.StreamChatEventsResponse.MetadataUpdate.KindCase.LAST_ACTIVITY_CHANGED -> StreamMetadataUpdate.LastActivity(from.lastActivityChanged.newLastActivity.seconds * 1000L)
            ChatService.StreamChatEventsResponse.MetadataUpdate.KindCase.KIND_NOT_SET -> null
            else -> null
        }
    }
}