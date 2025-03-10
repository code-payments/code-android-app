package xyz.flipchat.services.internal.protomapping

import com.codeinc.flipchat.gen.messaging.v1.Model
import com.getcode.model.ID
import com.getcode.model.chat.AnnouncementAction
import com.getcode.model.chat.MessageContent
import xyz.flipchat.services.internal.data.mapper.ifZeroOrElse

operator fun MessageContent.Companion.invoke(
    proto: Model.Content,
    senderId: ID,
    isFromSelf: Boolean = false,
): MessageContent? {
    return when (proto.typeCase) {
        Model.Content.TypeCase.LOCALIZED_ANNOUNCEMENT -> MessageContent.Announcement(
            isFromSelf = isFromSelf,
            value = proto.localizedAnnouncement.keyOrText
        )

        Model.Content.TypeCase.ACTIONABLE_ANNOUNCEMENT -> MessageContent.ActionableAnnouncement(
            isFromSelf = isFromSelf,
            keyOrText = proto.actionableAnnouncement.keyOrText,
            action = when (proto.actionableAnnouncement.action.typeCase) {
                Model.ActionableAnnouncementContent.Action.TypeCase.SHARE_ROOM_LINK -> AnnouncementAction.Share
                Model.ActionableAnnouncementContent.Action.TypeCase.TYPE_NOT_SET,
                null -> AnnouncementAction.Unknown
            }
        )

        Model.Content.TypeCase.TEXT -> MessageContent.RawText(
            isFromSelf = isFromSelf,
            value = proto.text.text
        )

        Model.Content.TypeCase.REACTION -> MessageContent.Reaction(
            emoji = proto.reaction.emoji,
            originalMessageId = proto.reaction.originalMessageId.value.toList(),
            senderId = senderId,
            isFromSelf = isFromSelf
        )

        Model.Content.TypeCase.REPLY -> MessageContent.Reply(
            text = proto.reply.replyText,
            originalMessageId = proto.reply.originalMessageId.value.toList(),
            isFromSelf = isFromSelf
        )

        Model.Content.TypeCase.DELETED -> MessageContent.DeletedMessage(
            originalMessageId = proto.deleted.originalMessageId.value.toList(),
            messageDeleter = senderId,
            isFromSelf = isFromSelf
        )

        Model.Content.TypeCase.TIP -> MessageContent.MessageTip(
            originalMessageId = proto.tip.originalMessageId.value.toList(),
            tipperId = senderId,
            isFromSelf = isFromSelf,
            amountInQuarks = proto.tip.tipAmount.quarks / 100_000
        )

        Model.Content.TypeCase.TYPE_NOT_SET -> return null
        else -> return null
    }
}