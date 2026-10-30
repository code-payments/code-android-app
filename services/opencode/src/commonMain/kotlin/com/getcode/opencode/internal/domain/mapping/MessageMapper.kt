package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.messaging.v1.OcpMessagingService
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.internal.network.extensions.toMessageKind
import com.getcode.opencode.model.messaging.MessageKind
import javax.inject.Inject
import com.getcode.opencode.model.messaging.Message as ProtocolMessage
import com.getcode.solana.keys.Signature as SolanaSig

internal class MessageMapper @Inject constructor():
    Mapper<OcpMessagingService.Message, ProtocolMessage> {
    override fun map(from: OcpMessagingService.Message): ProtocolMessage {
        val signature =
            SolanaSig(
                from.sendMessageRequestSignature.value.toByteArray().toList()
            )

        return ProtocolMessage(
            id = from.id.value.toByteArray().toList(),
            signature = signature,
            kind = when(from.kindCase) {
                OcpMessagingService.Message.KindCase.REQUEST_TO_GRAB_BILL -> from.requestToGrabBill.toMessageKind()
                OcpMessagingService.Message.KindCase.REQUEST_TO_GIVE_BILL -> from.requestToGiveBill.toMessageKind()
                OcpMessagingService.Message.KindCase.KIND_NOT_SET -> MessageKind.Unknown
                else -> MessageKind.Unknown
            }
        )
    }
}