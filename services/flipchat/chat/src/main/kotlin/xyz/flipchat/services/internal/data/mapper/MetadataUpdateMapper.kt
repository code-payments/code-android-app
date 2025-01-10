package xyz.flipchat.services.internal.data.mapper

import com.codeinc.flipchat.gen.chat.v1.ChatService
import com.codeinc.flipchat.gen.chat.v1.ChatService.MetadataUpdate as ApiMetadataUpdate
import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.domain.model.chat.StreamMetadataUpdate
import javax.inject.Inject

class MetadataUpdateMapper @Inject constructor(): Mapper<ApiMetadataUpdate, StreamMetadataUpdate?> {
    override fun map(from: ApiMetadataUpdate): StreamMetadataUpdate? {
        return when (from.kindCase) {
            ChatService.MetadataUpdate.KindCase.FULL_REFRESH -> StreamMetadataUpdate.Refresh(from.fullRefresh.metadata)
            ChatService.MetadataUpdate.KindCase.UNREAD_COUNT_CHANGED -> StreamMetadataUpdate.UnreadCount(from.unreadCountChanged.numUnread, from.unreadCountChanged.hasMoreUnread)
            ChatService.MetadataUpdate.KindCase.DISPLAY_NAME_CHANGED -> StreamMetadataUpdate.DisplayName(from.displayNameChanged.newDisplayName)
            ChatService.MetadataUpdate.KindCase.COVER_CHARGE_CHANGED -> StreamMetadataUpdate.CoverCharge(from.coverChargeChanged.newCoverCharge.quarks.ifZeroOrElse(200) { it / 100_000 })
            ChatService.MetadataUpdate.KindCase.LAST_ACTIVITY_CHANGED -> StreamMetadataUpdate.LastActivity(from.lastActivityChanged.newLastActivity.seconds * 1000L)
            ChatService.MetadataUpdate.KindCase.KIND_NOT_SET -> null
            else -> null
        }
    }
}