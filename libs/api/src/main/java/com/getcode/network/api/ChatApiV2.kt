package com.getcode.network.api

import com.codeinc.gen.chat.v2.ChatService
import com.codeinc.gen.chat.v2.ChatService.MemberIdentity
import com.codeinc.gen.chat.v2.ChatService.Content
import com.codeinc.gen.chat.v2.ChatService.NotifyIsTypingRequest
import com.codeinc.gen.chat.v2.ChatService.NotifyIsTypingResponse
import com.codeinc.gen.chat.v2.ChatService.PointerType
import com.codeinc.gen.chat.v2.ChatService.SendMessageRequest
import com.codeinc.gen.chat.v2.ChatService.SendMessageResponse
import com.codeinc.gen.common.v1.Model
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.model.Cursor
import com.getcode.model.ID
import com.getcode.model.SocialUser
import com.getcode.model.chat.ChatMember
import com.getcode.model.chat.OutgoingMessageContent
import com.getcode.model.chat.Platform
import com.getcode.model.chat.StartChatRequest
import com.getcode.model.chat.StartChatResponse
import com.getcode.network.core.GrpcApi
import com.getcode.network.repository.toByteString
import com.getcode.network.repository.toSolanaAccount
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.bytes
import com.getcode.utils.network.retryable
import com.getcode.utils.sign
import io.grpc.ManagedChannel
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import java.util.UUID
import javax.inject.Inject
import com.getcode.model.chat.AdvancePointerRequestV2 as AdvancePointerRequest
import com.getcode.model.chat.AdvancePointerResponseV2 as AdvancePointerResponse
import com.getcode.model.chat.ChatCursorV2 as ChatCursor
import com.getcode.model.chat.ChatGrpcV2 as ChatGrpc
import com.getcode.model.chat.ChatIdV2 as ChatId
import com.getcode.model.chat.GetChatsRequestV2 as GetChatsRequest
import com.getcode.model.chat.GetChatsResponseV2 as GetChatsResponse
import com.getcode.model.chat.GetMessagesDirectionV2 as GetMessagesDirection
import com.getcode.model.chat.GetMessagesRequestV2 as GetMessagesRequest
import com.getcode.model.chat.GetMessagesResponseV2 as GetMessagesResponse
import com.getcode.model.chat.ModelIntentId as IntentId
import com.getcode.model.chat.PointerV2 as Pointer
import com.getcode.model.chat.SetMuteStateRequestV2 as SetMuteStateRequest
import com.getcode.model.chat.SetMuteStateResponseV2 as SetMuteStateResponse

