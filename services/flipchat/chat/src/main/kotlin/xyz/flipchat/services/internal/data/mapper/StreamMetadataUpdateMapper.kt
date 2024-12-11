package xyz.flipchat.services.internal.data.mapper

import com.getcode.model.ID
import com.getcode.services.mapper.Mapper
import xyz.flipchat.services.domain.mapper.RoomConversationMapper
import xyz.flipchat.services.domain.model.chat.StreamMetadataUpdate
import xyz.flipchat.services.domain.model.chat.db.ConversationUpdate
import javax.inject.Inject

class StreamMetadataUpdateMapper @Inject constructor(
    private val metadataMapper: MetadataRoomMapper,
    private val conversationMapper: RoomConversationMapper,
): Mapper<Pair<ID, StreamMetadataUpdate>, ConversationUpdate> {
    override fun map(from: Pair<ID, StreamMetadataUpdate>): ConversationUpdate {
        val (id, update) = from
        return when (update) {
            is StreamMetadataUpdate.CoverCharge -> ConversationUpdate.CoverCharge(id, update.amount)
            is StreamMetadataUpdate.DisplayName -> ConversationUpdate.DisplayName(id, update.name)
            is StreamMetadataUpdate.LastActivity -> ConversationUpdate.LastActivity(id, update.timestamp)
            is StreamMetadataUpdate.Refresh -> ConversationUpdate.Refresh(conversationMapper.map(metadataMapper.map(update.metadata)))
            is StreamMetadataUpdate.UnreadCount -> ConversationUpdate.UnreadCount(id, update.numUnread, update.hasMoreUnread)
        }
    }
}