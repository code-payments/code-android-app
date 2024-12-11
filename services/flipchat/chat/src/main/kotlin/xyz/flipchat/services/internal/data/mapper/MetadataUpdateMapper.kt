package xyz.flipchat.services.internal.data.mapper

import com.codeinc.flipchat.gen.chat.v1.FlipchatService
import com.codeinc.flipchat.gen.chat.v1.FlipchatService.StreamChatEventsResponse.MetadataUpdate as ApiMetadataUpdate
import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.domain.model.chat.StreamMetadataUpdate
import javax.inject.Inject

class MetadataUpdateMapper @Inject constructor(): Mapper<ApiMetadataUpdate, StreamMetadataUpdate?> {
    override fun map(from: ApiMetadataUpdate): StreamMetadataUpdate? {
        return when (from.kindCase) {
            FlipchatService.StreamChatEventsResponse.MetadataUpdate.KindCase.FULL_REFRESH -> StreamMetadataUpdate.Refresh(from.fullRefresh.metadata)
            FlipchatService.StreamChatEventsResponse.MetadataUpdate.KindCase.UNREAD_COUNT_CHANGED -> StreamMetadataUpdate.UnreadCount(from.unreadCountChanged.numUnread, from.unreadCountChanged.hasMoreUnread)
            FlipchatService.StreamChatEventsResponse.MetadataUpdate.KindCase.DISPLAY_NAME_CHANGED -> StreamMetadataUpdate.DisplayName(from.displayNameChanged.newDisplayName)
            FlipchatService.StreamChatEventsResponse.MetadataUpdate.KindCase.COVER_CHARGE_CHANGED -> StreamMetadataUpdate.CoverCharge(from.coverChargeChanged.newCoverCharge.quarks.ifZeroOrElse(200) { it / 100_000 })
            FlipchatService.StreamChatEventsResponse.MetadataUpdate.KindCase.LAST_ACTIVITY_CHANGED -> StreamMetadataUpdate.LastActivity(from.lastActivityChanged.newLastActivity.seconds * 1000L)
            FlipchatService.StreamChatEventsResponse.MetadataUpdate.KindCase.KIND_NOT_SET -> null
            else -> null
        }
    }
}