class ChatApiV2 @Inject constructor(
    managedChannel: ManagedChannel
) : GrpcApi(managedChannel) {
    private val api = ChatGrpc.newStub(managedChannel).withWaitForReady()

    fun startChat(
        owner: KeyPair,
        self: SocialUser,
        with: SocialUser,
        intentId: ID
    ): Flow<StartChatResponse> {

        val request = StartChatRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .setTwoWayChat(
                ChatService.StartTwoWayChatParameters.newBuilder()
                    .setIntentId(IntentId.newBuilder().setValue(intentId.toByteString()))
                    .setOtherUser(with.tipAddress.bytes.toSolanaAccount())
                    .build()
            )
            .apply { setSignature(sign(owner)) }
            .build()

        return api::startChat
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun fetchChats(owner: KeyPair): Flow<GetChatsResponse> {
        val request = GetChatsRequest.newBuilder()
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .apply { setSignature(sign(owner)) }
            .build()

        return api::getChats
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun fetchChatMessages(
        owner: KeyPair,
        chatId: ID,
        cursor: Cursor? = null,
        limit: Int? = null
    ): Flow<GetMessagesResponse> {
        val builder = GetMessagesRequest.newBuilder()
            .setChatId(
                ChatId.newBuilder()
                    .setValue(chatId.toByteString())
                    .build()
            )

        if (cursor != null) {
            builder.setCursor(
                ChatCursor.newBuilder()
                    .setValue(cursor.toByteString())
            )
        }

        if (limit != null) {
            builder.setPageSize(limit)
        }

        builder.setDirection(GetMessagesDirection.DESC)

        val request = builder
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .apply { setSignature(sign(owner)) }
            .build()

        return api::getMessages
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun advancePointer(owner: KeyPair, chatId: ID, memberId: ID, to: ID, type: PointerType): Flow<AdvancePointerResponse> {
        val request = AdvancePointerRequest.newBuilder()
            .setChatId(
                ChatId.newBuilder()
                    .setValue(chatId.toByteArray().toByteString())
                    .build()
            ).setPointer(
                Pointer.newBuilder()
                    .setType(type)
                    .setMemberId(ChatService.MemberId.newBuilder()
                        .setValue(memberId.toByteString()))
                    .setValue(
                        ChatService.MessageId.newBuilder()
                            .setValue(to.toByteArray().toByteString())
                    )
            ).setOwner(owner.publicKeyBytes.toSolanaAccount())
            .apply { setSignature(sign(owner)) }
            .build()

        return api::advancePointer
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun setMuteState(owner: KeyPair, chatId: ID, muted: Boolean): Flow<SetMuteStateResponse> {
        val request = SetMuteStateRequest.newBuilder()
            .setChatId(
                ChatId.newBuilder()
                    .setValue(chatId.toByteArray().toByteString())
                    .build()
            ).setIsMuted(muted)
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .apply { setSignature(sign(owner)) }
            .build()

        return api::setMuteState
            .callAsCancellableFlow(request)
            .flowOn(Dispatchers.IO)
    }

    fun streamChatEvents(
        observer: StreamObserver<ChatService.StreamChatEventsResponse>
    ): StreamObserver<ChatService.StreamChatEventsRequest>? {
        return api.streamChatEvents(observer)
    }


    /**
     *     ChatId chat_id = 1;
     *
     *     ChatMemberId member_id = 2;
     *
     *     // Allowed content types that can be sent by client:
     *     //  - TextContent
     *     //  - ThankYouContent
     *     repeated Content content = 3;
     *
     *     common.v1.SolanaAccountId owner = 4;
     *
     *     common.v1.Signature signature = 5;
     */
    fun sendMessage(
        owner: KeyPair,
        chatId: ID,
        content: OutgoingMessageContent,
        observer: StreamObserver<SendMessageResponse>
    ) {
        val contentProto = when (content) {
            is OutgoingMessageContent.Text -> Content.newBuilder()
                .setText(ChatService.TextContent.newBuilder().setText(content.text))
        }

        val request = SendMessageRequest.newBuilder()
            .setChatId(ChatId.newBuilder()
                .setValue(chatId.toByteArray().toByteString())
            )
            .addContent(contentProto)
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .apply { setSignature(sign(owner)) }
            .build()

        api.sendMessage(request, observer)
    }

    fun onStartedTyping(
        owner: KeyPair,
        chatId: ID,
        observer: StreamObserver<NotifyIsTypingResponse>
    ) {
        val request = NotifyIsTypingRequest.newBuilder()
            .setChatId(ChatId.newBuilder()
                .setValue(chatId.toByteArray().toByteString())
            ).setIsTyping(true)
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .apply { setSignature(sign(owner)) }
            .build()

        api.notifyIsTyping(request, observer)
    }

    fun onStoppedTyping(
        owner: KeyPair,
        chatId: ID,
        observer: StreamObserver<NotifyIsTypingResponse>
    ) {
        val request = NotifyIsTypingRequest.newBuilder()
            .setChatId(ChatId.newBuilder()
                .setValue(chatId.toByteArray().toByteString())
            ).setIsTyping(false)
            .setOwner(owner.publicKeyBytes.toSolanaAccount())
            .apply { setSignature(sign(owner)) }
            .build()

        api.notifyIsTyping(request, observer)
    }
}

private val SocialUser.chatMemberIdentity: MemberIdentity
    get() {
        val builder = MemberIdentity.newBuilder()
            .setUsername(username)
            .setPlatform(
                when (Platform.named(platform)) {
                    Platform.Unknown -> ChatService.Platform.UNKNOWN_PLATFORM
                    Platform.Twitter -> ChatService.Platform.TWITTER
                }
            )

        if (imageUrl != null) {
            builder.setProfilePicUrl(imageUrl)
        }

        return builder.build()
    }